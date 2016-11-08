package org.labkey.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.bundling.Jar
import org.labkey.gradle.task.SchemaCompile
import org.labkey.gradle.util.GroupNames

/**
 * Class that will convert xsd files into a jar file
 */
class XmlBeans implements Plugin<Project>
{
    public static final String CLASSIFIER = "schemas"

    def void apply(Project project)
    {
        project.extensions.create("xmlBeans", XmlBeansExtension)

        addDependencies(project)
        addTasks(project)
        addArtifacts(project)

    }

    public static boolean isApplicable(Project project)
    {
        return !AntBuild.isApplicable(project) && project.file(project.xmlBeans.schemasDir).exists()
    }

    private void addDependencies(Project project)
    {
        project.configurations
                {
                    xmlbeans
                    xmlSchema // N.B.  This cannot be called xmlBeans or it won't be found
                }
        project.dependencies
                {
                    xmlbeans "org.apache.xmlbeans:xbean:${project.xmlbeansVersion}"
                }
    }

    private void addArtifacts(Project project)
    {
        project.artifacts {
            xmlSchema project.tasks.schemasJar
        }
    }

    private void addTasks(Project project)
    {
        def Task schemasCompile = project.task('schemasCompile',
                group: GroupNames.XML_SCHEMA,
                type: SchemaCompile,
                description: "compile XML schemas from directory '$project.xmlBeans.schemasDir' into Java classes",
                {
                    inputs.dir  project.xmlBeans.schemasDir
                    outputs.dir "$project.labkey.srcGenDir/$project.xmlBeans.classDir"
                }
        )
        schemasCompile.onlyIf {
            isApplicable(project)
        }

        def Task schemasJar = project.task('schemasJar',
                group: GroupNames.XML_SCHEMA,
                type: Jar,
                description: "produce schemas jar file from directory '$project.xmlBeans.classDir'",
                {
                    classifier CLASSIFIER
                    from "$project.buildDir/$project.xmlBeans.classDir"
                    exclude '**/*.java'
                    baseName project.name.equals("schemas") ? "schemas": "${project.name}_schemas"
                    version project.version
                    group project.group
                    destinationDir = project.file(project.labkey.explodedModuleLibDir)
                }
        )
        schemasJar.dependsOn(schemasCompile)
        schemasJar.onlyIf
                {
                    isApplicable(project)
                }

        project.task("cleanSchemasJar",
                group: GroupNames.XML_SCHEMA,
                type: Delete,
                description: "remove schema jar file",
                {
                    delete "$schemasJar.destinationDir/$schemasJar.archiveName"
                }
        )

        project.task("cleanSchemasCompile",
                group: GroupNames.XML_SCHEMA,
                type: Delete,
                description: "remove source and class files generated from xsd files",
                {
                    delete "$project.buildDir/$project.xmlBeans.classDir",
                    "$project.labkey.srcGenDir/$project.xmlBeans.classDir"
                }
        )
    }
}

class XmlBeansExtension
{
    def String schemasDir = "schemas" // the directory containing the schemas to be compiled
    def String classDir = "xb" // the name of the directory in build or build/gensrc for the source and class files
}

