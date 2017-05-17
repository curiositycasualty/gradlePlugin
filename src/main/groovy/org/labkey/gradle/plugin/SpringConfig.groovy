package org.labkey.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.labkey.gradle.util.BuildUtils

/**
 * Used for copying the Spring config files to the module's build directory.
 */
class SpringConfig implements Plugin<Project>
{
    private static final DIR_PREFIX = "webapp/WEB-INF"
    String _dirName

    static boolean isApplicable(Project project)
    {
        return project.file("${DIR_PREFIX}/${project.name}").exists()
    }

    @Override
    void apply(Project project)
    {
        _dirName = "${DIR_PREFIX}/${project.name}"
        project.apply plugin: 'java-base'

        addSourceSet(project)
        addDependencies(project)
    }

    private void addSourceSet(Project project)
    {
        project.sourceSets
                {
                    spring {
                        resources {
                            srcDirs = [_dirName]
                        }
                        output.resourcesDir = project.labkey.explodedModuleConfigDir
                    }
                }
        project.tasks.processResources.dependsOn(project.tasks.processSpringResources)
    }

    private static void addDependencies(Project project)
    {
        // Issue 30155: without this, the spring xml files will not find the classes in the api jar
        if (BuildUtils.isIntellij())
        {
            project.dependencies.add("springImplementation", project.project(":server:api").tasks.jar.outputs.files)
            if (project.tasks.findByName("jar") != null)
                project.dependencies.add("springImplementation", project.tasks.jar.outputs.files)
            if (project.tasks.findByName("apiJar") != null)
                project.dependencies.add("springImplementation", project.tasks.apiJar.outputs.files)
        }
    }
}