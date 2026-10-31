package io.davud.k8.backup.api;

import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SecretReference {
    @Required
    @Size(min = 1)
    private String name;

    @Required
    @Size(min = 1)
    private String namespace;
}
