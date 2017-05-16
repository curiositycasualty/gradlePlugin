package org.labkey.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.task.GzipAction
import org.labkey.gradle.task.WriteDependenciesFile
/**
 * TODO probably more efficient to fold this into the SimpleModule plugin
 */
class ModuleResources implements Plugin<Project>
{
    private static final String DIR_NAME = "resources"

    static boolean isApplicable(Project project)
    {
        return project.file(DIR_NAME).exists()
    }

    @Override
    void apply(Project project)
    {
        project.apply plugin: 'java-base'
        addSourceSet(project)
        addTasks(project)
    }

    private void addTasks(Project project)
    {
        Task writeDependenciesFile = project.task("writeDependenciesList",
                type: WriteDependenciesFile,
                description: "write a list of direct external dependencies that should be checked on the credits page"
        )
        project.tasks.processModuleResources.dependsOn(writeDependenciesFile)
        project.tasks.clean.dependsOn(project.tasks.cleanWriteDependenciesList)
    }

    private void addSourceSet(Project project)
    {
        project.sourceSets
                {
                    module {
                        resources {
                            srcDirs = [DIR_NAME]
                            exclude "schemas/**/obsolete/**"
                        }
                        output.resourcesDir = project.labkey.explodedModuleDir
                    }
                }
        if (!LabKeyExtension.isDevMode(project))
            project.tasks.processModuleResources.doLast(new GzipAction())
        project.tasks.processResources.dependsOn('processModuleResources')
    }
}
