/**
 * Copyright (C) 2006-2017 Talend Inc. - www.talend.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.talend.sdk.component.design.extension;

import static java.util.Optional.of;
import static java.util.Optional.ofNullable;

import java.lang.reflect.AnnotatedElement;
import java.util.stream.Stream;

import org.talend.sdk.component.api.component.Icon;
import org.talend.sdk.component.container.Container;
import org.talend.sdk.component.design.extension.flows.FlowsFactory;
import org.talend.sdk.component.runtime.manager.ContainerComponentRegistry;
import org.talend.sdk.component.runtime.manager.spi.ContainerListenerExtension;

/**
 * Service provider for {@link ContainerListenerExtension} service
 */
public class DesignContainerListener implements ContainerListenerExtension {

    /**
     * Enriches {@link Container} with {@link DesignModelRegistry}
     * It depends on Updater listener which adds {@link ContainerComponentRegistry} class to {@link Container}
     */
    @Override
    public void onCreate(Container container) {
        ContainerComponentRegistry componentRegistry = container.get(ContainerComponentRegistry.class);
        if (componentRegistry == null) {
            throw new IllegalArgumentException("container doesn't contain ContainerComponentRegistry");
        }

        componentRegistry.getComponents().values().stream() //
                .peek(family -> family.set(DesignFamilyModel.class, new DesignFamilyModel(findIcon(family.getFamilyPackage()))))
                .flatMap(family -> Stream.concat( //
                        family.getPartitionMappers().values().stream(), //
                        family.getProcessors().values().stream())) //
                .forEach(meta -> {
                    FlowsFactory factory = FlowsFactory.get(meta);
                    meta.set(DesignModel.class,
                            new DesignModel( //
                                    meta.getId(), //
                                    findIcon(meta.getType()), //
                                    factory.getInputFlows(), //
                                    factory.getOutputFlows())); //
                });

    }

    /**
     * It relies on Updater listener onClose() method which removes all ComponentFamilyMeta from Container
     * Thus, this listener has nothing to do on close
     */
    @Override
    public void onClose(Container container) {
        // no-op
    }

    private String findIcon(final AnnotatedElement type) {
        return ofNullable(type.getAnnotation(Icon.class)).map(i -> i.value() == Icon.IconType.CUSTOM
                ? of(i.custom()).filter(s -> !s.isEmpty()).orElse("default") : i.value().getKey()).orElse("default");
    }

}
