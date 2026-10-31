package io.davud.k8.backup.reconciler;

import io.davud.k8.backup.api.Backup;
import io.davud.k8.backup.api.BackupJob;
import io.davud.k8.backup.api.BackupJobType;
import io.davud.k8.backup.api.BackupRepository;
import io.davud.k8.backup.api.BackupStatus;
import io.davud.k8.backup.api.JobStatus;
import io.davud.k8.backup.render.JobResources;
import io.davud.k8.backup.render.Names;
import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusUpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.MaxReconciliationInterval;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

// The periodic resync picks up rotated credentials: the source Secret isn't
// watched, to avoid caching every Secret in the cluster.
@ControllerConfiguration(
        fieldManager = BackupReconciler.FIELD_MANAGER,
        maxReconciliationInterval = @MaxReconciliationInterval(interval = 10, timeUnit = TimeUnit.MINUTES))
public class BackupReconciler implements Reconciler<Backup> {
    public static final String FIELD_MANAGER = "backup-operator";

    private static final Logger log = LoggerFactory.getLogger(BackupReconciler.class);

    @Override
    public UpdateControl<Backup> reconcile(Backup backup, Context<Backup> context) {
        var client = context.getClient();
        var ns = backup.getMetadata().getNamespace();
        var spec = backup.getSpec();

        var repo = client.resources(BackupRepository.class).withName(spec.getTarget()).get();
        if (repo == null) {
            return notReady(backup, "RepositoryNotFound", "BackupRepository " + spec.getTarget() + " does not exist");
        }

        var credsRef = repo.getSpec().getCredentialsSecret();
        var source = client.secrets().inNamespace(credsRef.getNamespace()).withName(credsRef.getName()).get();
        if (source == null || source.getData() == null
                || !JobResources.CREDENTIAL_KEYS.stream().allMatch(source.getData()::containsKey)) {
            return notReady(backup, "CredentialsNotFound", "Secret " + credsRef.getNamespace() + "/" + credsRef.getName()
                    + " must exist with keys " + String.join(", ", JobResources.CREDENTIAL_KEYS));
        }

        for (var job : spec.getJobs()) {
            if (Names.cronJob(backup, job).length() > Names.MAX_CRONJOB_NAME) {
                return notReady(backup, "NameTooLong", "CronJob name " + Names.cronJob(backup, job)
                        + " exceeds " + Names.MAX_CRONJOB_NAME + " characters; shorten the Backup or job name");
            }
        }

        apply(client, JobResources.credentials(backup, source));

        var pullRef = repo.getSpec().getImagePullSecret();
        if (pullRef != null) {
            var pull = client.secrets().inNamespace(pullRef.getNamespace()).withName(pullRef.getName()).get();
            if (pull == null) {
                return notReady(backup, "PullSecretNotFound", "Secret " + pullRef.getNamespace() + "/" + pullRef.getName() + " not found");
            }
            apply(client, JobResources.pullSecret(backup, pull));
        }

        var jobStatuses = new ArrayList<JobStatus>();
        var problems = new ArrayList<String>();
        for (var job : spec.getJobs()) {
            missingSource(client, ns, job).ifPresent(problems::add);
            apply(client, JobResources.config(repo, backup, job));
            var cronJob = apply(client, JobResources.cronJob(repo, backup, job));
            jobStatuses.add(jobStatus(backup, job, cronJob));
        }

        var wanted = spec.getJobs().stream().map(BackupJob::getName).collect(Collectors.toSet());
        deleteStale(client.batch().v1().cronjobs().inNamespace(ns).withLabel(Names.BACKUP_LABEL, backup.getMetadata().getName()).list().getItems(), wanted, client);
        deleteStale(client.configMaps().inNamespace(ns).withLabel(Names.BACKUP_LABEL, backup.getMetadata().getName()).list().getItems(), wanted, client);

        var status = status(backup);
        status.setJobs(jobStatuses);
        status.setLastBackupTime(jobStatuses.stream()
                .map(JobStatus::getLastSuccessfulTime)
                .filter(Objects::nonNull)
                .max(Comparator.comparing(Instant::parse))
                .orElse(null));
        if (problems.isEmpty()) {
            setReady(backup, "True", "Reconciled", spec.getJobs().size() + " job(s) scheduled");
        } else {
            // CronJobs are still applied; they'll start working once the source shows up.
            setReady(backup, "False", "SourceNotFound", String.join("; ", problems));
        }
        return UpdateControl.patchStatus(backup);
    }

    @Override
    public ErrorStatusUpdateControl<Backup> updateErrorStatus(Backup backup, Context<Backup> context, Exception e) {
        log.error("Reconciling {}/{} failed", backup.getMetadata().getNamespace(), backup.getMetadata().getName(), e);
        setReady(backup, "False", "ReconcileError", e.getMessage());
        return ErrorStatusUpdateControl.patchStatus(backup);
    }

    @Override
    public List<EventSource<?, Backup>> prepareEventSources(EventSourceContext<Backup> context) {
        // CronJob status changes (lastSuccessfulTime) feed .status; edits/deletes get reverted.
        var cronJobs = new InformerEventSource<>(
                InformerEventSourceConfiguration.from(CronJob.class, Backup.class)
                        .withLabelSelector(Names.MANAGED_BY_SELECTOR)
                        .withSecondaryToPrimaryMapper(Mappers.fromOwnerReferences(Backup.class))
                        .build(),
                context);
        // A repository change re-renders every Backup that targets it.
        var repositories = new InformerEventSource<>(
                InformerEventSourceConfiguration.from(BackupRepository.class, Backup.class)
                        .withSecondaryToPrimaryMapper(repo -> context.getPrimaryCache()
                                .list(b -> b.getSpec() != null && repo.getMetadata().getName().equals(b.getSpec().getTarget()))
                                .map(ResourceID::fromResource)
                                .collect(Collectors.toSet()))
                        .build(),
                context);
        return List.of(cronJobs, repositories);
    }

    private static <T extends HasMetadata> T apply(KubernetesClient client, T resource) {
        return client.resource(resource).fieldManager(FIELD_MANAGER).forceConflicts().serverSideApply();
    }

    private static void deleteStale(List<? extends HasMetadata> existing, Set<String> wanted, KubernetesClient client) {
        for (var resource : existing) {
            var job = resource.getMetadata().getLabels().get(Names.JOB_LABEL);
            if (job != null && !wanted.contains(job)) {
                log.info("Deleting {} {}/{} for removed job {}", resource.getKind(),
                        resource.getMetadata().getNamespace(), resource.getMetadata().getName(), job);
                client.resource(resource).delete();
            }
        }
    }

    private static Optional<String> missingSource(KubernetesClient client, String ns, BackupJob job) {
        if (job.getType() == BackupJobType.POSTGRES_DUMP) {
            var secret = job.getPostgres().getCnpgCluster() + "-app";
            if (client.resources(Secret.class).inNamespace(ns).withName(secret).get() == null) {
                return Optional.of("job " + job.getName() + ": Secret " + secret + " not found (is the CNPG cluster name right?)");
            }
        } else if (job.getType() == BackupJobType.FILESYSTEM) {
            var pvc = job.getFilesystem().getPvc();
            if (client.resources(PersistentVolumeClaim.class).inNamespace(ns).withName(pvc).get() == null) {
                return Optional.of("job " + job.getName() + ": PVC " + pvc + " not found");
            }
        }
        return Optional.empty();
    }

    private static JobStatus jobStatus(Backup backup, BackupJob job, CronJob cronJob) {
        var s = cronJob.getStatus();
        return new JobStatus(
                job.getName(),
                cronJob.getMetadata().getName(),
                Names.repositorySubPath(backup, job),
                s == null ? null : s.getLastScheduleTime(),
                s == null ? null : s.getLastSuccessfulTime());
    }

    private static UpdateControl<Backup> notReady(Backup backup, String reason, String message) {
        log.warn("{}/{}: {}", backup.getMetadata().getNamespace(), backup.getMetadata().getName(), message);
        setReady(backup, "False", reason, message);
        return UpdateControl.patchStatus(backup);
    }

    private static BackupStatus status(Backup backup) {
        if (backup.getStatus() == null) {
            backup.setStatus(new BackupStatus());
        }
        return backup.getStatus();
    }

    private static void setReady(Backup backup, String value, String reason, String message) {
        var status = status(backup);
        var generation = backup.getMetadata().getGeneration();
        status.setObservedGeneration(generation);
        var previous = status.getConditions().stream().filter(c -> "Ready".equals(c.getType())).findFirst();
        var transition = previous.filter(c -> value.equals(c.getStatus()))
                .map(Condition::getLastTransitionTime)
                .orElse(Instant.now().toString());
        var conditions = new ArrayList<>(status.getConditions().stream().filter(c -> !"Ready".equals(c.getType())).toList());
        conditions.add(new ConditionBuilder()
                .withType("Ready")
                .withStatus(value)
                .withReason(reason)
                .withMessage(message)
                .withObservedGeneration(generation)
                .withLastTransitionTime(transition)
                .build());
        status.setConditions(conditions);
    }
}
