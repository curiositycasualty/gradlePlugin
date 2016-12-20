package org.labkey.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DeleteSpec
import org.gradle.api.tasks.Delete
import org.labkey.gradle.task.StartTomcat
import org.labkey.gradle.task.StopTomcat
import org.labkey.gradle.util.GroupNames
/**
 * Plugin for starting and stopping tomcat
 */
class Tomcat implements Plugin<Project>
{
    @Override
    void apply(Project project)
    {
        project.extensions.create("tomcat",TomcatExtension)
        if (project.plugins.hasPlugin(TestRunner.class))
        {
            TestRunnerExtension testEx = (TestRunnerExtension) project.getExtensions().getByName("testRunner")
            project.tomcat.assertionFlag = testEx.getTestProperty("disableAssertions") ? "-da" : "-ea"
        }
        addTasks(project)
    }


    private static void addTasks(Project project)
    {
        project.task(
                "startTomcat",
                group: GroupNames.WEB_APPLICATION,
                description: "Start the local Tomcat instance",
                type: StartTomcat
        )
        project.task(
                "stopTomcat",
                group: GroupNames.WEB_APPLICATION,
                description: "Stop the local Tomcat instance",
                type: StopTomcat
        )
        project.task(
                "cleanLogs",
                group: GroupNames.WEB_APPLICATION,
                description: "Delete logs from ${project.tomcatDir}",
                type: Delete,
                {
                    DeleteSpec spec -> spec.delete project.fileTree("${project.tomcatDir}/logs")
                }
        )
        project.task(
                "cleanTemp",
                group: GroupNames.WEB_APPLICATION,
                description: "Delete  temp files from ${project.tomcatDir}",
                type: Delete,
                {
                   DeleteSpec spec -> spec.delete project.fileTree("${project.tomcatDir}/temp")
                }
        )
    }
}

class TomcatExtension
{
    boolean devMode = true
    String assertionFlag = "-ea" // set to -da to disable assertions
    String maxMemory = "1G"
    boolean recompileJsp = true
    String trustStore = ""
    String trustStorePassword = ""
    String catalinaOpts = ""
}
