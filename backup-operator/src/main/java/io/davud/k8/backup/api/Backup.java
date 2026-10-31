package io.davud.k8.backup.api;

import io.fabric8.crd.generator.annotation.AdditionalPrinterColumn;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

/**
 * One Backup per app. Each entry in spec.jobs becomes a CronJob that archives
 * to its own borg repo under {@code <repoPrefix>/<namespace>/<backup>/<job>}.
 */
@Group("backup.davud.io")
@Version("v1alpha1")
@ShortNames("bkup")
@AdditionalPrinterColumn(name = "Ready", type = AdditionalPrinterColumn.Type.STRING,
        jsonPath = ".status.conditions[?(@.type==\"Ready\")].status")
public class Backup extends CustomResource<BackupSpec, BackupStatus> implements Namespaced {
}
