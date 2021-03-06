/*
 * Copyright (c) 2016-2018 LabKey Corporation
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
import org.gradle.api.file.FileTree
import org.gradle.api.specs.AndSpec
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.JavaExec
import org.labkey.gradle.plugin.extension.GwtExtension
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.task.GzipAction
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.GroupNames

/**
 * Used to compile GWT source files into Javascript
 */
class Gwt implements Plugin<Project>
{
    public static final String SOURCE_DIR = "gwtsrc"

    private static final String GWT_EXTENSION = ".gwt.xml"

    static boolean isApplicable(Project project)
    {
        return project.file(SOURCE_DIR).exists()
    }

    @Override
    void apply(Project project)
    {
        project.apply plugin: 'java-base'
        project.extensions.create("gwt", GwtExtension)
        if (LabKeyExtension.isDevMode(project))
        {
            project.gwt.style = "PRETTY"
            project.gwt.draftCompile = true
            project.gwt.allBrowserCompile = false
        }

        addConfigurations(project)
        addDependencies(project)
        addSourceSet(project)
        addTasks(project)
    }

    private void addConfigurations(Project project)
    {

        project.configurations
                {
                    gwtCompile
                }
    }

    private void addDependencies(Project project)
    {
        // Be backwards-compatible for builds that still reference GXT and GWT-DND
        if (project.hasProperty("gxtVersion") && project.hasProperty("gwtDndVersion"))
        {
            String gxtGroup = (BuildUtils.compareVersions(project.gxtVersion, "2.2.5") > 0) ? "com.sencha.gxt" : "com.extjs"

            project.dependencies {
                gwtCompile "com.google.gwt:gwt-user:${project.gwtVersion}",
                        "com.google.gwt:gwt-dev:${project.gwtVersion}",
                        "${gxtGroup}:gxt:${project.gxtVersion}",
                        "com.allen-sauer.gwt.dnd:gwt-dnd:${project.gwtDndVersion}",
                        "javax.validation:validation-api:${project.validationApiVersion}"
            }
        }
        else
        {
            project.dependencies {
                gwtCompile "com.google.gwt:gwt-user:${project.gwtVersion}",
                        "com.google.gwt:gwt-dev:${project.gwtVersion}",
                        "javax.validation:validation-api:${project.validationApiVersion}"
            }
        }
    }

    private void addSourceSet(Project project)
    {
        project.sourceSets {
            gwt {
                java {
                    srcDir project.gwt.srcDir
                }
            }
            main {
                java {
                    srcDir project.gwt.srcDir
                }
            }
        }
    }

    private void addTasks(Project project)
    {
        Map<String, String> gwtModuleClasses = getGwtModuleClasses(project)
        List<Task> gwtTasks = new ArrayList<>(gwtModuleClasses.size());
        gwtModuleClasses.entrySet().each {
             gwtModuleClass ->

                String taskName ='compileGwt' + gwtModuleClass.getKey()
                project.tasks.register(taskName, JavaExec) {
                    JavaExec java ->
                        java.outputs.cacheIf {true}
                        java.group = GroupNames.GWT
                        java.description = "compile GWT source files for " + gwtModuleClass.getKey() + " into JS"

                        GString extrasDir = "${project.buildDir}/${project.gwt.extrasDir}"
                        String outputDir = "${project.buildDir}/${project.gwt.outputDir}"

                        java.inputs.files(project.sourceSets.gwt.java.srcDirs)

                        java.outputs.dir extrasDir
                        java.outputs.dir outputDir

                        // Workaround for incremental build (GRADLE-1483)
                        java.outputs.upToDateSpec = new AndSpec()

                        java.doFirst {
                            project.file(extrasDir).mkdirs()
                            project.file(outputDir).mkdirs()
                        }

                        if (!LabKeyExtension.isDevMode(project))
                        {
                            java.doLast new GzipAction()
                        }

                        java.main = 'com.google.gwt.dev.Compiler'

                        def paths = []

                        paths += [
                                project.sourceSets.gwt.compileClasspath,       // Dep
                                project.sourceSets.gwt.java.srcDirs           // Java source
                        ]
                        String apiProjectPath = BuildUtils.getApiProjectPath(project.gradle)
                        if (project.findProject(apiProjectPath) != null && project.project(apiProjectPath).file(project.gwt.srcDir).exists())
                            paths += [project.project(apiProjectPath).file(project.gwt.srcDir)]
                        java.classpath paths

                        java.args =
                                [
                                        '-war', outputDir,
                                        '-style', project.gwt.style,
                                        '-logLevel', project.gwt.logLevel,
                                        '-extra', extrasDir,
                                        '-deploy', extrasDir,
                                        '-localWorkers', 4,
                                        gwtModuleClass.getValue()
                                ]
                        if (project.gwt.draftCompile)
                            java.args.add('-draftCompile')
                        java.jvmArgs =
                                [
                                        '-Xss1024k',
                                        '-Djava.awt.headless=true'
                                ]

                        java.maxHeapSize = '512m'

                }
                gwtTasks.add(project.tasks.named(taskName))
        }
        project.tasks.register('compileGwt', Copy) {
            Copy copy ->
                copy.from gwtTasks
                copy.into project.labkey.explodedModuleWebDir
                copy.description = "compile all GWT source files into JS and copy them to the module's web directory"
                copy.group = GroupNames.GWT
        }

        project.tasks.classes.dependsOn(project.tasks.compileGwt)
    }

    private static Map<String, String> getGwtModuleClasses(Project project)
    {
        File gwtSrc = project.file(project.gwt.srcDir)
        FileTree tree = project.fileTree(dir: gwtSrc, includes: ["**/*${GWT_EXTENSION}"]);
        Map<String, String> nameToClass = new HashMap<>();
        String separator = System.getProperty("file.separator").equals("\\") ? "\\\\" : System.getProperty("file.separator");
        for (File file : tree.getFiles())
        {
            String className = file.getPath()
            className = className.substring(gwtSrc.getPath().length() + 1); // lop off the part of the path before the package structure
            className = className.replaceAll(separator, "."); // convert from path to class package
            className = className.substring(0, className.indexOf(GWT_EXTENSION)); // remove suffix
            nameToClass.put(file.getName().substring(0, file.getName().indexOf(GWT_EXTENSION)),className);
        }
        return nameToClass;
    }

}

