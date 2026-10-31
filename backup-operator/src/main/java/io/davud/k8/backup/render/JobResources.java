package io.davud.k8.backup.render;

import io.davud.k8.backup.api.Backup;
import io.davud.k8.backup.api.BackupJob;
import io.davud.k8.backup.api.BackupJobType;
import io.davud.k8.backup.api.BackupRepository;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.api.model.batch.v1.CronJobBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JobResources {
    public static final String DEFAULT_IMAGE = "registry.aquilla.dev/davud/borgmatic:2.0.13";
    public static final long DEFAULT_UID = 10001L;
    public static final String CONFIG_DIR = "/etc/borgmatic.d";
    public static final String CONFIG_KEY = "config.yaml";
    public static final String KNOWN_HOSTS_KEY = "known_hosts";
    public static final String PASSPHRASE_KEY = "BORG_PASSPHRASE";
    public static final String SSH_KEY_KEY = "BORG_SSH_KEY";
    public static final String REPO_PREFIX_KEY = "BORG_REPO_PREFIX";
    public static final List<String> CREDENTIAL_KEYS = List.of(PASSPHRASE_KEY, SSH_KEY_KEY, REPO_PREFIX_KEY);

    private JobResources() {
    }

    /**
     * The repository's borg credentials, copied into the Backup's namespace.
     */
    public static Secret credentials(Backup backup, Secret source) {
        var data = new HashMap<String, String>();
        CREDENTIAL_KEYS.forEach(key -> data.put(key, source.getData().get(key)));
        return new SecretBuilder()
                .withMetadata(meta(backup, Names.credentialsSecret(backup), Names.labels(backup)))
                .withType("Opaque")
                .withData(data)
                .build();
    }

    public static Secret pullSecret(Backup backup, Secret source) {
        return new SecretBuilder()
                .withMetadata(meta(backup, Names.pullSecret(backup), Names.labels(backup)))
                .withType(source.getType())
                .withData(source.getData())
                .build();
    }

    public static ConfigMap config(BackupRepository repo, Backup backup, BackupJob job) {
        var data = new HashMap<String, String>();
        data.put(CONFIG_KEY, BorgmaticConfig.render(repo, backup, job));
        if (repo.getSpec().getKnownHosts() != null) {
            data.put(KNOWN_HOSTS_KEY, repo.getSpec().getKnownHosts());
        }
        return new ConfigMapBuilder()
                .withMetadata(meta(backup, Names.configMap(backup, job), Names.labels(backup, job)))
                .withData(data)
                .build();
    }

    public static CronJob cronJob(BackupRepository repo, Backup backup, BackupJob job) {
        long uid = job.getRunAsUser() != null ? job.getRunAsUser() : DEFAULT_UID;
        long gid = job.getRunAsGroup() != null ? job.getRunAsGroup() : uid;
        var image = repo.getSpec().getImage() != null ? repo.getSpec().getImage() : DEFAULT_IMAGE;
        // repo-create is a no-op when the repo exists, so it runs every time
        // instead of needing a one-off init step per app.
        var borgmatic = "borgmatic --config " + CONFIG_DIR + "/" + CONFIG_KEY;
        var script = borgmatic + " repo-create --encryption " + repo.getSpec().getEncryption() + " --make-parent-dirs"
                + " && " + borgmatic + " create prune compact --stats --verbosity 1";

        return new CronJobBuilder()
                .withMetadata(meta(backup, Names.cronJob(backup, job), Names.labels(backup, job)))
                .withNewSpec()
                    .withSchedule(job.getSchedule())
                    .withSuspend(backup.getSpec().isSuspend())
                    .withConcurrencyPolicy("Forbid")
                    .withStartingDeadlineSeconds(3600L)
                    .withSuccessfulJobsHistoryLimit(3)
                    .withFailedJobsHistoryLimit(3)
                    .withNewJobTemplate()
                        .withNewMetadata().withLabels(Names.labels(backup, job)).endMetadata()
                        .withNewSpec()
                            .withBackoffLimit(2)
                            .withNewTemplate()
                                .withNewMetadata().withLabels(Names.labels(backup, job)).endMetadata()
                                .withNewSpec()
                                    .withRestartPolicy("Never")
                                    .withImagePullSecrets(repo.getSpec().getImagePullSecret() == null
                                            ? List.of()
                                            : List.of(new LocalObjectReference(Names.pullSecret(backup))))
                                    .withNewSecurityContext()
                                        .withRunAsNonRoot(true)
                                        .withRunAsUser(uid)
                                        .withRunAsGroup(gid)
                                        .withNewSeccompProfile().withType("RuntimeDefault").endSeccompProfile()
                                    .endSecurityContext()
                                    .addNewContainer()
                                        .withName("borgmatic")
                                        .withImage(image)
                                        // args, not command: the image entrypoint sets up ssh and the passwd entry
                                        .withArgs("sh", "-c", script)
                                        .withEnv(env(repo, backup, job))
                                        .withVolumeMounts(volumeMounts(job))
                                        .withNewSecurityContext()
                                            .withAllowPrivilegeEscalation(false)
                                            .withReadOnlyRootFilesystem(true)
                                            .withNewCapabilities().withDrop("ALL").endCapabilities()
                                        .endSecurityContext()
                                        .withNewResources()
                                            .withRequests(Map.of("cpu", new Quantity("50m"), "memory", new Quantity("128Mi")))
                                            .withLimits(Map.of("memory", new Quantity("1Gi")))
                                        .endResources()
                                    .endContainer()
                                    .withVolumes(volumes(backup, job))
                                .endSpec()
                            .endTemplate()
                        .endSpec()
                    .endJobTemplate()
                .endSpec()
                .build();
    }

    private static List<EnvVar> env(BackupRepository repo, Backup backup, BackupJob job) {
        var creds = Names.credentialsSecret(backup);
        var env = new ArrayList<EnvVar>();
        env.add(new EnvVar("HOME", "/tmp/home", null));
        env.add(secretEnv(PASSPHRASE_KEY, creds, PASSPHRASE_KEY));
        env.add(secretEnv(SSH_KEY_KEY, creds, SSH_KEY_KEY));
        env.add(secretEnv(REPO_PREFIX_KEY, creds, REPO_PREFIX_KEY));
        if (repo.getSpec().getKnownHosts() != null) {
            env.add(new EnvVar("BORG_KNOWN_HOSTS_FILE", CONFIG_DIR + "/" + KNOWN_HOSTS_KEY, null));
        }
        if (job.getType() == BackupJobType.POSTGRES_DUMP) {
            var app = job.getPostgres().getCnpgCluster() + "-app";
            env.add(secretEnv("PGUSER", app, "username"));
            env.add(secretEnv("PGPASSWORD", app, "password"));
            env.add(secretEnv("PGDATABASE", app, "dbname"));
        }
        return env;
    }

    private static EnvVar secretEnv(String name, String secret, String key) {
        return new EnvVarBuilder()
                .withName(name)
                .withNewValueFrom().withNewSecretKeyRef().withName(secret).withKey(key).endSecretKeyRef().endValueFrom()
                .build();
    }

    private static List<VolumeMount> volumeMounts(BackupJob job) {
        var mounts = new ArrayList<VolumeMount>();
        mounts.add(new VolumeMountBuilder().withName("config").withMountPath(CONFIG_DIR).withReadOnly(true).build());
        mounts.add(new VolumeMountBuilder().withName("tmp").withMountPath("/tmp").build());
        if (job.getType() == BackupJobType.FILESYSTEM) {
            mounts.add(new VolumeMountBuilder().withName("data").withMountPath(BorgmaticConfig.DATA_MOUNT).withReadOnly(true).build());
        }
        return mounts;
    }

    private static List<Volume> volumes(Backup backup, BackupJob job) {
        var volumes = new ArrayList<Volume>();
        volumes.add(new VolumeBuilder().withName("config")
                .withNewConfigMap().withName(Names.configMap(backup, job)).endConfigMap().build());
        volumes.add(new VolumeBuilder().withName("tmp").withNewEmptyDir().endEmptyDir().build());
        if (job.getType() == BackupJobType.FILESYSTEM) {
            volumes.add(new VolumeBuilder().withName("data")
                    .withNewPersistentVolumeClaim().withClaimName(job.getFilesystem().getPvc()).withReadOnly(true).endPersistentVolumeClaim()
                    .build());
        }
        return volumes;
    }

    private static ObjectMeta meta(Backup backup, String name, Map<String, String> labels) {
        return new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(backup.getMetadata().getNamespace())
                .withLabels(labels)
                .withOwnerReferences(owner(backup))
                .build();
    }

    private static OwnerReference owner(Backup backup) {
        return new OwnerReferenceBuilder()
                .withApiVersion(backup.getApiVersion())
                .withKind(backup.getKind())
                .withName(backup.getMetadata().getName())
                .withUid(backup.getMetadata().getUid())
                .withController(true)
                .withBlockOwnerDeletion(true)
                .build();
    }
}
