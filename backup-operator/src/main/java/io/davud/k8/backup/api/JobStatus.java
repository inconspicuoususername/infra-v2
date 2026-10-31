package io.davud.k8.backup.api;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JobStatus {
    private String name;
    private String cronJob;
    /**
     * Relative to the repository's BORG_REPO_PREFIX.
     */
    private String repository;
    private String lastScheduleTime;
    private String lastSuccessfulTime;
}
