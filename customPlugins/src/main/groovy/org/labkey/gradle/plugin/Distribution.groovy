package org.labkey.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.labkey.gradle.task.PackageDistribution

class Distribution implements Plugin<Project>
{
    public static final String DIRECTORY = "distributions"
    public static final String GROUP_NAME = "Distribution"

    @Override
    void apply(Project project)
    {

        project.extensions.create("dist", DistributionExtension)

        project.dist {
            distModulesDir = "${project.rootProject.buildDir}/distModules"
        }
        addConfigurations(project)
        addTasks(project)
    }

    private void addConfigurations(Project project)
    {
        project.configurations
                {
                    distribution
                }
    }

    private static void addTasks(Project project)
    {
        def Task dist = project.task(
                "distribution",
                group: GROUP_NAME,
                description: "Make LabKey distribution for a single module",
                type: PackageDistribution
        )
        dist.dependsOn(project.configurations.distribution)
        if (project.rootProject.hasProperty("distAll"))
            project.rootProject.tasks.distAll.dependsOn(dist)
    }

    public static void inheritDependencies(Project project, String inheritedProjectPath)
    {
        project.project(inheritedProjectPath).configurations.distribution.dependencies.each {
            project.dependencies.add("distribution", it)
        }
    }
}


class DistributionExtension
{
    def String distModulesDir

    // properties used in the installer/build.xml file
    def String subDirName
    def String extraFileIdentifier
}