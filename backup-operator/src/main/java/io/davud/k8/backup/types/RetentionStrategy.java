package io.davud.k8.backup.types;

import lombok.Getter;
import lombok.Setter;

import java.util.Optional;

@Getter
@Setter
public class RetentionStrategy {
    Integer keepDaily;
    Integer keepWeekly;
    Integer keepMonthly;
}
