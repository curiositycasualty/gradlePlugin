/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.plugin.extension.NpmRunExtension
import org.labkey.gradle.util.GroupNames

/**
 * Used to add tasks for running npm commands for a module.
 *
 * Borrowed heavily from https://plugins.gradle.org/plugin/com.palantir.npm-run
 */
class NpmRun implements Plugin<Project>
{
    public static final String NPM_PROJECT_FILE = "package.json"
    public static final String NPM_PROJECT_LOCK_FILE = "package-lock.json"
    public static final String TYPESCRIPT_CONFIG_FILE = "tsconfig.json"
    public static final String NODE_MODULES_DIR = "node_modules"
    public static final String WEBPACK_DIR = "webpack"

    private static final String EXTENSION_NAME = "npmRun"

    static boolean isApplicable(Project project)
    {
        return project.file(NPM_PROJECT_FILE).exists()
    }

    @Override
    void apply(Project project)
    {
        project.extensions.create(EXTENSION_NAME, NpmRunExtension)

        project.afterEvaluate {
            addTasks(project)
        }
    }

    private void addTasks(Project project)
    {
        project.task("npmRunClean")
                {Task task ->
                    task.group = GroupNames.NPM_RUN
                    task.description = "Runs 'npm run ${project.npmRun.clean}'"
                    task.dependsOn "npmInstall"
                    task.dependsOn "npm_run_${project.npmRun.clean}"
                    task.mustRunAfter "npmInstall"
                }

        project.task("npmRunBuildProd")
                {Task task ->
                    task.group = GroupNames.NPM_RUN
                    task.description = "Runs 'npm run ${project.npmRun.buildProd}'"
                    task.dependsOn "npmInstall"
                    task.dependsOn "npm_run_${project.npmRun.buildProd}"
                    task.mustRunAfter "npmInstall"
                }
        addTaskInputOutput(project.tasks.npmRunBuildProd)
        addTaskInputOutput(project.tasks.getByName("npm_run_${project.npmRun.buildProd}"))

        project.task("npmRunBuild")
                {Task task ->
                    task.group = GroupNames.NPM_RUN
                    task.description ="Runs 'npm run ${project.npmRun.buildDev}'"
                    task.dependsOn "npmInstall"
                    task.dependsOn "npm_run_${project.npmRun.buildDev}"
                    task.mustRunAfter "npmInstall"
                }
        addTaskInputOutput(project.tasks.npmRunBuild)
        addTaskInputOutput(project.tasks.getByName("npm_run_${project.npmRun.buildDev}"))

        String runCommand = LabKeyExtension.isDevMode(project) ? "npmRunBuild" : "npmRunBuildProd"
        if (project.tasks.findByName("module") != null)
            project.tasks.module.dependsOn(runCommand)
        if (project.tasks.findByName("processModuleResources") != null)
            project.tasks.processModuleResources.dependsOn(runCommand)

        project.tasks.npmInstall {Task task ->
            task.inputs.file project.file(NPM_PROJECT_FILE)
            if (project.file(NPM_PROJECT_LOCK_FILE).exists())
                task.inputs.file project.file(NPM_PROJECT_LOCK_FILE)
        }
        project.tasks.npmInstall.outputs.upToDateWhen { project.file(NODE_MODULES_DIR).exists() }
    }


    private void addTaskInputOutput(Task task)
    {
        task.inputs.file task.project.file(NPM_PROJECT_FILE)
        task.inputs.file task.project.file(TYPESCRIPT_CONFIG_FILE)
        task.inputs.dir task.project.file(WEBPACK_DIR)

        // common input file pattern for client source
        task.inputs.files task.project.fileTree(dir: "src", includes: ["client/**/*", "theme/**/*"])

        // "core" theme building
        task.inputs.files task.project.fileTree(dir: "resources", includes: ["styles/**/*", "themes/**/*"])

        // common output file pattern for client artifacts
        task.outputs.files task.project.fileTree(dir: "resources", includes: ["web/**/*"])
    }
}

