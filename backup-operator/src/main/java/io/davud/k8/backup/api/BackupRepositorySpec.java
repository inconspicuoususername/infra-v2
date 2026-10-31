package io.davud.k8.backup.api;

import io.fabric8.generator.annotation.Default;
import io.fabric8.generator.annotation.Required;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BackupRepositorySpec {
    /**
     * Secret holding BORG_PASSPHRASE, BORG_SSH_KEY and BORG_REPO_PREFIX (e.g.
     * {@code ssh://user@host:23/./backups/v2}, no trailing slash). The prefix
     * lives in the Secret so the backup host stays out of git.
     */
    @Required
    private SecretReference credentialsSecret;

    /**
     * known_hosts lines for the borg server. When unset, the host key is
     * accepted on first use and not remembered.
     */
    private String knownHosts;

    /**
     * Passed to {@code borgmatic repo-create} when a job's repo doesn't exist yet.
     */
    @Default("repokey-blake2")
    private String encryption = "repokey-blake2";

    /**
     * Default retention for jobs that don't set their own.
     */
    private RetentionPolicy retention;

    /**
     * dockerconfigjson Secret for pulling the runner image; copied into each
     * Backup's namespace alongside the borg credentials.
     */
    private SecretReference imagePullSecret;

    /**
     * Runner image override (borgmatic + borg + ssh + pg_dump, non-root capable).
     */
    private String image;
}
