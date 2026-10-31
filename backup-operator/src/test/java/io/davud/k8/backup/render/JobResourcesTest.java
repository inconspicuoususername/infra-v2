package io.davud.k8.backup.render;

import io.davud.k8.backup.api.Backup;
import io.davud.k8.backup.api.BackupJob;
import io.davud.k8.backup.api.BackupJobType;
import io.davud.k8.backup.api.BackupRepository;
import io.davud.k8.backup.api.BackupRepositorySpec;
import io.davud.k8.backup.api.BackupSpec;
import io.davud.k8.backup.api.FilesystemSource;
import io.davud.k8.backup.api.PostgresSource;
import io.davud.k8.backup.api.RetentionPolicy;
import io.davud.k8.backup.api.SecretReference;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JobResourcesTest {

    @Test
    void postgresConfigReferencesEnvInsteadOfSecrets() {
        var config = BorgmaticConfig.render(repo(), backup(postgresJob()), postgresJob());

        assertThat(config).contains("path: \"${BORG_REPO_PREFIX}/plane/plane/db\"");
        assertThat(config).contains("hostname: plane-db-rw");
        assertThat(config).contains("password: \"${PGPASSWORD}\"");
        assertThat(config).contains("name: \"${PGDATABASE}\"");
        assertThat(config).contains("archive_name_format: \"db-{now:%Y-%m-%dT%H:%M:%S}\"");
        assertThat(config).doesNotContain("source_directories");
    }

    @Test
    void unreadableFilesFailTheJob() {
        var config = BorgmaticConfig.render(repo(), backup(filesystemJob()), filesystemJob());

        for (var code : BorgmaticConfig.BORG_ERRORS) {
            assertThat(config).contains("- code: " + code + "\n  treat_as: error");
        }
    }

    @Test
    void explicitDatabaseOverridesAppDatabase() {
        var job = postgresJob();
        job.getPostgres().setDatabase("other");

        assertThat(BorgmaticConfig.render(repo(), backup(job), job)).contains("name: other");
    }

    @Test
    void filesystemSubPathIsNormalised() {
        var job = filesystemJob();
        job.getFilesystem().setSubPath("uploads/");

        var config = BorgmaticConfig.render(repo(), backup(job), job);

        assertThat(config).contains("- /data/uploads");
        assertThat(config).doesNotContain("postgresql_databases");
    }

    @Test
    void retentionPrefersJobThenRepositoryThenDefault() {
        var repo = repo();
        var job = postgresJob();
        assertThat(BorgmaticConfig.retention(repo, job)).isSameAs(BorgmaticConfig.DEFAULT_RETENTION);

        repo.getSpec().setRetention(new RetentionPolicy(1, 2, 3));
        assertThat(BorgmaticConfig.retention(repo, job).getKeepDaily()).isEqualTo(1);

        job.setRetention(new RetentionPolicy(9, null, null));
        var config = BorgmaticConfig.render(repo, backup(job), job);
        assertThat(config).contains("keep_daily: 9").doesNotContain("keep_weekly");
    }

    @Test
    void cronJobPodIsRestrictedCompliant() {
        var job = filesystemJob();
        var cronJob = JobResources.cronJob(repo(), backup(job), job);
        var pod = cronJob.getSpec().getJobTemplate().getSpec().getTemplate().getSpec();
        var container = pod.getContainers().get(0);

        assertThat(pod.getSecurityContext().getRunAsNonRoot()).isTrue();
        assertThat(pod.getSecurityContext().getRunAsUser()).isEqualTo(JobResources.DEFAULT_UID);
        assertThat(pod.getSecurityContext().getSeccompProfile().getType()).isEqualTo("RuntimeDefault");
        assertThat(container.getSecurityContext().getAllowPrivilegeEscalation()).isFalse();
        assertThat(container.getSecurityContext().getCapabilities().getDrop()).containsExactly("ALL");
        assertThat(container.getSecurityContext().getReadOnlyRootFilesystem()).isTrue();
        // command would bypass the image entrypoint that writes the ssh key and passwd entry
        assertThat(container.getCommand()).isEmpty();
        assertThat(container.getArgs()).hasSize(3);
        assertThat(pod.getVolumes()).filteredOn(v -> v.getPersistentVolumeClaim() != null).singleElement().satisfies(v -> {
            assertThat(v.getPersistentVolumeClaim().getClaimName()).isEqualTo("plane-minio");
            assertThat(v.getPersistentVolumeClaim().getReadOnly()).isTrue();
        });
    }

    @Test
    void runAsUserIsConfigurable() {
        var job = filesystemJob();
        job.setRunAsUser(1000L);

        var pod = JobResources.cronJob(repo(), backup(job), job).getSpec().getJobTemplate().getSpec().getTemplate().getSpec();

        assertThat(pod.getSecurityContext().getRunAsUser()).isEqualTo(1000L);
        assertThat(pod.getSecurityContext().getRunAsGroup()).isEqualTo(1000L);
    }

    @Test
    void secretsOnlyReachThePodThroughSecretKeyRefs() {
        var job = postgresJob();
        var env = JobResources.cronJob(repo(), backup(job), job).getSpec().getJobTemplate().getSpec().getTemplate().getSpec()
                .getContainers().get(0).getEnv();

        assertThat(env).filteredOn(e -> e.getName().startsWith("PG") || e.getName().startsWith("BORG_"))
                .allSatisfy(e -> assertThat(e.getValueFrom().getSecretKeyRef()).isNotNull());
        assertThat(env).extracting(EnvVar::getName)
                .contains("BORG_PASSPHRASE", "BORG_SSH_KEY", "BORG_REPO_PREFIX", "PGUSER", "PGPASSWORD", "PGDATABASE")
                .doesNotContain("BORG_KNOWN_HOSTS_FILE");
    }

    @Test
    void knownHostsEnablesStrictHostChecking() {
        var repo = repo();
        repo.getSpec().setKnownHosts("[box]:23 ssh-ed25519 AAAA");
        var job = postgresJob();

        assertThat(JobResources.config(repo, backup(job), job).getData()).containsKey(JobResources.KNOWN_HOSTS_KEY);
        assertThat(JobResources.cronJob(repo, backup(job), job).getSpec().getJobTemplate().getSpec().getTemplate().getSpec()
                .getContainers().get(0).getEnv()).extracting(EnvVar::getName).contains("BORG_KNOWN_HOSTS_FILE");
    }

    @Test
    void everythingIsOwnedByTheBackup() {
        var job = postgresJob();
        var backup = backup(job);
        var source = new SecretBuilder().withData(Map.of(JobResources.PASSPHRASE_KEY, "cA==", JobResources.SSH_KEY_KEY, "aw==", JobResources.REPO_PREFIX_KEY, "cw==", "other", "eA==")).build();

        var secret = JobResources.credentials(backup, source);

        assertThat(secret.getData()).containsOnlyKeys(JobResources.CREDENTIAL_KEYS.toArray(String[]::new));
        for (var resource : List.of(secret, JobResources.config(repo(), backup, job), JobResources.cronJob(repo(), backup, job))) {
            assertThat(resource.getMetadata().getNamespace()).isEqualTo("plane");
            assertThat(resource.getMetadata().getOwnerReferences()).singleElement()
                    .satisfies(o -> assertThat(o.getUid()).isEqualTo("uid-1"));
        }
    }

    @Test
    void pullSecretIsReferencedOnlyWhenConfigured() {
        var job = postgresJob();
        var repo = repo();
        assertThat(JobResources.cronJob(repo, backup(job), job).getSpec().getJobTemplate().getSpec().getTemplate().getSpec()
                .getImagePullSecrets()).isEmpty();

        repo.getSpec().setImagePullSecret(new SecretReference("registry-pull", "backup-system"));
        assertThat(JobResources.cronJob(repo, backup(job), job).getSpec().getJobTemplate().getSpec().getTemplate().getSpec()
                .getImagePullSecrets()).singleElement().satisfies(r -> assertThat(r.getName()).isEqualTo("plane-borg-pull"));
    }

    @Test
    void suspendPropagatesToCronJobs() {
        var job = postgresJob();
        var backup = backup(job);
        backup.getSpec().setSuspend(true);

        assertThat(JobResources.cronJob(repo(), backup, job).getSpec().getSuspend()).isTrue();
    }

    // Not an assertion: dumps rendered objects so they can be checked against a
    // real API server, e.g. `kubectl apply --dry-run=server -f target/rendered/`.
    @Test
    void writeRenderedExamples() throws IOException {
        var dir = Path.of("target", "rendered");
        Files.createDirectories(dir);
        for (var job : List.of(postgresJob(), filesystemJob())) {
            var backup = backup(job);
            var cronJob = JobResources.cronJob(repo(), backup, job);
            var pod = new PodBuilder()
                    .withMetadata(new ObjectMetaBuilder().withName("render-" + job.getName()).withNamespace("plane").build())
                    .withSpec(cronJob.getSpec().getJobTemplate().getSpec().getTemplate().getSpec())
                    .build();
            Files.writeString(dir.resolve(job.getName() + "-cronjob.yaml"), Serialization.asYaml(cronJob));
            Files.writeString(dir.resolve(job.getName() + "-pod.yaml"), Serialization.asYaml(pod));
            Files.writeString(dir.resolve(job.getName() + "-borgmatic.yaml"), BorgmaticConfig.render(repo(), backup, job));
        }
    }

    private static BackupRepository repo() {
        var spec = new BackupRepositorySpec();
        spec.setCredentialsSecret(new SecretReference("borg-credentials", "backup-system"));
        var repo = new BackupRepository();
        repo.setMetadata(new ObjectMetaBuilder().withName("storagebox").build());
        repo.setSpec(spec);
        return repo;
    }

    private static Backup backup(BackupJob job) {
        var spec = new BackupSpec();
        spec.setTarget("storagebox");
        spec.setJobs(List.of(job));
        var backup = new Backup();
        backup.setMetadata(new ObjectMetaBuilder().withName("plane").withNamespace("plane").withUid("uid-1").withGeneration(1L).build());
        backup.setSpec(spec);
        return backup;
    }

    private static BackupJob postgresJob() {
        var pg = new PostgresSource();
        pg.setCnpgCluster("plane-db");
        var job = new BackupJob();
        job.setName("db");
        job.setType(BackupJobType.POSTGRES_DUMP);
        job.setSchedule("0 3 * * *");
        job.setPostgres(pg);
        return job;
    }

    private static BackupJob filesystemJob() {
        var fs = new FilesystemSource();
        fs.setPvc("plane-minio");
        var job = new BackupJob();
        job.setName("uploads");
        job.setType(BackupJobType.FILESYSTEM);
        job.setSchedule("30 3 * * *");
        job.setFilesystem(fs);
        return job;
    }
}
