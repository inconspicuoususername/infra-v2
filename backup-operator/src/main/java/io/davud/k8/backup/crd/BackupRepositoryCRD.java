package io.davud.k8.backup.crd;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;

public class BackupRepositoryCRD extends CustomResource<BackupSpec, BackupStatus> implements Namespaced {
}
