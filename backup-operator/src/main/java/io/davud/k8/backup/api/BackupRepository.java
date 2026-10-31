package io.davud.k8.backup.api;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

/**
 * Cluster-scoped borg backend (the old ansible {@code backup_backends}).
 */
@Group("backup.davud.io")
@Version("v1alpha1")
@ShortNames("bkrepo")
public class BackupRepository extends CustomResource<BackupRepositorySpec, Void> {
}
