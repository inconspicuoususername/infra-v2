package io.davud.k8.backup.api;

import io.fabric8.generator.annotation.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps 1:1 to borgmatic's keep_* options.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RetentionPolicy {
    @Min(0)
    private Integer keepDaily;

    @Min(0)
    private Integer keepWeekly;

    @Min(0)
    private Integer keepMonthly;
}
