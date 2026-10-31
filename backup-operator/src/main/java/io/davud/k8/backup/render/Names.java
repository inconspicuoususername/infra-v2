package io.davud.k8.backup.render;

import io.davud.k8.backup.api.Backup;
import io.davud.k8.backup.api.BackupJob;

import java.util.Map;

public final class Names {
    public static final String MANAGED_BY_SELECTOR = "app.kubernetes.io/managed-by=backup-operator";
    public static final String BACKUP_LABEL = "backup.davud.io/backup";
    public static final String JOB_LABEL = "backup.davud.io/job";

    // CronJob names are capped at 52 chars: the controller appends an 11-char suffix to Job names.
    public static final int MAX_CRONJOB_NAME = 52;

    private Names() {
    }

    public static String cronJob(Backup backup, BackupJob job) {
        return backup.getMetadata().getName() + "-" + job.getName();
    }

    public static String configMap(Backup backup, BackupJob job) {
        return cronJob(backup, job) + "-borgmatic";
    }

    public static String credentialsSecret(Backup backup) {
        return backup.getMetadata().getName() + "-borg";
    }

    public static String pullSecret(Backup backup) {
        return backup.getMetadata().getName() + "-borg-pull";
    }

    /**
     * Repo location relative to the repository's BORG_REPO_PREFIX.
     */
    public static String repositorySubPath(Backup backup, BackupJob job) {
        return backup.getMetadata().getNamespace() + "/" + backup.getMetadata().getName() + "/" + job.getName();
    }

    /**
     * Full repo path as borgmatic sees it; the prefix is filled in from the pod's env.
     */
    public static String repositoryPath(Backup backup, BackupJob job) {
        return "${BORG_REPO_PREFIX}/" + repositorySubPath(backup, job);
    }

    public static Map<String, String> labels(Backup backup) {
        return Map.of(
                "app.kubernetes.io/managed-by", "backup-operator",
                BACKUP_LABEL, backup.getMetadata().getName());
    }

    public static Map<String, String> labels(Backup backup, BackupJob job) {
        return Map.of(
                "app.kubernetes.io/managed-by", "backup-operator",
                BACKUP_LABEL, backup.getMetadata().getName(),
                JOB_LABEL, job.getName());
    }
}
