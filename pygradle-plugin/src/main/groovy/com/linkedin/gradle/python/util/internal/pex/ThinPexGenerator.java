/*
 * Copyright 2016 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.linkedin.gradle.python.util.internal.pex;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.linkedin.gradle.python.util.pip.PipFreezeAction;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.process.ExecResult;

import com.linkedin.gradle.python.PythonExtension;
import com.linkedin.gradle.python.extension.DeployableExtension;
import com.linkedin.gradle.python.util.EntryPointHelpers;
import com.linkedin.gradle.python.util.ExtensionUtils;
import com.linkedin.gradle.python.util.PexFileUtil;
import com.linkedin.gradle.python.util.entrypoint.EntryPointWriter;
import com.linkedin.gradle.python.util.pex.EntryPointTemplateProvider;


public class ThinPexGenerator implements PexGenerator {

    private static final Logger logger = Logging.getLogger(ThinPexGenerator.class);

    private final Project project;
    private final List<String> pexOptions;
    private final EntryPointTemplateProvider templateProvider;
    private final Map<String, String> extraProperties;

    public ThinPexGenerator(
            Project project,
            List<String> pexOptions,
            EntryPointTemplateProvider templateProvider,
            Map<String, String> extraProperties) {
        this.project = project;
        this.pexOptions = pexOptions;
        this.templateProvider = templateProvider;
        this.extraProperties = extraProperties == null ? new HashMap<String, String>() : extraProperties;
    }

    @Override
    public void buildEntryPoints() throws Exception {
        PythonExtension extension = ExtensionUtils.getPythonExtension(project);
        DeployableExtension deployableExtension = ExtensionUtils.getPythonComponentExtension(
                extension, DeployableExtension.class);

        Map<String, String> dependencies = new PipFreezeAction(project).getDependencies();

        PexExecSpecAction action = PexExecSpecAction.withOutEntryPoint(
                project, project.getName(), pexOptions, dependencies);

        ExecResult exec = project.exec(action);
        new PexExecOutputParser(action, exec).validatePexBuildSuccessfully();

        for (String it : EntryPointHelpers.collectEntryPoints(project)) {
            logger.lifecycle("Processing entry point: {}", it);
            String[] split = it.split("=");
            String name = split[0].trim();
            String entry = split[1].trim();

            Map<String, String> propertyMap = new HashMap<>();
            propertyMap.putAll(extraProperties);
            propertyMap.put("realPex", PexFileUtil.createThinPexFilename(project.getName()));
            propertyMap.put("entryPoint", entry);

            DefaultTemplateProviderOptions providerOptions = new DefaultTemplateProviderOptions(project, extension, entry);
            new EntryPointWriter(project, templateProvider.retrieveTemplate(providerOptions))
                .writeEntryPoint(new File(deployableExtension.getDeployableBinDir(), name), propertyMap);
        }
    }
}
