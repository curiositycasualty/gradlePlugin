package org.labkey.gradle.task

import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.SystemUtils
import org.gradle.api.DefaultTask
import org.gradle.api.file.CopySpec
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecSpec
import org.labkey.gradle.plugin.DistributionExtension
import org.labkey.gradle.plugin.StagingExtension
import org.labkey.gradle.util.PropertiesUtils

import java.nio.file.Files
import java.nio.file.Paths

class ModuleDistribution extends DefaultTask
{
    Boolean includeWindowsInstaller = false
    Boolean includeZipArchive = false
    Boolean includeTarGZArchive = false
    Boolean makeDistribution = true // set to false for the "extra modules"
    String extraFileIdentifier = ""
    Boolean includeMassSpecBinaries = false
    String versionPrefix = null
    String subDirName
    String artifactName

    File distributionDir

    String archivePrefix
    DistributionExtension distExtension

    ModuleDistribution()
    {
        description = "Make a LabKey modules distribution"
        distExtension = project.extensions.findByType(DistributionExtension.class)

        this.dependsOn(project.project(":server").tasks.stageTomcatJars)
    }

    File getDistributionDir()
    {
        if (distributionDir == null && subDirName != null)
            distributionDir = project.file("${distExtension.dir}/${subDirName}")
        return distributionDir
    }

    @OutputFiles
    List<File> getDistFiles()
    {
        List<File> distFiles = new ArrayList<>()

        if (includeTarGZArchive)
            distFiles.add(new File(getTarArchivePath()))
        if (includeZipArchive)
            distFiles.add(new File(getZipArchivePath()))
        if (includeWindowsInstaller && SystemUtils.IS_OS_WINDOWS)
            distFiles.add(new File(getDistributionDir(), getWindowsInstallerName()))
        return distFiles
    }

    @TaskAction
    void doAction()
    {
        init()

        if (makeDistribution)
            createDistributionFiles()
        gatherModules()
        packageRedistributables()

    }

    private String getVersionPrefix()
    {
        if (versionPrefix == null)
            versionPrefix = "Labkey${project.installerVersion}${extraFileIdentifier}"
        return versionPrefix
    }

    private String getArchivePrefix()
    {
        if (archivePrefix == null)
            archivePrefix =  "${getVersionPrefix()}-bin"
        return archivePrefix
    }

    private void init()
    {
        // FIXME why is it necessary to do this mkdirs?
        project.buildDir.mkdirs()
        distributionDir.deleteDir()
        new File(distExtension.extraSrcDir).deleteDir()
        // because we gather up all modules put into this directory, we always want to start clean
        // TODO we can probably avoid using this altogether by just copying from the distribution configuration
        new File(distExtension.modulesDir).deleteDir()
    }

    private void gatherModules()
    {
        project.copy
        { CopySpec copy ->
            copy.from { project.configurations.distribution }
            copy.into distExtension.modulesDir
        }
    }

    private void packageRedistributables()
    {
        if (makeDistribution)
        {
            copyLibXml()
            packageInstallers()
        }
        packageArchives()
    }

    private void copyLibXml()
    {
        Properties copyProps = new Properties()
        //The Windows installer only supports Postgres, which it also installs.
        copyProps.put("jdbcURL", "jdbc:postgresql://localhost/labkey")
        copyProps.put("jdbcDriverClassName", "org.postgresql.Driver")

        project.copy
        { CopySpec copy ->
            copy.from("${project.rootProject.projectDir}/webapps")
            copy.include("labkey.xml")
            copy.into(project.buildDir)
            copy.filter({ String line ->
                return PropertiesUtils.replaceProps(line, copyProps, true)
            })
        }
    }

    private void packageInstallers()
    {
        if (includeWindowsInstaller && SystemUtils.IS_OS_WINDOWS) {
            project.exec
            { ExecSpec spec ->
                spec.commandLine FilenameUtils.separatorsToSystem("${distExtension.installerSrcDir}/nsis2.46/makensis.exe")
                spec.args = [
                        "/DPRODUCT_VERSION=\"${project.version}\"",
                        "/DPRODUCT_REVISION=\"${project.vcsRevision}\"",
                        FilenameUtils.separatorsToSystem("${distExtension.installerSrcDir}/labkey_installer.nsi")
                ]
            }

            project.copy
            { CopySpec copy ->
                copy.from("${project.buildDir}/..") // makensis puts the installer into build/installer without the project name subdirectory
                copy.include("Setup_includeJRE.exe")
                copy.into(getDistributionDir())
                copy.rename("Setup_includeJRE.exe", getWindowsInstallerName())
            }
        }
    }

    private void packageArchives()
    {
        if (includeTarGZArchive)
        {
            tarArchives()
        }
        if (includeZipArchive)
        {
            zipArchives()
        }
    }

    private String getWindowsInstallerName()
    {
        return "${getVersionPrefix()}-Setup.exe"
    }

    String getArtifactId()
    {
        return subDirName
    }

    String getArtifactName()
    {
        if (artifactName == null)
        {
            if (makeDistribution)
                artifactName = getArchivePrefix()
            else
                artifactName = getVersionPrefix()
        }
        return artifactName
    }

    private String getTarArchivePath()
    {
        return "${getDistributionDir()}/${getArtifactName()}.tar.gz"
    }

    private String getZipArchivePath()
    {
        return "${getDistributionDir()}/${getArtifactName()}.zip"
    }

    private void tarArchives()
    {
        String archivePrefix = getArchivePrefix()
        if (makeDistribution)
        {
            StagingExtension staging = project.getExtensions().getByType(StagingExtension.class)

            ant.tar(tarfile: getTarArchivePath(),
                    longfile: "gnu",
                    compression: "gzip") {
                tarfileset(dir: staging.webappDir,
                        prefix: "${archivePrefix}/labkeywebapp") {
                    exclude(name: "WEB-INF/classes/distribution")
                }
                tarfileset(dir: distExtension.modulesDir,
                        prefix: "${archivePrefix}/modules") {
                    include(name: "*.module")
                }
                tarfileset(dir: distExtension.extraSrcDir,
                        prefix: "${archivePrefix}/") {
                    include(name: "**/*")
                }
                tarfileset(dir: staging.tomcatLibDir, prefix: "${archivePrefix}/tomcat-lib") {
                    // this exclusion is necessary because for some reason when buildFromSource=false,
                    // the tomcat bootstrap jar is included in the staged libraries and the LabKey boostrap jar is not.
                    // Not sure why.
                    exclude(name: "bootstrap.jar")
                }

                if (includeMassSpecBinaries)
                {
                    tarfileset(dir: "${project.rootProject.projectDir}/external/windows/msinspect",
                            prefix: "${archivePrefix}/bin") {
                        include(name: "**/*.jar")
                        exclude(name: "**/.svn")
                    }
                }

                tarfileset(dir: staging.pipelineLibDir,
                        prefix: "${archivePrefix}/pipeline-lib") {
                }

                tarfileset(file: "${project.buildDir}/manual-upgrade.sh", prefix: archivePrefix, mode: 744)

                tarfileset(dir: distExtension.archiveDataDir,
                        prefix: archivePrefix) {
                    include(name: "README.txt")
                }
                tarfileset(dir: project.buildDir,
                        prefix: getArchivePrefix()) {
                    include(name: "VERSION")
                }
                tarfileset(dir: project.buildDir,
                        prefix: archivePrefix) {
                    include(name: "labkey.xml")
                }
            }
        }
        else
        {
            ant.tar(tarfile: getTarArchivePath(),
                    longfile: "gnu",
                    compression: "gzip") {
                tarfileset(dir: distExtension.modulesDir,
                        prefix: "${archivePrefix}/modules") {
                    include(name: "*.module")
                }
            }
        }

    }

    private void zipArchives()
    {
        String archivePrefix = this.getArchivePrefix()
        if (makeDistribution)
        {
            ant.zip(destfile: getZipArchivePath()) {
                zipfileset(dir: "${project.rootProject.buildDir}/staging/labkeyWebapp",
                        prefix: "${archivePrefix}/labkeywebapp") {
                    exclude(name: "WEB-INF/classes/distribution")
                }
                zipfileset(dir: distExtension.modulesDir,
                        prefix: "${archivePrefix}/modules") {
                    include(name: "*.module")
                }
                zipfileset(dir: distExtension.extraSrcDir,
                        prefix: "${archivePrefix}/") {
                    include(name: "**/*")
                }
                project.project(":server").configurations.tomcatJars.getFiles().collect({
                    tomcatJar ->
                        zipfileset(file: tomcatJar.path,
                                prefix: "${archivePrefix}/tomcat-lib")
                })
                zipfileset(dir: "${project.rootProject.buildDir}/staging/pipelineLib",
                        prefix: "${archivePrefix}/pipeline-lib")
                zipfileset(dir: "${project.rootProject.projectDir}/external/windows/core",
                        prefix: "${archivePrefix}/bin") {
                    include(name: "**/*")
                    exclude(name: "**/.svn")
                }

                if (includeMassSpecBinaries)
                {
                    zipfileset(dir: "${project.rootProject.projectDir}/external/windows/",
                            prefix: "${archivePrefix}/bin") {
                        exclude(name: "**/.svn")
                        include(name: "tpp/**/*")
                        include(name: "comet/**/*")
                        include(name: "msinspect/**/*")
                        include(name: "labkey/**/*")
                        include(name: "pwiz/**/*")
                    }
                }

                zipfileset(dir: distExtension.archiveDataDir,
                        prefix: "${archivePrefix}") {
                    include(name: "README.txt")
                }
                zipfileset(dir: "${project.buildDir}/",
                        prefix: "${archivePrefix}") {
                    include(name: "VERSION")
                }
                zipfileset(dir: "${project.buildDir}/",
                        prefix: "${archivePrefix}") {
                    include(name: "labkey.xml")
                }
            }
        }
        else
        {
            ant.zip(destfile: getZipArchivePath()) {
                zipfileset(dir: distExtension.modulesDir,
                        prefix: "${archivePrefix}/modules") {
                    include(name: "*.module")
                }
            }
        }

    }

    private void createDistributionFiles()
    {
        writeDistributionFile()
        writeVersionFile()
        // copy the manual-update script to the build directory so we can fix the line endings.
        project.copy({CopySpec copy ->
            copy.from(distExtension.archiveDataDir)
            copy.include "manual-upgrade.sh"
            copy.into project.buildDir
        })
        project.ant.fixcrlf (srcdir: project.buildDir, includes: "manual-upgrade.sh VERSION", eol: "unix")
    }


    private void writeDistributionFile()
    {
        File distExtraDir = new File(project.rootProject.buildDir, DistributionExtension.DIST_FILE_DIR)
        if (!distExtraDir.exists())
            distExtraDir.mkdirs()
        Files.write(Paths.get(distExtraDir.absolutePath, DistributionExtension.DIST_FILE_NAME), project.name.getBytes())
    }

    private void writeVersionFile()
    {
        Files.write(Paths.get(project.buildDir.absolutePath, DistributionExtension.VERSION_FILE_NAME), ((String) project.version).getBytes())
    }
}
