package io.davud.k8.backup.api;

import io.fabric8.crd.generator.annotation.PrinterColumn;
import io.fabric8.crd.generator.annotation.PrinterColumnFormat;
import io.fabric8.kubernetes.api.model.Condition;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class BackupStatus {
    private List<Condition> conditions = new ArrayList<>();

    private Long observedGeneration;

    /**
     * Most recent successful run across all jobs.
     */
    @PrinterColumn(name = "Last Backup", format = PrinterColumnFormat.DATE_TIME)
    private String lastBackupTime;

    private List<JobStatus> jobs = new ArrayList<>();
}
