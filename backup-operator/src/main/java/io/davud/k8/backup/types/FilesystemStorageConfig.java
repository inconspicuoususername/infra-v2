package io.davud.k8.backup.types;

import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FilesystemStorageConfig implements StorageConfig {
    private final BackupJobType type = BackupJobType.FILESYSTEM;

    @Required
    @Size(min = 1)
    @Setter
    private String pvc;

    @Setter
    private String subpath;
}
