package io.davud.k8.backup.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum BackupJobType {
    /**
     * pg_dump of a CloudNativePG cluster, streamed into borg by borgmatic.
     */
    @JsonProperty("postgresDump")
    POSTGRES_DUMP,

    /**
     * The contents of a PVC, mounted read-only.
     */
    @JsonProperty("filesystem")
    FILESYSTEM
}
