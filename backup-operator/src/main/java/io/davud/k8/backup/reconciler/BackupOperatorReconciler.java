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
package io.davud.k8.backup.reconciler;

import io.davud.k8.backup.ConfigMapDependentResource;
import io.davud.k8.backup.crd.BackupCRD;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

import java.util.List;

@Workflow(dependents = {@Dependent(type = ConfigMapDependentResource.class)})
public class BackupOperatorReconciler implements Reconciler<BackupCRD> {

    public UpdateControl<BackupCRD> reconcile(BackupCRD primary,
                                              Context<BackupCRD> context) {

        var namespace = primary.getMetadata().getNamespace();
        var spec = primary.getSpec();

        return UpdateControl.noUpdate();
    }


    @Override
    public List<EventSource<?, BackupCRD>> prepareEventSources(EventSourceContext<BackupCRD> context) {
        var cmEventSource = new InformerEventSource<>(
                InformerEventSourceConfiguration
                        .from(Secret.class, BackupCRD.class)
                        // map a ConfigMap event -> which Machine(s) to reconcile,
                        // default: follow the owner reference
                        .build(),
                context);
        return List.of(cmEventSource);
    }

    private Secret getBackupBackendSecret
}
