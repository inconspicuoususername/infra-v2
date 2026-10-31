package io.davud.k8.backup.api;

import io.fabric8.generator.annotation.Min;
import io.fabric8.generator.annotation.Pattern;
import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.Size;
import io.fabric8.generator.annotation.ValidationRule;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ValidationRule(value = "self.type != 'postgresDump' || has(self.postgres)", message = "postgres must be set when type is postgresDump")
@ValidationRule(value = "self.type != 'filesystem' || has(self.filesystem)", message = "filesystem must be set when type is filesystem")
public class BackupJob {
    /**
     * Unique within the Backup. Becomes the borg repo leaf and the CronJob name suffix.
     */
    @Required
    @Size(min = 1, max = 30)
    @Pattern("^[a-z0-9]([-a-z0-9]*[a-z0-9])?$")
    private String name;

    @Required
    private BackupJobType type;

    /**
     * Cron expression for the generated CronJob.
     */
    @Required
    @Size(min = 1)
    private String schedule;

    /**
     * Overrides the repository's default retention.
     */
    private RetentionPolicy retention;

    private PostgresSource postgres;

    private FilesystemSource filesystem;

    /**
     * UID/GID the backup pod runs as. Filesystem jobs need one that can read
     * the PVC's files (usually the app's own UID). Defaults to 10001.
     */
    @Min(1)
    private Long runAsUser;

    @Min(0)
    private Long runAsGroup;
}
