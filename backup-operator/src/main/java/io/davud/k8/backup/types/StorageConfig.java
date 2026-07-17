package io.davud.k8.backup.types;


import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",            // Maps to the 'type' field in YAML
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PostgresStorageConfig.class, name = "POSTGRES_DUMP"),
        @JsonSubTypes.Type(value = FilesystemStorageConfig.class, name = "FILESYSTEM")
})
public interface StorageConfig {
    BackupJobType getType();
}
