/**
 * Copyright (C) 2006-2018 Talend Inc. - www.talend.com
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
package org.talend.sdk.component.maven;

import static org.apache.maven.plugins.annotations.ResolutionScope.COMPILE_PLUS_RUNTIME;

import java.io.File;
import java.util.Map;

import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProjectHelper;
import org.talend.sdk.component.tools.CarBundler;

/**
 * Bundles the component as a component archive (.car).
 */
@Mojo(name = "car", requiresDependencyResolution = COMPILE_PLUS_RUNTIME)
public class CarMojo extends DependencyAwareMojo {

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.car",
            property = "talend.car.output")
    private File output;

    /**
     * Should the component archive be attached.
     */
    @Parameter(defaultValue = "true", property = "talend.car.attach")
    private boolean attach;

    /**
     * The classifier to use if attach is true.
     */
    @Parameter(defaultValue = "component", property = "talend.car.classifier")
    private String classifier;

    /**
     * Additional custom metadata to bundle in the component archive.
     */
    @Parameter
    private Map<String, String> metadata;

    @Component
    private MavenProjectHelper helper;

    @Override
    public void execute() {
        final CarBundler.Configuration configuration = new CarBundler.Configuration();
        configuration.setMainGav(mainGav());
        configuration.setOutput(output);
        configuration.setArtifacts(artifacts());
        configuration.setVersion(project.getVersion());
        configuration.setCustomMetadata(metadata);
        new CarBundler(configuration, getLog()).run();
        if (attach) {
            helper.attachArtifact(project, output, classifier);
            getLog().info("Attached " + output + " with classifier " + classifier);
        }
    }
}
