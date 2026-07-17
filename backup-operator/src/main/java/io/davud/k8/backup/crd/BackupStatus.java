/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.davud.k8.backup.crd;

import io.fabric8.kubernetes.api.model.Condition;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class BackupStatus {

    /**
     * Conditions represent the current state of the Backup resource
     * (e.g. "Ready", "Progressing", "Degraded").
     */
    private List<Condition> conditions = new ArrayList<>();

    /**
     * ObservedGeneration is the .metadata.generation the controller last acted
     * on -- compare against .metadata.generation to know if a change is pending.
     */
    private Long observedGeneration;

    /**
     * LastBackupTime is the most recent successful run across all jobs.
     */
    private String lastBackupTime;

    /**
     * LastArchive is the borg archive name of the most recent successful run.
     */
    private String lastArchive;

    // getters and setters for all four
}
