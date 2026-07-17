package io.davud.k8.backup.crd;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("io.davud")
@Version("v1")
public class BackupBackendCRD extends CustomResource<BackupSpec, BackupStatus> implements Namespaced {
}
