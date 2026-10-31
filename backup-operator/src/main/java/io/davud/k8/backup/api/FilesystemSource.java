package io.davud.k8.backup.api;

import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.Size;
import io.fabric8.generator.annotation.ValidationRule;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FilesystemSource {
    /**
     * PersistentVolumeClaim in the Backup's namespace, mounted read-only.
     */
    @Required
    @Size(min = 1)
    private String pvc;

    /**
     * Only archive this directory within the volume.
     */
    @ValidationRule(value = "!self.startsWith('/') && !self.contains('..')", message = "subPath must be relative and must not contain '..'")
    private String subPath;
}
