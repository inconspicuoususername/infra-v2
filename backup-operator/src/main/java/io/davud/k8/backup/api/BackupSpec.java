package io.davud.k8.backup.api;

import io.fabric8.crd.generator.annotation.PrinterColumn;
import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.Size;
import io.fabric8.generator.annotation.ValidationRule;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BackupSpec {
    /**
     * Name of the cluster-scoped BackupRepository to archive to. Its borg
     * credentials are copied into this namespace by the operator.
     */
    @Required
    @Size(min = 1)
    @PrinterColumn(name = "Repository")
    private String target;

    @Required
    @Size(min = 1, max = 20)
    @ValidationRule(value = "self.all(j, self.exists_one(k, k.name == j.name))", message = "job names must be unique")
    private List<BackupJob> jobs;

    /**
     * Suspends the generated CronJobs without deleting anything.
     */
    private boolean suspend;
}
