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
package org.labkey.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.util.DatabaseProperties
import org.labkey.gradle.util.PropertiesUtils

class DoThenSetup extends DefaultTask
{
    protected DatabaseProperties databaseProperties
    boolean dbPropertiesChanged = false

    Closure<Void> fn = {
        setDatabaseProperties()
    }

    DoThenSetup()
    {
        if (project.findProject(":server") != null)
            this.dependsOn project.project(":server").configurations.tomcatJars
    }

    @TaskAction
    void setup() {
        project.println("DTS.setup pre-fn: ${databaseProperties.configProperties}")
        getFn().run()
        project.println("DTS.setup post-fn: ${databaseProperties.configProperties}")


        String appDocBase = project.serverDeploy.webappDir.toString().split("[/\\\\]").join("${File.separator}")

        if (!labkeyXmlUpToDate(appDocBase))
        {
            //ant setup copy portions. Setting jdbc props is now handled by pick_db and bootstrap.
            Properties configProperties = databaseProperties.getConfigProperties()
            configProperties.setProperty("appDocBase", appDocBase)
            boolean isNextLineComment = false

            project.copy({ CopySpec copy ->
                copy.from "${project.rootProject.projectDir}/webapps"
                copy.into "${project.rootProject.buildDir}"
                copy.include "labkey.xml"
                copy.filter({ String line ->
                    String newLine = line;

                    if (project.ext.has('enableJms') && project.ext.enableJms)
                    {
                        newLine = newLine.replace("<!--@@jmsConfig@@", "");
                        newLine = newLine.replace("@@jmsConfig@@-->", "");
                        return newLine;
                    }
                    if (isNextLineComment || newLine.contains("<!--"))
                    {
                        isNextLineComment = !newLine.contains("-->");
                        return newLine;
                    }
                    return PropertiesUtils.replaceProps(line, configProperties, true);
                })
            })

            project.copy({ CopySpec copy ->
                copy.from "${project.rootProject.buildDir}"
                copy.into "${project.ext.tomcatConfDir}"
                copy.include "labkey.xml"
            })
        }

        if (project.findProject(":server") != null)
            copyTomcatJars()


    }

    // labkeyXml is up to date if it was created after the current config file was created
    // and it has the current appDocBase
    private boolean labkeyXmlUpToDate(String appDocBase)
    {
        if (dbPropertiesChanged)
            return false;

        File dbPropFile = DatabaseProperties.getPickedConfigFile(project)
        File tomcatLabkeyXml = new File("${project.ext.tomcatConfDir}", "labkey.xml")
        if (!dbPropFile.exists() || !tomcatLabkeyXml.exists())
            return false
        if (dbPropFile.lastModified() < tomcatLabkeyXml.lastModified())
        {
            // make sure we haven't switch contexts
            for (String line: tomcatLabkeyXml.readLines())
            {
                if (line.contains("docBase=\"" + appDocBase + "\""))
                    return true
            }
        }
        return false
    }

    private void copyTomcatJars()
    {
        Project serverProject = project.project(":server")
        // for consistency with a distribution deployment and the treatment of all other deployment artifacts,
        // first copy the tomcat jars into the staging directory
        project.ant.copy(

                todir: project.staging.tomcatLibDir,
                preserveLastModified: true
        )
            {
                serverProject.configurations.tomcatJars { Configuration collection ->
                    collection.addToAntBuilder(project.ant, "fileset", FileCollection.AntType.FileSet)
                }
                // Put unversioned files into the tomcatLibDir.  These files are meant to be copied into
                // the tomcat/lib directory when deploying a build or a distribution.  When version numbers change,
                // you will end up with multiple versions of these jar files on the classpath, which will often
                // result in problems of compatibility.  Additionally, we want to maintain the (incorrect) names
                // of the files that have been used with the Ant build process.
                //
                // We may employ CATALINA_BASE in order to separate our libraries from the ones that come with
                // the tomcat distribution. This will require updating our instructions for installation by clients
                // but would allow us to use artifacts with more self-documenting names.
                chainedmapper()
                        {
                            flattenmapper()
                            // get rid of the version numbers on the jar files
                            // matches on: name-X.Y.Z-SNAPSHOT.jar, name-X.Y.Z_branch-SNAPSHOT.jar, name-X.Y.Z.jar
                            regexpmapper(from: "^(.*?)(-\\d+(\\.\\d+)*(_.+)?(-SNAPSHOT)?)?\\.jar", to: "\\1.jar")
                            filtermapper()
                                    {
                                        replacestring(from: "mysql-connector-java", to: "mysql") // the Ant build used mysql.jar
                                        replacestring(from: "javax.mail", to: "mail") // the Ant build used mail.jar
                                    }
                        }
            }

        // Then copy them into the tomcat/lib directory
        project.ant.copy(
            todir: "${project.tomcatDir}/lib",
            preserveLastModified: true
        )
        {
            fileset(dir: project.staging.tomcatLibDir)
        }
    }

    protected void setDatabaseProperties()
    {
        databaseProperties = new DatabaseProperties(project, false)
    }

    void setDatabaseProperties(DatabaseProperties dbProperties)
    {
        this.databaseProperties = dbProperties
    }

    DatabaseProperties getDatabaseProperties()
    {
        return databaseProperties
    }
}
