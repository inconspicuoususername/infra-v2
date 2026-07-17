package io.davud.k8.backup.crd;


import io.davud.k8.backup.types.BackupJob;
import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BackupSpec {
    /**
     * Target is the name of a (cluster-scoped) BackupRepository -- the borg
     * backend. The controller holds the borg credential once, in the repo's
     * namespace, and projects it into this namespace; you never copy the SSH key per app
     */
    @Required
    @Size(min = 1)
    String target;


    @Required
    @Size(min = 1)
    List<BackupJob> jobs;
}
