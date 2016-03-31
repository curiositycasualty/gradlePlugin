package org.labkey.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * Task to compile XSD schema files into Java class files using he ant XMLBean
 */
class SchemaCompile extends DefaultTask {

  @TaskAction
  def compile() {
    ant.taskdef(
            name: 'xmlbean',
            classname: 'org.apache.xmlbeans.impl.tool.XMLBean',
            classpath: project.rootProject.configurations.xmlbeans.asPath
    )
    ant.xmlbean(
            schema: project.xmlBean.schemasDir,
            javasource: project.sourceCompatibility,
            srcgendir: project.xmlBean.srcGenDir,
            classgendir: project.xmlBean.classDir,
            classpath: project.rootProject.configurations.xmlbeans.asPath,
            failonerror: true
    )
  }
}