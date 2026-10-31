package io.davud.k8.backup.api;

import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * A CloudNativePG Cluster in the Backup's namespace. Connection details come
 * from the {@code <cluster>-rw} Service and the {@code <cluster>-app} Secret.
 */
@Getter
@Setter
public class PostgresSource {
    @Required
    @Size(min = 1)
    private String cnpgCluster;

    /**
     * Database to dump. Defaults to the cluster's app database.
     */
    private String database;
}
