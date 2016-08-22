package org.labkey.gradle.plugin

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Jar

import java.text.SimpleDateFormat
import java.util.regex.Matcher
import java.util.regex.Pattern
/**
 * This class is used for building a LabKey module (one that typically resides in a *modules
 * directory).  It defines tasks for building the jar files (<module>_api.jar, <module>_jsp.jar, <module>.jar, <module>_schemas.jar)
 * as well as tasks for copying resources to the build directory.
 *
 * Created by susanh on 4/5/16.
 */
class SimpleModule extends LabKey
{
    // Deprecated: instead of creating this file,
    // set the skipBuild property to true in the module's build.gradle file
    //   ext.skipBuild = true
    def String _skipBuildFile = "skipBuild.txt"
    def String _modulePropertiesFile = "module.properties"
    private static final String ENLISTMENT_PROPERTIES = "enlistment.properties"
    def Properties _moduleProperties;
    def Project _project;
    def Pattern PROPERTY_PATTERN = Pattern.compile("@@([^@]+)@@")

    @Override
    void apply(Project project)
    {
        _project = project;

        _project.apply plugin: 'java-base'
        setJavaBuildProperties()

        _project.build.onlyIf({
            def List<String> indicators = new ArrayList<>();
            if (project.file(_skipBuildFile).exists())
                indicators.add(_skipBuildFile + " exists")
            if (!project.file(_modulePropertiesFile).exists())
                indicators.add(_modulePropertiesFile + " does not exist")
            if (project.labkey.skipBuild)
                indicators.add("skipBuild property set for Gradle project")

            if (indicators.size() > 0)
            {
                project.logger.info("$project.name build skipped because: " + indicators.join("; "))
            }
            return indicators.isEmpty()
        })

        applyPlugins()
        addConfiguration()
        setModuleProperties()
        addTasks()
        addArtifacts()
    }

    protected void applyPlugins()
    {
        _project.apply plugin: 'org.labkey.xmlBeans'
        _project.apply plugin: 'org.labkey.resources'
        if (_project.file(Api.SOURCE_DIR).exists())
            _project.apply plugin: 'org.labkey.api'

        _project.apply plugin: 'org.labkey.springConfig'
        _project.apply plugin: 'org.labkey.webapp'
        _project.apply plugin: 'org.labkey.libResources'
        _project.apply plugin: 'org.labkey.clientLibraries'

//        _project.apply plugin: "com.jfrog.artifactory"

        _project.apply plugin: 'maven'
        _project.apply plugin: 'maven-publish'

        _project.apply plugin: 'org.labkey.jsp'

        if (_project.file(Gwt.SOURCE_DIR).exists())
            _project.apply plugin: 'org.labkey.gwt'

        if (_project.file(Distribution.DIRECTORY).exists())
            _project.apply plugin: 'org.labkey.distribution'

        if (_project.file(NpmRun.NPM_PROJECT_FILE).exists())
        {
            // This brings in nodeSetup and npmInstall tasks.  See https://github.com/srs/gradle-node-plugin
            _project.apply plugin: 'com.moowork.node'
            _project.apply plugin: 'org.labkey.npmRun'
        }
    }

    private void addConfiguration()
    {
        _project.configurations
                {
                    published
                }
    }

    protected void setJavaBuildProperties()
    {
        _project.sourceCompatibility = _project.labkey.sourceCompatibility
        _project.targetCompatibility = _project.labkey.targetCompatibility

        _project.libsDirName = 'explodedModule/lib'

        addSourceSets()

        _project.jar {
            manifest.attributes provider: 'LabKey'
            // TODO set other attributes for manifest?
            baseName project.name
        }
    }

    private void addSourceSets()
    {
        _project.sourceSets {
            main {
                java {
                    srcDirs = ['src']
                }
                resources {
                    srcDirs = ['src']
                    exclude '**/*.java'
                    exclude '**/*.jsp'
                }
            }
        }
    }

    private void setVcsProperties()
    {
        if (_project.plugins.hasPlugin("org.labkey.versioning"))
        {
            _moduleProperties.setProperty("VcsURL", _project.versioning.info.url)
            _moduleProperties.setProperty("VcsRevision", _project.versioning.info.commit)
            _moduleProperties.setProperty("BuildNumber", _project.versioning.info.build)
        }
        else
        {
            _moduleProperties.setProperty("VcsURL", "Not built from a source control working copy")
            _moduleProperties.setProperty("VcsRevision", "Not built from a source control working copy")
            _moduleProperties.setProperty("BuildNumber", "Not built from a source control working copy")
        }
    }

    private setEnlistmentId()
    {
        File enlistmentFile = new File(_project.getRootProject().getProjectDir(), ENLISTMENT_PROPERTIES)
        Properties enlistmentProperties = new Properties()
        if (!enlistmentFile.exists())
        {
            UUID id = UUID.randomUUID()
            enlistmentProperties.setProperty("enlistment.id", id.toString())
            enlistmentProperties.store(new FileWriter(enlistmentFile), SimpleDateFormat.getDateTimeInstance().format(new Date()))
        }
        else
        {
            readProperties(enlistmentFile, enlistmentProperties)
        }
        _moduleProperties.setProperty("EnlistmentId", enlistmentProperties.getProperty("enlistment.id"))
    }

    private void setBuildInfoProperties()
    {
        _moduleProperties.setProperty("RequiredServerVersion", "0.0")
        if (_moduleProperties.getProperty("BuildType") == null)
            _moduleProperties.setProperty("BuildType", _project.labkey.getDeployModeName(_project))
        _moduleProperties.setProperty("BuildUser", System.getProperty("user.name"))
        _moduleProperties.setProperty("BuildOS", System.getProperty("os.name"))
        _moduleProperties.setProperty("BuildTime", SimpleDateFormat.getDateTimeInstance().format(new Date()))
        _moduleProperties.setProperty("BuildPath", _project.buildDir.getAbsolutePath() )
        _moduleProperties.setProperty("SourcePath", _project.projectDir.getAbsolutePath() )
        _moduleProperties.setProperty("ResourcePath", "") // TODO  _project.getResources().... ???
        if (_moduleProperties.getProperty("ConsolidateScripts") == null)
            _moduleProperties.setProperty("ConsolidateScripts", "")
        if (_moduleProperties.getProperty("ManageVersion") == null)
            _moduleProperties.setProperty("ManageVersion", "")
    }

    private void setModuleInfoProperties()
    {
        if (_moduleProperties.getProperty("Name") == null)
            _moduleProperties.setProperty("Name", _project.name)
        if (_moduleProperties.getProperty("ModuleClass") == null)
            _moduleProperties.setProperty("ModuleClass", "org.labkey.api.module.SimpleModule")
    }

    private static void readProperties(File propertiesFile, Properties properties)
    {
        if (propertiesFile.exists())
        {
            FileInputStream is;
            try
            {
                is = new FileInputStream(propertiesFile)
                properties.load(is)
            }
            finally
            {
                if (is != null)
                    is.close()
            }
        }
    }

    protected void setModuleProperties()
    {
        File propertiesFile = _project.file(_modulePropertiesFile)
        _moduleProperties = new Properties()
        readProperties(propertiesFile, _moduleProperties)

        // remove -SNAPSHOT because the module loader does not expect or handle decorated version numbers
        _moduleProperties.setProperty("Version", _project.version.toString().replace("-SNAPSHOT", ""))
        setBuildInfoProperties()
        setModuleInfoProperties()
        setVcsProperties()
        setEnlistmentId()
    }

    protected void addTasks()
    {
        def Task moduleXmlTask = _project.task('moduleXml',
                group: "module",
                type: Copy,
                description: "create the module.xml file using module.properties",
                {
                    from _project.project(":server").projectDir
                    include 'module.template.xml'
                    rename {"module.xml"}
                    filter( { String line ->
                        //Todo: migrate to ParsingUtils
                        def Matcher matcher = PROPERTY_PATTERN.matcher(line);
                        def String newLine = line;
                        while (matcher.find())
                        {
                            if (_moduleProperties.containsKey(matcher.group(1)))
                            {
                                newLine = newLine.replace(matcher.group(), (String) _moduleProperties.get(matcher.group(1)))
                            }
                            else
                            {
                                newLine = newLine.replace(matcher.group(), "")
                            }
                        }
                        return newLine;

                    })
                    destinationDir = new File((String) _project.sourceSets.spring.output.resourcesDir)
                }
        )
        moduleXmlTask.outputs.upToDateWhen(
                {
                    Task task ->
                        File moduleXmlFile = new File((String) _project.sourceSets.spring.output.resourcesDir, "/module.xml")
                        if (!moduleXmlFile.exists())
                            return false
                        else
                        {
                            if (_project.file(_modulePropertiesFile).lastModified() > moduleXmlFile.lastModified() ||
                                _project.project(":server").file('module.template.xml').lastModified() > moduleXmlFile.lastModified())
                                return false
                        }
                        return true
                }
        )

        def Task moduleFile = _project.task("module",
                group: "module",
                type: Jar,
                description: "create the module file for this project",
                {
                    from _project.labkey.explodedModuleDir
                    exclude '**/*.uptodate'
                    exclude "META-INF/${_project.name}/**"
                    exclude 'gwt-unitCache/**'
                    baseName _project.name
                    extension 'module'
                    destinationDir = new File((String) _project.labkey.stagingModulesDir)
                }
        )
        moduleFile.dependsOn(moduleXmlTask, _project.tasks.jar)
        if (_project.hasProperty('apiJar'))
            moduleFile.dependsOn(_project.tasks.apiJar)
        if (_project.hasProperty('jspJar'))
            moduleFile.dependsOn(_project.tasks.jspJar)
        _project.tasks.build.dependsOn(moduleFile)
        _project.tasks.clean.dependsOn(_project.tasks.cleanModule)
        _project.artifacts
                {
                    published moduleFile
                }
    }

    protected void addArtifacts()
    {
        _project.publishing {
            publications {
                moduleFile(MavenPublication) {
                    artifact _project.tasks.module
                }
                if (_project.hasProperty('apiJar'))
                {
                    api(MavenPublication) {
                        artifact _project.tasks.apiJar
                    }
                }
            }
        }
//
//        _project.artifactoryPublish {
//            dependsOn _project.tasks.module
//            publications('moduleFile')
//            if (_project.hasProperty('apiJar'))
//                publications('api')
//        }
    }
}

