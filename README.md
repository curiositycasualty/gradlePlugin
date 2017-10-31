## gradlePlugin

The gradlePlugin jar is a jar file containing plugins, tasks, extensions and utilities used for building the [LabKey](https://www.labkey.org)
Server application and its modules.

If building your own LabKey module, you may choose to use these plugins or not.  They bring in a lot of functionality 
but also make certain assumptions that you may not want to impose on your module.  See the 
[LabKey documentation](https://www.labkey.org/Documentation/wiki-page.view?name=gradleModules) for more information.

## Release Notes

### version 1.0.1

*Released*: July 2, 2017
(Earliest compatible LabKey version: 17.2)

The first official release of the plugin to support Labkey 17.2 release.  

### version 1.1

*Released*: August 3, 2017
(Earliest compatible LabKey version: 17.2)

* [Issue 31046](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=31046) - Remove JSP jars from WEB-INF/jsp directory with undeployModule
* [Issue 31044](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=31044) - Exclude 
'out' directory generated by IntellijBuilds when finding input files for Antlr
* [Issue 30916](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=30916) - Prevent duplicate bootstrap 
jar files due to including branch names in version numbers

### version 1.2
*Released*: Sept 28, 2017
(Earliest compatible LabKey version: 17.2)

* [Issue 31186](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=31186) - createModule task
should not copy scripts and schema.xml when hasManagedSchema == false
* [Issue 31390](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=31390) - add `external/<os>` as 
an input directory for deployApp so it recognizes when new files are added and need to be deployed
* [Issue 30206](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=30206) - don't re-copy `labkey.xml`
if there have been no changes to database properties or context
* [Issue 31165](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=31165) - update naming of distribution
files
* [Issue 31477](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=31477) - add explicit task dependency
so jar file is included in client API Jar file
* [Issue 31490](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=31490) - remove jar file from modules-api
directory when doing clean task for module
* [Issue 31061](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=31061) - do not include jar files
 in module lib directories if already included in one of the base modules
* Improve cleaning for distribution tasks
* Make stageModules first delete the staging modules directory (to prevent picking up modules not in the current set) 
* Make cleanDeploy also cleanStaging
* Prevent creation of jar file if there is no src directory for a project
* Make sure jsp directory exists before trying to delete files from it
* remove npm_prune as a dependency on npmInstall
* add `cleanOut` task to remove the `out` directory created by IntelliJ builds
* collect R install logs into file
* enable passing database properties through TeamCity configuration
* add `showDiscrepancies` task to produce a report of all external jars that have multiple versions in the build

### version 1.2.1
*Released*: Oct 16, 2017
(Earliest compatible LabKey version: 17.2)

* [Issue 31742](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=31742) - Remove redundant npm_setup command for better performance
* [Issue 31778](https://www.labkey.org/home/Developer/issues/Secure/issues-details.view?issueId=31778) - Update jar and module naming for sprint branches
* [Issue 31165](https://www.labkey.org/home/Developer/issues/Secure/issues-details.view?issueId=31165) - Update naming convention for distribution files
* Update logic for finding source directory for compressClientLibs to use lastIndexOf "web" or "webapp" directory
* Exclude node_modules directory when checking for .lib.xml files for minor performance improvement

### version 1.2.2
*Released*: Nov ???, 2017
(Earliest compatible LabKey version: 17.2)

* FileModule plugin enforces unique names for LabKey modules
* [Issue 31985](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=31985) - bootstrap task not connecting to master database for dropping database
* Update npm run tasks to use isDevMode instead of separate property to determine which build task to run
