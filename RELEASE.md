## Release notes

### Release 1.16.0 (major features/fixes)

[@30466](https://swarm.workshop.perforce.com/changes/30466) - Merge pull request #209 from jenkinsci/skumar_jenkinsfilePath JENKINSFILE_PATH variable should be available outside of the pipeline tag.

[@30453](https://swarm.workshop.perforce.com/changes/30453) - Merge pull request #205 from stuartrowe:validate-check-catch-log. Expose quiet field to Validate.checkCatch() 

[@30451](https://swarm.workshop.perforce.com/changes/30451) - Merge pull request #206 from austin987:JENKINS-72335. The reconcile command should ignore the summary messages from the server for multiple paths in the reconcile command. 

[@30450](https://swarm.workshop.perforce.com/changes/30450) - Merge pull request #207 from hjain-perforce:master.Fixed a bug where the character "+" in the workspace name was being replaced with a space (" ").

[@30513](https://swarm.workshop.perforce.com/changes/30513) - Update P4Java Version to 2024.1.2612262.

### Release 1.15.1 (major features/fixes)

[@30143](https://swarm.workshop.perforce.com/changes/30143) - Merge pull request #[197] from [skumar7322/SwarmAPI] Changed version of Swarm API to v11.

[@30144](https://swarm.workshop.perforce.com/changes/30144) - Merge pull request #[184] from [lleroy/manual-spec-from-file-in-perforce-view] . Enhanced the functionality to read "Change View" mapping from a file.

[@30145](https://swarm.workshop.perforce.com/changes/30145) - Update P4Java Version to 2023.2.2553500


### Release 1.14.4 (major features/fixes)

[@29870](https://swarm.workshop.perforce.com/changes/29870) - Merge pull request #194 from kjs104901/bugfix. Fixed bug where client cant sync global libraries on Windows JENKINS-72098

[@29848](https://swarm.workshop.perforce.com/changes/29848), [@29868](https://swarm.workshop.perforce.com/changes/29868) - Merge pull request #195, #196 from jenkinsci/StreamAtChange. Add new feature to support stream at change for stream depot. JENKINS-70378

[@29829](https://swarm.workshop.perforce.com/changes/29829) - Fixed bug where JENKINSFILE_PATH is not available in pipeline when lightweight checkout and skipDefaultCheckout are set. JENKINS-39107

[@29869](https://swarm.workshop.perforce.com/changes/29869) - Handle NoSuchFileException to support forward compatibility with apache Commons IO 2.13.0


### Release 1.14.3 (major features/fixes)

[@29812](https://swarm.workshop.perforce.com/changes/29812) - Merge pull request #178 from joel-f-brown/master. Do not call "p4 counter" for integer counter name. JENKINS-70219

[@29794](https://swarm.workshop.perforce.com/changes/29794) - Merge pull request #191 from jenkinsci/PollingToLatest. Add new feature to detect change beyond pinned changelist and trigger the build. JENKINS-65246, JENKINS-63879


### Release 1.14.2 (major features/fixes)

[@29672](https://swarm.workshop.perforce.com/changes/29672) - Merge pull request #180 from skumar7322/UpdateP4JavaVersion.  Update p4Java version to: 2022.2.2444480

[@29668](https://swarm.workshop.perforce.com/changes/29668) - Update Json Version to 20230227


### Release 1.14.1 (major features/fixes)

[@29505](https://swarm.workshop.perforce.com/changes/29505) - Merge pull request #177 from skumar7322/UpdateP4Java221P2.  Update P4 Java Version to "2022.1.2423241"


### Release 1.14.0 (major features/fixes)

[@29476](https://swarm.workshop.perforce.com/changes/29476) - Merge pull request #174 from skumar7322/UpdateJenkins346.  Update Jenkins Version to 2.346.3

[@29472](https://swarm.workshop.perforce.com/changes/29472) - Merge pull request #173 from skumar7322/TemplateWorkspaceIssue.  Polling should not set client root to Null.  JENKINS-59300

[@29440](https://swarm.workshop.perforce.com/changes/29440) - Merge pull request #172 from skumar7322/ReconcileWithT.  Add '-t' flag to reconcile command.  JENKINS-59922

[@29411](https://swarm.workshop.perforce.com/changes/29411) - Merge pull request #171 from skumar7322/SecurityPatch.  Security Patch : Update Jenkins Version and Other Dependencies 


### Release 1.13.3 (major features/fixes)

[@29366](https://swarm.workshop.perforce.com/changes/29366) - Update p4java 2022.1.2390907.  Resolves file corruption for compressed text+C files over 64K.


### Release 1.13.2 (major features/fixes)

[@29321](https://swarm.workshop.perforce.com/changes/29321) - Merge pull request #165 from skumar7322/UnshelveIssue. Unable to unshelve change: java.lang.IllegalStateException: Expected 1 instance of hudson.model.User$AllUsers but got 0.  JENKINS-69172

[@29237](https://swarm.workshop.perforce.com/changes/29237) - Merge pull request #157 from joel-f-brown/master.  Do not quote the path when creating label.  JENKINS-67631

[@29213](https://swarm.workshop.perforce.com/changes/29213) - Merge pull request #159 from joel-f-brown/bugsView.  For ManualWorkspace views from a file, construct RHS if missing.  JENKINS-69491

[@29212](https://swarm.workshop.perforce.com/changes/29212) - Merge pull request #160 from pschlan/JENKINS-69808.  Cleanup client after publish, if enabled.  JENKINS-69808

[@29210](https://swarm.workshop.perforce.com/changes/29210) - [SECURITY] Use HTTPS to resolve dependencies in Maven Build.  Use HTTPS instead of HTTP to resolve deps CVE-2021-26291

[@29209](https://swarm.workshop.perforce.com/changes/29209) - Merge pull request #162 from skumar7322/master.  Update P4Java Version to 2022.1.2350821.  JENKINS-68551


### ~~Release 1.13.1 (major features/fixes)~~ (skip)

### Release 1.13.0 (major features/fixes)

[@28960](https://swarm.workshop.perforce.com/changes/28960) - Upgrade p4java 2021.2.2314547

[@28892](https://swarm.workshop.perforce.com/changes/28892) - Update P4Java 2021.2.2293546.  Fixed parallel sync authentication issue on case-insensitive servers. JENKINS-48525 JENKINS-68104.

[@28891](https://swarm.workshop.perforce.com/changes/28891) - Merge pull request #149 from skumar7322/JENKINS_38781_UnshelveIssue.  JENKINS-38781 : p4 unshelve not updating

[@28865](https://swarm.workshop.perforce.com/changes/28865) - Merge pull request #146 from skumar7322/dependencyUpdate.  Update Jenkins version to 2.138.4

[@28806](https://swarm.workshop.perforce.com/changes/28806) - Merge pull request #144 from VirtualTim/patch-1.  Change `status` to be a choice instead of a string

[@28805](https://swarm.workshop.perforce.com/changes/28805) - Merge pull request #145 from joel-f-brown/master.  Allow option to not detect changelists for build, JENKINS-68516

[@28786](https://swarm.workshop.perforce.com/changes/28786) - Merge pull request #133 from nephre/force_sync_when_p4cleanworkspace.  Force sync and populate have list on P4_CLEANWORKSPACE

[@28785](https://swarm.workshop.perforce.com/changes/28785) - Merge pull request #135 from redsil/patch-1.  Fix bug where exclude paths are turned into include paths

[@28784](https://swarm.workshop.perforce.com/changes/28784) - Merge pull request #140 from jorgenpt/fix-file-limit-not-persisting. Persist `fileLimit` when serializing a changeset


### Release 1.12.4 (major features/fixes)

[@28770](https://swarm.workshop.perforce.com/changes/28770) - Check Item is PerforceScm.DescriptorImpl onChange event. JENKINS-68378


### Release 1.12.3 (major features/fixes)

[@28755](https://swarm.workshop.perforce.com/changes/28755) - Updating P4Java to 2021.2.2278127 JENKINS-67631 JENKINS-68006 JENKINS-67936 JENKINS-64739 JENKINS-54500 JENKINS-27877

[@28735](https://swarm.workshop.perforce.com/changes/28735) - Merge pull request #142 from skumar7322/branchIndexing. Branch indexing occurring for every multibranch job JENKINS-64946

[@28728](https://swarm.workshop.perforce.com/changes/28728) - Merge pull request #141 from joel-f-brown/master. User session cache logging level changed to finer/finest JENKINS-68006


### Release 1.12.2 (major features/fixes)

[@28555](https://swarm.workshop.perforce.com/changes/28555) - Update p4java patch version 2021.2.2240592


### Release 1.12.1 (major features/fixes)

[@28528](https://swarm.workshop.perforce.com/changes/28528) - Merge pull request #136 from NotMyFault/chore/master/prep-for-icon-removal-from-core Preparation for core removing sunset icons: https://github.com/jenkinsci/jenkins/pull/5778/

[@28526](https://swarm.workshop.perforce.com/changes/28526) - Merge pull request #137 from joel-f-brown/master If Credential ID exists in session cache, set the connection's Ticket

[@28525](https://swarm.workshop.perforce.com/changes/28525) - Use ConcurrentHashMap for thread safety.


### Release 1.12.0 (major features/fixes)

[@28510](https://swarm.workshop.perforce.com/changes/28510) - Update P4Java 2021.2.2234376

[@28508](https://swarm.workshop.perforce.com/changes/28508) - Cache P4TICKET in session. Session cache now keyed on Credential ID and stores: User, Expiry time and Ticket. JENKINS-60141


### Release 1.11.6 (major features/fixes)

[@28022](https://swarm.workshop.perforce.com/changes/28022) - Merge pull request #134 from stuartrowe/JENKINS-66648  [JENKINS-66648] Replace 'now' label with the P4ChangeRef for the current head

[@28021](https://swarm.workshop.perforce.com/changes/28021) - Alternative cleanup option. In some situations (Windows) we need to individually set the file to be writable and then delete. JENKINS-66089

[@28018](https://swarm.workshop.perforce.com/changes/28018) - Workspace formatting for Groovy tasks. Allow the replacement of "@" in the Workspace root when running a Groovy Task.

[@28015](https://swarm.workshop.perforce.com/changes/28015) - MultiBranch exclude filter. A regular expression exclude filter for Swarm branches, Stream names and Branch paths. JENKINS-58346 JENKINS-63625

[@28012](https://swarm.workshop.perforce.com/changes/28012) - Update P4Java to 2021.1.2163843

[@28009](https://swarm.workshop.perforce.com/changes/28009) - Merge pull request #130 from lleroy/manual-spec-from-file-in-perforce Implemented support for "Manual Spec" stored in a file in Perforce

[@28008](https://swarm.workshop.perforce.com/changes/28008) - Merge pull request #129 from nephre/polling_per_change Add support for controlling polling per change filter behavior with build parameter.   JENKINS-66169

[@28002](https://swarm.workshop.perforce.com/changes/28002) - Merge pull request #131 from jorgenpt/support-windows-linebreaks Add support for Windows linebreaks in filters

[@27924](https://swarm.workshop.perforce.com/changes/27924) - Merge pull request #128 from bsmoyers/poll-at-counter allow polling scm when populate options in job are set to pin to counter name in addition to changelist or label

[@27857](https://swarm.workshop.perforce.com/changes/27857) - Throw AbortException if Task fails. JENKINS-51562

[@27770](https://swarm.workshop.perforce.com/changes/27770) - Update Swarm API to v9


### Release 1.11.5 (major features/fixes)

[@27768](https://swarm.workshop.perforce.com/changes/27768) - [SECURITY-2327]


### Release 1.11.4 (major features/fixes)

[@27675](https://swarm.workshop.perforce.com/changes/27675) - Substitute missing Axes for Key Name. Added useKey option to substitute empty keys in client views, depot paths and stream paths.  JENKINS-55985


### Release 1.11.3 (major features/fixes)

[@27492](https://swarm.workshop.perforce.com/changes/27492) - Add new Filter to build with the latest change found during polling.  JENKINS-63879

[@27442](https://swarm.workshop.perforce.com/changes/27442) - Added global - hide Server messages option. JENKINS-59750

[@27240](https://swarm.workshop.perforce.com/changes/27240) - Merge pull request #95 from lystor/bug-56248. JENKINS-56248: P4Plugin Polls Workspaces With Disabled Polling (poll: false)


### Release 1.11.2 (major features/fixes)

[@27175](https://swarm.workshop.perforce.com/changes/27175) - Prevent dormant multi branch project continiously building.    The new behaviour uses the last build change if no changes are found (preventing a triggered build) or if there was no previous builds (in the case of new projects) then the latest changes is used to triggering a build. JENKINS-63494 and JENKINS-64193.

[@26955](https://swarm.workshop.perforce.com/changes/26955) - Merge pull request #125 from dvilaverde/master Add ContributorMetadataAction to SwarmScmSource to enable CHANGE_AUTHOR env variable in build

[@26938](https://swarm.workshop.perforce.com/changes/26938) - Merge pull request #122 from dvilaverde/master Have P4ChangeRequestSCMHead implement interface ChangeRequestSCMHead2

[@26930](https://swarm.workshop.perforce.com/changes/26930) - Merge pull request #124 from dvilaverde/case-sensitive SwarmSCMSource#getHeads uses swarm id instead of name for P4SCMHead


### Release 1.11.1 (major features/fixes)

[@26860](https://swarm.workshop.perforce.com/changes/26860) - Global option to include changes since last successful build.  JENKINS-64030

[@26820](https://swarm.workshop.perforce.com/changes/26820) - Merge pull request #121 from KasperFranz/patch-1  Use Github as source of documentation

[@26766](https://swarm.workshop.perforce.com/changes/26766) - Merge pull request #120 from VirtualTim/patch-1  Remove background colour for change list

[@26673](https://swarm.workshop.perforce.com/changes/26673) - Code cleanup.  Deprecated classes like AbstractStepImpl, AbstractStepDescriptorImpl and AbstractSynchronousStepExecution for P4TaggingStep


### Release 1.11.0 (major features/fixes)

#### Important Changes
 * P4Java now defaults to TLSv1.2 for SSL connections (previously TLSv1) to use an older version please refer to our [KB article](https://community.perforce.com/s/article/2620).
 * P4Java now implements P4IGNORE file directory traversal and ellipses wildcard support.
 * Use of TempClients in MultiBranch to include Virtual streams in branch scan.

[@26666](https://swarm.workshop.perforce.com/changes/26666) - Update P4Java to latest patch 2020.1.1999383.  Fixes for P4IGNORE, reconcile flags and performance improvements for ExtendedFileSpec operations.  JENKINS-59922 JENKINS-39094 JENKINS-51192

[@26640](https://swarm.workshop.perforce.com/changes/26640) - Tick interval message to prevent timeout.  An advanced setting in the Credential sets a 'tick' interval to print out a 'waiting' message to the log during long running commands.  The value needs to be less than 30,000ms to avoid Jenkins automatic disconnect. JENKINS-58161

[@26617](https://swarm.workshop.perforce.com/changes/26617) - Prevent `ssl:` string from appearing in the P4PORT Jelly field.  Jelly uses getP4port() method for binding the fields; adding the ssl prefix was causing issues. JENKINS-63246

[@26603](https://swarm.workshop.perforce.com/changes/26603) - Merge pull request #119 from joel-f-brown/master Add virtual stream detection for MultiBranch pipeline.  JENKINS-62699

[@26602](https://swarm.workshop.perforce.com/changes/26602) - Reuse temp client connections for the multi-branch scan.  TempClientHelper is set in retrieve and updated for different heads.


### Release 1.10.14 (major features/fixes)

[@26593](https://swarm.workshop.perforce.com/changes/26593) - P4Trigger support for Folder based credentials.  Avoid deprecated findCredential method and use the 'job' as the Item.  JENKINS-62811


### Release 1.10.13 (major features/fixes)

[@26587](https://swarm.workshop.perforce.com/changes/26587) - Use default P4IGNORE if unset.  Check if P4Java's environment has set P4IGNORE for the server connection, otherwise use OS defaults.  JENKINS-59726 JENKINS-54707

[@26585](https://swarm.workshop.perforce.com/changes/26585) - Use a TempClientHelper to map the P4Path and then check for changes.  JENKINS-62259

[@26581](https://swarm.workshop.perforce.com/changes/26581) - Return "ssl:" on P4PORT for SSL connections.  Use 'contains' check on trigger checks for backwards compatibility.  JENKINS-62253

[@26579](https://swarm.workshop.perforce.com/changes/26579) - Prevent logging if Populate quiet flag is set.  Originally the Populate quiet flag was passed as a '-q' flag to the command, but some commands using callback streaming handlers still reported output.  JENKINS-59750

[@26423](https://swarm.workshop.perforce.com/changes/26423) - Add range limit for MultiBranch head revision queries.  JENKINS-61745


### Release 1.10.12 (major features/fixes)

[@26364](https://swarm.workshop.perforce.com/changes/26364) - Merge pull request #118 from williambrode/InitCauseAbortExecption.  Initialize the cause of AbortException

[@26363](https://swarm.workshop.perforce.com/changes/26363) - Merge pull request #117 from mlynnyk/tagAction-issue.  TagAction should not be written to config.xml

[@26359](https://swarm.workshop.perforce.com/changes/26359) - Removed "isSsl()" method from P4Credentials due to it causing an issue with JCasC plugin.  JENKINS-58249

[@26312](https://swarm.workshop.perforce.com/changes/26312) - Added a getSsl method into the Perforce credential classes to enable support for the Configuration as code plugin.  JENKINS-58249

[@26356](https://swarm.workshop.perforce.com/changes/26356) - Reverting job list to original pre 1.10.10 release.  JENKINS-61156


### Release 1.10.11 (major features/fixes)

[@26352](https://swarm.workshop.perforce.com/changes/26352) - [SECURITY-1765]


### Release 1.10.10 (major features/fixes)

[@26294](https://swarm.workshop.perforce.com/changes/26294) - Merge pull request #116 from ajxb/ajb-trigger.  Get list of jobs immediately prior to probing

[@26263](https://swarm.workshop.perforce.com/changes/26263) - Merge pull request #115 from nwillems/viewmaphelper-test.  Allow spacing around viewmappings


### Release 1.10.9 (major features/fixes)

[@26211](https://swarm.workshop.perforce.com/changes/26211) - Merge pull request #113 from jenkinsci/msmeeth-master EXECUTOR_NUMBER is always expanded to '0' in workspace name when checking out with Perforce to a subdirectory  JENKINS-59484

[@26210](https://swarm.workshop.perforce.com/changes/26210) - Merge pull request #93 from lystor/bug-56290 Impossible to Add Streams with Exact Names to Multibranch Pipeline  JENKINS-56290

[@26208](https://swarm.workshop.perforce.com/changes/26208) - Merge pull request #112 from p4charu/jenkinsci-master Refactored to reduce number of calls to login -s  JENKINS-60141

[@26206](https://swarm.workshop.perforce.com/changes/26206) - Making Polling ignore "default jenkinsfile" when "Pipeline: Multibranch with defaults" plugin is installed and instead use the word "jenkinsfile" as the jenkinsfile location.  JENKINS-60321

[@26204](https://swarm.workshop.perforce.com/changes/26204) - Merge pull request #111 from p4charu/jenkinsci-master Check if the template ws exists when deleting jenkins project  JENKINS-60144


### ~~Release 1.10.8 (major features/fixes)~~ (skip)

### Release 1.10.7 (major features/fixes)

[@26186](https://swarm.workshop.perforce.com/changes/26186) - Update p4java 2019.1.1889202

[@26163](https://swarm.workshop.perforce.com/changes/26163) - Pre-load build environment. buildEnvironment() is called by Jenkins and tagAction may not be set - so use old pre-load behaviour.  JENKINS-60074


### Release 1.10.6 (major features/fixes)

[@26143](https://swarm.workshop.perforce.com/changes/26143) - Update p4java 2019.1.1873579 for Symlink and UTF8-BOM support.  JENKINS-59213 JENKINS-59611 

[@26142](https://swarm.workshop.perforce.com/changes/26142) - Enable Advanced Publish paths for Submits.  Publish path filter for reconcile JENKINS-56501

[@26122](https://swarm.workshop.perforce.com/changes/26122) - Added additional login to resolve parallel connection issue. Includes test case for parallel stages.  JENKINS-52806

[@26121](https://swarm.workshop.perforce.com/changes/26121) - Merge pull request #104 from williambrode/JENKINS-52806 Fix for p4 environment variables being wrong in parallel pipeline syncs.


### Release 1.10.5 (major features/fixes)

[@26112](https://swarm.workshop.perforce.com/changes/26112) - Merge pull request #110 from p4charu/jenkinsci-master.  If last build is not completely built look for last build in progress.  JENKINS-58639


### Release 1.10.4 (major features/fixes)

[@26099](https://swarm.workshop.perforce.com/changes/26099) - Prevent infinite polling when last build and current are pinned to a label.  Added test to verify support for static lables. JENKINS-58149

[@26096](https://swarm.workshop.perforce.com/changes/26096) - Merge pull request #108 from joel-f-brown/master.  Credentials handling for Folders when using P4Groovy.  Use run to determine the credentials in GetP4Task, then pass the P4BaseCredentials instead of the credentials ID.  Now the P4Groovy getConnection() method uses the P4BaseCredentials instead of looking up the credentials from the active Jenkins instance.  JENKINS-58745 JENKINS-57314

[@26095](https://swarm.workshop.perforce.com/changes/26095) - Merge pull request #109 from daniel-beck-bot/https-urls-pom.  Use HTTPS URLs in pom.xml

[@26094](https://swarm.workshop.perforce.com/changes/26094) - Merge pull request #105 from jenkinsci/publishFilter.  Publish path filter for reconcile JENKINS-56501

[@25909](https://swarm.workshop.perforce.com/changes/25909) - Merge pull request #103 from williambrode/JENKINS-58686.  Don't query submitted changes when syncing to CL 0.  JENKINS-58686


### Release 1.10.3 (major features/fixes)

[@25850](https://swarm.workshop.perforce.com/changes/25850) - Merge pull request #101 from williambrode/master.  Lower print out for null changelog to logger.fine()

[@25849](https://swarm.workshop.perforce.com/changes/25849) - Merge pull request #102 from stuartrowe/JENKINS-58504.  Only set the script path for pipeline script checkout.  JENKINS-58504

[@25844](https://swarm.workshop.perforce.com/changes/25844) - Change Swarm Reviews so that they are only identified by review number in the multi branch (for better rendering in BlueOcean).

[@25843](https://swarm.workshop.perforce.com/changes/25843) - MultiBranch support for Swarm path Exclusions in the project configuration.

[@25826](https://swarm.workshop.perforce.com/changes/25826) - Global option to limit [@query.  By default 'Head change query limit' is set to 1000 (0 is no limit) and the plugin will limit the query based on the workspace view.  For large servers set the value to a range based](https://swarm.workshop.perforce.com/changes/query.  By default 'Head change query limit' is set to 1000 (0 is no limit) and the plugin will limit the query based on the workspace view.  For large servers set the value to a range based)


### Release 1.10.2 (major features/fixes)

[@25801](https://swarm.workshop.perforce.com/changes/25801) - Update p4java to 2019.1.1827134.  Fix various symlink issue. JENKINS-58109 JENKINS-58048 JENKINS-57945 JENKINS-57955


### Release 1.10.1 (major features/fixes)

[@25759](https://swarm.workshop.perforce.com/changes/25759) - Use iclient.reconcileFiles over old execMap method. Added extra logging to reconcile.

[@25758](https://swarm.workshop.perforce.com/changes/25758) - P4Java update 2019.1.1822775  JENKINS-58109 JENKINS-58048 JENKINS-57945 JENKINS-57955

    #1822014 (Bug #099164) Support Ditto '&' mappings in client view. 
    #1821933 (Bug #099149) Resolve symlink detection issue when using reconcile. 
    #1815000 (Bug #099165) Support reconcile streaming functionality

[@25737](https://swarm.workshop.perforce.com/changes/25737) - Trim white space for Manual Workspace Views.

[@25736](https://swarm.workshop.perforce.com/changes/25736) - Cleanup step - use checkbox state and ignore Global option.  The global options for deleting the client is ignored and the cleanup checkbox state is used.  JENKINS-47107

[@25689](https://swarm.workshop.perforce.com/changes/25689) - Update SpecFile usage docs.  JENKINS-52679

[@25688](https://swarm.workshop.perforce.com/changes/25688) - Raise errors for execMap "client -i" in SpecWorkspaceImpl.  JENKINS-52679

[@25668](https://swarm.workshop.perforce.com/changes/25668) - Merge pull request #94 from lystor/bug-56414.  JENKINS-56414: Support of Reconcile with Modtime in P4Publish


### Release 1.10.0 (major features/fixes)

[@25655](https://swarm.workshop.perforce.com/changes/25655) - Extract the Job Environment when fetching the Jenkinsfile. Allow Parameterized builds for the Stream field when using 'Pipline Script from SCM'. JENKINS-56775

[@25651](https://swarm.workshop.perforce.com/changes/25651) - Merge pull request #100 from stuartrowe/JENKINS-57534.  Limit buildChange to the next highest change number within the client's view. JENKINS-57534

[@25648](https://swarm.workshop.perforce.com/changes/25648) - BlueOcean: Create MultiBranch Pipelines.  Endpoints for Perforce credentials and Swarm Projects query.  JENKINS-52396 

[@25632](https://swarm.workshop.perforce.com/changes/25632) - Update P4Java 2019.1.1802476

[@25610](https://swarm.workshop.perforce.com/changes/25610) - Merge pull request #98 from stuartrowe/JENKINS-57358.  Add null check before using P4Path object returned by method SwarmScmSource#getPathsInBranch.  JENKINS-57358

[@25570](https://swarm.workshop.perforce.com/changes/25570) - Merge pull request #92 from p4charu/jenkinsci-master.  Updated help text for Jenkinsfile path.  JENKINS-39107 

[@25564](https://swarm.workshop.perforce.com/changes/25564) - Merge branch 'local_map'.  MultiBranch support for 'HelixBranches' local and remote Mappings.  Allows MultiBranches to map Depot paths external to the MultiBranch projects root.  

---

_For example, a MultiBranch project with the following Jenkinsfiles:_

    ProjA >> main
      //depot/projA/main/Jenkinsfile
      //depot/projA/main/src/...

    ProjA>> dev
      //depot/projA/dev/Jenkinsfile
      //depot/projA/dev/src/...

_An 'Includes' path for Branch Sources set to:  `//depot/projA/...`  Jenkins will now create two branches 'main' and 'dev'.  The default Mapping is '...' so the 'src/...' and any other files under the branch root will appear in the project's workspace.  To import an extenal location we can add another line to the mapping:_

    ...
    //depot/external/stuff/...

_Now both branches 'main' and 'dev' will have a folder 'depot/external/stuff' with the mapped files.  We can also use the `BRANCH_NAME` environment variable in the remote path uses the branch name._

    ...
    //depot/external/${BRANCH_NAME}/... 

_and in 'main' you will now see the remote files in ' depot/external/main' along with the local project files mapped with '...'_

---

[@25553](https://swarm.workshop.perforce.com/changes/25553) - Merge pull request #97 from stuartrowe/JENKINS-57179.  Post a comment after voting if description field is not empty.  JENKINS-57179

[@25530](https://swarm.workshop.perforce.com/changes/25530) - Merge pull request #96 from stuartrowe/JENKINS-57095.  Use StringUtils.containsIgnoreCase instead of String#contains so the ignore list parameter for Validate#check is case insensitive.  JENKINS-57095


### Release 1.9.7 (major features/fixes)

[@25439](https://swarm.workshop.perforce.com/changes/25439) - Skip polling if Job is in WaitingItem Queue.  JENKINS-56037 JENKINS-56286

[@25437](https://swarm.workshop.perforce.com/changes/25437) - Skip build if polling finds no/null change.  JENKINS-55075

[@25123](https://swarm.workshop.perforce.com/changes/25123) - Added Pipeline Job support and tests for declarative and scripted.  JENKINS-39107

[@25115](https://swarm.workshop.perforce.com/changes/25115) - Merge pull request #91 from p4charu/jenkinsci-master.  Fixed JENKINS-39107 and JENKINS-55826

[@25057](https://swarm.workshop.perforce.com/changes/25057) - Merge pull request #90 from jlloyola/master.  Changed P4Ticket web field type to password.

[@25056](https://swarm.workshop.perforce.com/changes/25056) - Check for duplicate syncIDs and test polling after failed build.  JENKINS-55075


### Release 1.9.6 (major features/fixes)

[@25019](https://swarm.workshop.perforce.com/changes/25019) - Merge pull request #89 from p4charu/jenkinsci-master.  JENKINS-49924: Added more help text for absolute clarity

[@25018](https://swarm.workshop.perforce.com/changes/25018) - Merge pull request #88 from p4charu/jenkinsci-master.  Fixed JENKINS-55430 and better credential error reporting

[@25015](https://swarm.workshop.perforce.com/changes/25015) - Enable/Disable MultiBranch Scan per change using Polling Filter.

[@25014](https://swarm.workshop.perforce.com/changes/25014) - Scan per change support for MultiBranch with remote Jenkinsfiles.  Only the 'Polling per Change' Filter is supported; need to disable or support the other filter types.  JENKINS-53936

[@25006](https://swarm.workshop.perforce.com/changes/25006) - Support for remote Jenkinsfiles in a MultiBranch project.  Jenkinsfile are idenitified by the Include branches path (e.g.  //depot/Remote/...), but you must add a Mapping in the BranchSource->Advanced->View Mappings to locate the projects source and use a special ${BRANCH_NAME} in the path. (e.g. //depot/ProjectA/${BRANCH_NAME}/...) JENKINS-53936
                                                                                                                         
[@25001](https://swarm.workshop.perforce.com/changes/25001) - Strip undefined variables.  JENKINS-53723

[@24987](https://swarm.workshop.perforce.com/changes/24987) - Diff client object before save to reduce unnecessary spec changes.  JENKINS-52590

[@24986](https://swarm.workshop.perforce.com/changes/24986) - Merge pull request #87 from p4charu/jenkinsci-master.  Test to confirm JENKINS-54628 is fixed.

[@24968](https://swarm.workshop.perforce.com/changes/24968) - Merge pull request #85 from DrakkenWulf/master.  Fix ability to do Replay in Pipelines.  JENKINS-55107


### Release 1.9.5 (major features/fixes)

[@24944](https://swarm.workshop.perforce.com/changes/24944) - Merge pull request #86 from p4charu/jenkinsci-master.  JENKINS-51632 and minor code cleanup

[@24942](https://swarm.workshop.perforce.com/changes/24942) - Deep clone of Workspace objects.  WorkspaceSpec was not cloned and caused the View to change.  JENKINS-54695

[@24940](https://swarm.workshop.perforce.com/changes/24940) - Skip sync when PreviewSync is used with quiet option `p4 sync -q -n`.  JENKINS-54153

[@24939](https://swarm.workshop.perforce.com/changes/24939) - Escape unsupported XML low ascii characters with a '?'.  JENKINS-52661 JENKINS-54841

[@24938](https://swarm.workshop.perforce.com/changes/24938) - Prevent XML parse errors from failing the build.  Jelly page is called while the changelist is being built.  Log the error and return an empty list of changes.  JENKINS-53030

[@24937](https://swarm.workshop.perforce.com/changes/24937) - Merge pull request #84 from p4charu/jenkinsci-master.  Added help text for Include and Fixed incorrect images. JENKINS-54584

[@24850](https://swarm.workshop.perforce.com/changes/24850) - Merge pull request #83 from rudolfwalter/patch-1.  Avoid unnecessary call to TagAction.getTicket()


### Release 1.9.4 (major features/fixes)

[@24842](https://swarm.workshop.perforce.com/changes/24842) - Add logging for Client View field on Workspace update.

[@24841](https://swarm.workshop.perforce.com/changes/24841) - Merge pull request #81 from p4charu/jenkinsci-master.  Hide Helix Library from Add Source option in MultiBranch.

[@24830](https://swarm.workshop.perforce.com/changes/24830) - Preserve Pipeline client when using Lightweight checkout.  Clone Workspace before creating Temp Client. JENKINS-54488

[@24829](https://swarm.workshop.perforce.com/changes/24829) - Support Jenkinsfiles in sub directories.  Preserve Jenkinsfile path in Client View. JENKINS-54382

[@24826](https://swarm.workshop.perforce.com/changes/24826) - Update Perforce Server Unicode check.


### Release 1.9.3 (major features/fixes)

[@24805](https://swarm.workshop.perforce.com/changes/24805) - Tidy up connection methods.  Cache TagAction calls to getTicket() when adding P4_TICKET to the Environment.  JENKINS-54222

[@24792](https://swarm.workshop.perforce.com/changes/24792) - Cleanup Global workspace used during polling.  The temporary workspace is recreated by the polling event from details stored in the previous build's metadata.  JENKINS-50975

[@24791](https://swarm.workshop.perforce.com/changes/24791) - Extra debug logging for Perforce commands; issue and complete step.  Log level FINEST.  JENKINS-54222

[@24780](https://swarm.workshop.perforce.com/changes/24780) - Merge pull request #80 from p4charu/master.  Fix and a test for concurrent global libraries using perforce workspaces. JENKINS-50975

[@24778](https://swarm.workshop.perforce.com/changes/24778) - Remove unused commons-compress dependency.  Resolve CVE-2018-11771 threat warning.

[@24777](https://swarm.workshop.perforce.com/changes/24777) - Global Library to use UUID for client name and delete after use.  Add a new 'Delete the Perforce Client after checkout' option (for Manual clients) which normally is false, but set to true for Global Library checkouts.  JENKINS-50975

[@24767](https://swarm.workshop.perforce.com/changes/24767) - Global Library support for multiple instances.  Enforce a depot syntax path ending of "/...".  Remove 'beta' from Global Library configuration.  Generate a unique workspace to sync multiple Libraries. JENKINS-53922

[@24698](https://swarm.workshop.perforce.com/changes/24698) - Fix for the revision number used by Swarm and other browsers.


### Release 1.9.2 (major features/fixes)

[@24671](https://swarm.workshop.perforce.com/changes/24671) - Added backwards support for old XML data.  Class rename in 1.9.0 would prevent processing of old build data.  JENKINS-53870


### Release 1.9.1 (major features/fixes)

[@24662](https://swarm.workshop.perforce.com/changes/24662) - Undo *SCMSource rename to original *ScmSource.  Resolve configuration loading issue due to 1.9.0 bad release.


### ~~Release 1.9.0 (major features/fixes)~~ (skip)

**Warning:  MultiBranch projects may require re-configuration; please backup settings prior to upgrade.**

[@24622](https://swarm.workshop.perforce.com/changes/24622) - Added 'command-launcher' for Jenkins 2.89.1 release and greater.

[@24591](https://swarm.workshop.perforce.com/changes/24591) - Merge pull request #79 from kroutley/disablePollingBypass.  JENKINS-53224 (Disable polling bypass)

[@24590](https://swarm.workshop.perforce.com/changes/24590) - Test case: polling during a non concurrent build.  Verify that the build inprogress does not get seen as a new polling event.  JENKINS-53224

[@24575](https://swarm.workshop.perforce.com/changes/24575) - Merge pull request #78 from kroutley/p4affectedfile.  JENKINS-53222 (Add all FileAction enum types to P4AffectedFile)

[@24574](https://swarm.workshop.perforce.com/changes/24574) - Merge pull request #77 from kroutley/master.  JENKINS-53221 (Add P4_ROOT to environment variables)

[@24571](https://swarm.workshop.perforce.com/changes/24571) - Support for Swarm pre-commit review events.  Swarm pre-commit review event creates a new 'Reviews' Tag in the MultiBranch and builds with specified change and shelf.

[@24536](https://swarm.workshop.perforce.com/changes/24536) - Improved Swarm SCMRevision builder.  Started to add Swarm Review Event support and exteded test case.  JENKINS-52066

[@24529](https://swarm.workshop.perforce.com/changes/24529) - Swarm Commit Event support, refactoring and test.  JENKINS-52605 (Fix doc in 'Includes' help bubble for MultiBranch).  JENKINS-52066 (Improve Swarm Commit and Branch Event support)

[@24504](https://swarm.workshop.perforce.com/changes/24504) - Tree walker for BranchSCMSource Events.  On a change-submit event use the change number and grab a submitted file. Walk up the path looking for a Jenkinsfile, then derive 'Project' path, 'Branch' name and 'Path' for the SCMHead/Revision.  This assumes that the Jenkinsfile is in the route of your projects.  Alternatively use the Swarm Event and pass your own 'Project', 'Branch' and 'Path'.

[@24501](https://swarm.workshop.perforce.com/changes/24501) - Refactor to use P4SCMXxx naming convention.

[@24497](https://swarm.workshop.perforce.com/changes/24497) - Force use of revision for Head.  Update P4Head->P4Path revision with P4Revision to avoid builds on unbounded 'latest'.

[@24492](https://swarm.workshop.perforce.com/changes/24492) - Initial work for MultiBranch Event trigger.  JENKINS-52066 (Triggered Events and not Polling per change)

[@24487](https://swarm.workshop.perforce.com/changes/24487) - Perforce Connection Refactor using try with resources for all IOptionsServer objects.

[@24484](https://swarm.workshop.perforce.com/changes/24484) - Trim white space from shelve ID.  JENKINS-52970


### Release 1.8.15 (major features/fixes)

[@24473](https://swarm.workshop.perforce.com/changes/24473) - Enable ChangeView and Client Backup features.

[@24472](https://swarm.workshop.perforce.com/changes/24472) - Add a min version check for each Client Type.  JENKINS-51048

[@24458](https://swarm.workshop.perforce.com/changes/24458) - Verify new View Mapping code supports DepotSource Excludes.  JENKINS-52861

[@24455](https://swarm.workshop.perforce.com/changes/24455) - Allow query of single streams by appending a `*` to a streams path.  https://github.com/p4paul/p4-jenkins/issues/36

[@24454](https://swarm.workshop.perforce.com/changes/24454) - Exclude and Include mapping support.  Refactored P4Path usage and added P4SwarmPath.  JENKINS-49804

[@24439](https://swarm.workshop.perforce.com/changes/24439) - Move ViewMapHelper to shared class.  JENKINS-52852

[@24433](https://swarm.workshop.perforce.com/changes/24433) - Extra arguments support for P4Groovy save function.  JENKINS-52782


### Release 1.8.14 (major features/fixes)

[@24409](https://swarm.workshop.perforce.com/changes/24409) - Added ChangeView and Client Backup support.

[@24406](https://swarm.workshop.perforce.com/changes/24406) - Cleanup client setup.  Should reduce 'null' root and views and work with replica Perforce Servers.

[@24404](https://swarm.workshop.perforce.com/changes/24404) - Support spaces in depot path for MultiBranch and DepotSource.  Includes filters for leading white space (spaces/tabs).  JENKINS-52652

[@24403](https://swarm.workshop.perforce.com/changes/24403) - Support spaces in depot path for MultiBranch and DepotSource.  JENKINS-52604

[@24402](https://swarm.workshop.perforce.com/changes/24402) - Fix Ellipsis detection in ClientViewMappingGenerator.  'p4sync' was mistaking a single '.' for '...' when building the view.  JENKINS-52572

[@24401](https://swarm.workshop.perforce.com/changes/24401) - Fix 'p4sync' auto complete and validate checks.


### ~~Release 1.8.13~~ (broken deployment)


### Release 1.8.12 (major features/fixes)

[@24359](https://swarm.workshop.perforce.com/changes/24359) - Support ServerID on client for Edge type serves.

[@24267](https://swarm.workshop.perforce.com/changes/24267) - Use JOB_NAME in the Global Library Workspace name.  Global Library workspaces must be unique.  JENKINS-49525 JENKINS-50975

[@24266](https://swarm.workshop.perforce.com/changes/24266) - Enable Windows tests.

[@24233](https://swarm.workshop.perforce.com/changes/24233) - Merge pull request #74 from msmeeth56/master.  Updated tests to work against Windows, and lower spec computers.  JENKINS-52145

[@24174](https://swarm.workshop.perforce.com/changes/24174) - Deprecated Helix 'Build Farm' servers.


### ~~Release 1.8.11~~ (broken deployment)


### Release 1.8.10 (major features/fixes)

[@24029](https://swarm.workshop.perforce.com/changes/24029) - CRUMB entry no longer required for the Perforce curl script trigger.  Merge pull request #71 from AllegorithmicSAS/CrumbExclusion.  Add CrumbExclusion for /p4/change

[@23943](https://swarm.workshop.perforce.com/changes/23943) - Poll per change for Pipeline.  JENKINS-47427

[@23941](https://swarm.workshop.perforce.com/changes/23941) - Track Credentials at build time.  Unable to track when the Job is configured as Pipeline Jobs may use one or more credentials in the Jenkinsfile.  JENKINS-44706

[@23935](https://swarm.workshop.perforce.com/changes/23935) - Store Perforce Ticket as a Secret, but support old clear text ticket credentials.  JENKINS-49474


### Release 1.8.9 (major features/fixes)

[@23858](https://swarm.workshop.perforce.com/changes/23858) - Skip probing disabled Jobs.  Includes minor updates to Polling tests. JENKINS-50634

[@23850](https://swarm.workshop.perforce.com/changes/23850) - Change Java Pattern polling filter name.  The @Symbol name for the Java Pattern filter clashed with the View Mask filter.  JENKINS-50027

[@23821](https://swarm.workshop.perforce.com/changes/23821) - Raise Submit errors in Publish step.  JENKINS-49825


### Release 1.8.8 (major features/fixes)

[@23806](https://swarm.workshop.perforce.com/changes/23806) - Update P4Java 2018.1.1638495; digest calculation for UTF8 files. JENKINS-49955

[@23796](https://swarm.workshop.perforce.com/changes/23796) - Look for the expanded original name when updating the view.  Added test case for Job names with a '/' restricted character.  JENKINS-50393

[@23780](https://swarm.workshop.perforce.com/changes/23780) - Add vote up/down support to p4review step.  JENKINS-46755

[@23756](https://swarm.workshop.perforce.com/changes/23756) - Leave the filesys.utf8bom property unset.  Updated docs to explain how to set the undoc configurable for builds slaves.  JENKINS-49141


### Release 1.8.7 (major features/fixes)

[@23744](https://swarm.workshop.perforce.com/changes/23744) - Update P4Java to 2017.2.1635107 - Fix for ShiftJIS charset JENKINS-49916 and reconcile -w cleanup of symlinks JENKINS-49393 

[@23696](https://swarm.workshop.perforce.com/changes/23696) - Document changelist reporting behavior when using multiple p4sync steps.  JENKINS-48854 #review-23697

[@23684](https://swarm.workshop.perforce.com/changes/23684) - Merge pull request #70 from PaSaSaP/master.  Expand view mappings.


### Release 1.8.6 (major features/fixes)

[@23652](https://swarm.workshop.perforce.com/changes/23652) - Use getPreviousCompletedBuild().  The lastBuild from getPreviousBuild() may be in progress or blocked.  JENKINS-49617

[@23649](https://swarm.workshop.perforce.com/changes/23649) - Document HUDSON_CHANGELOG_FILE environment variable.  JENKINS-49383

[@23648](https://swarm.workshop.perforce.com/changes/23648) - Document lightweight checkout for Pipeline and MultiBranch.  JENKINS-49048 JENKINS-49678

[@23645](https://swarm.workshop.perforce.com/changes/23645) - Use DEFAULT_FILE_LIMIT if max file limit is set to 0.  JENKINS-49633

[@23629](https://swarm.workshop.perforce.com/changes/23629) - Fix helper text for Environment.  JENKINS-31664

[@23626](https://swarm.workshop.perforce.com/changes/23626) - Do not change the workspace root during polling.  JENKINS-48434

[@23625](https://swarm.workshop.perforce.com/changes/23625) - Tidy up Populate documentation.  JENKINS-49283

[@23624](https://swarm.workshop.perforce.com/changes/23624) - Extended test to cover issues when using global libraries.  JENKINS-44800

[@23622](https://swarm.workshop.perforce.com/changes/23622) - Add Library path to client name for a unique SyncID.  JENKINS-49047

[@23621](https://swarm.workshop.perforce.com/changes/23621) - Performance - Only add trust when needed.  May fix issue JENKINS-44430

[@23620](https://swarm.workshop.perforce.com/changes/23620) - Merge pull request #69 from jfperusse-bhvr/master.  Fix issue unshelving move/delete of locked files.


### Release 1.8.5 (major features/fixes)

[@23604](https://swarm.workshop.perforce.com/changes/23604) - Merge pull request #68 from ChadiEM/deleted-jenkinsfile.  Make sure branch is not discovered when a Jenkinsfile has been removed.

[@23590](https://swarm.workshop.perforce.com/changes/23590) - Fix file limits for change reporting.  JENKINS-47602

[@23589](https://swarm.workshop.perforce.com/changes/23589) - Remove forced clobber and set default clobber option to true.  JENKINS-49041

[@23544](https://swarm.workshop.perforce.com/changes/23544) - Update dependancies to latest supported version for Jenkins 1.642.3

[@23536](https://swarm.workshop.perforce.com/changes/23536) - Merge pull request #67 from jromigh/master.  Adding UI alerts for Pattern Filter errors

[@23535](https://swarm.workshop.perforce.com/changes/23535) - Change console output to use <span>. JENKINS-45611

[@23534](https://swarm.workshop.perforce.com/changes/23534) - Move relative P4ROOT for tests into target/ folder. JENKINS-45612

[@23533](https://swarm.workshop.perforce.com/changes/23533) - Merge pull request #66 from jromigh/master. Implementing a Java Pattern filter for polling. JENKINS-41217

[@23478](https://swarm.workshop.perforce.com/changes/23478) - Merge pull request #65 from PhRX/jenkins-44845. JENKINS-48845 : Backward compatibility for pre-swarm P4 releases


### Release 1.8.4 (major features/fixes)

[@23425](https://swarm.workshop.perforce.com/changes/23425) - Backwards compatibility for p4 describe.  Max limit on files for p4 describe (-m flag) was introduced in 2014.1  JENKINS-48433

[@23403](https://swarm.workshop.perforce.com/changes/23403) - Update P4Java 2017.2.1601181  P4Java fix to throw p4 print warnings used during lightweight checkout.

[@23373](https://swarm.workshop.perforce.com/changes/23373) - Lightweight checkout support.  Implementation for scm-api classes SCMFile and SCMFileSystem, allowing Jenkins to navigate Perforce within the scope of a workspace view.  Lightweight checkout uses a tempoary Perforce workspace to naviagete and fetch the files.  The client name and client view mapping will be modified from a template name e.g. jenkins-${NODE_NAME}-${JOB_NAME} to the tempoary name jenkinsTemp-UUID.  Alternativly if a user as used ${P4_CLIENT} in the client mapping this will remain unchanged and will be get expanded during the job run.  JENKINS-45999  JENKINS-46269

[@23320](https://swarm.workshop.perforce.com/changes/23320) - Remove deprecated ID for SCMSource constructor.


### Release 1.8.3 (major features/fixes)

[@23263](https://swarm.workshop.perforce.com/changes/23263) - Update NavigateHelper for future use.

[@23261](https://swarm.workshop.perforce.com/changes/23261) - Backwards compatibility for new Mappings field in MultiBranch.


### Release 1.8.2 (major features/fixes)

[@23239](https://swarm.workshop.perforce.com/changes/23239) - Add configuration parameter for choosing whether to allow auto-submission of config changes to perforce.  This can avoid creating floods of changelists at the cost of requiring a manual changelist submit. A scheduled task may be alleviate the need to manually submit the updated changes while still reducing the number of changelists created.

[@23234](https://swarm.workshop.perforce.com/changes/23234) - Avoid resetting workspace root during polling operation.  Ideally, workspaces shouldn't need to be reconfigured every poll, but adjusting the polling logic to only create-if-not-exists is beyond my scope.  JENKINS-46908

[@23218](https://swarm.workshop.perforce.com/changes/23218) - Merge pull request #63 from ADTRAN/JENKINS-34052. JENKINS-34052 Poll for concurrent jobs

[@23217](https://swarm.workshop.perforce.com/changes/23217) - Add the ability to provide a view spec to Helix Branches SCM Pr/61. Review Pr/64

[@23216](https://swarm.workshop.perforce.com/changes/23216) - Allow expandedDesc to be used if only Description has been set. This can occur in the "Save Configuration" beta functionality.


### Release 1.8.1 (major features/fixes)

[@23192](https://swarm.workshop.perforce.com/changes/23192) - Workaround for EXECUTOR_NUMBER not being set.

[@23190](https://swarm.workshop.perforce.com/changes/23190) - Merge pull request #62 from fbyrne/standardize-nodename-var.  Standardize the evaluation of what NODE_NAME in p4 workspace name generation.

[@23155](https://swarm.workshop.perforce.com/changes/23155) - Remove Workspace cloning for better concurrent builds.  Remove Workspace cloning and encourage users to make use of EXECUTOR_NUMBER in the Jenkins job name.  JENKINS-41432

[@23071](https://swarm.workshop.perforce.com/changes/23071) - Fix for Swarm P4Approve step; use ApproveState.name not id.


### Release 1.8.0 (major features/fixes)

[@23042](https://swarm.workshop.perforce.com/changes/23042) - Disable BOM addition to UTF8 files.  https://github.com/p4paul/p4-jenkins/issues/32

[@23024](https://swarm.workshop.perforce.com/changes/23024) - Merge pull request #59 from fbyrne/JENKINS-45657.  JENKINS-45657 Recreation of pull request

[@23018](https://swarm.workshop.perforce.com/changes/23018) - Support null changelogFile if checkout sets changelog:false.  JENKINS-46352

[@23017](https://swarm.workshop.perforce.com/changes/23017) - **MultiBranch** configuration cleanup.  Remove BETA, hide standard options in Advanced group and added isGraphCompatible check in Populate Descriptor.

[@23016](https://swarm.workshop.perforce.com/changes/23016) - Draft docs for **Global Library** and file for MultiBranch.

[@22999](https://swarm.workshop.perforce.com/changes/22999) - Fix sync options on FlushOnly and CheckOnly.  JENKINS-46352

[@22987](https://swarm.workshop.perforce.com/changes/22987) - Clean up changelogFilename reporting for HUDSON\_CHANGELOG\_FILE.  JENKINS-37442

[@22986](https://swarm.workshop.perforce.com/changes/22986) - Copy environment map to EnvVars.  JENKINS-37584


### Release 1.7.7 (major features/fixes)

[@22971](https://swarm.workshop.perforce.com/changes/22971) - Update p4java 2017.2.1577651.  Server Property sequence fix (used by Swarm URL).

[@22970](https://swarm.workshop.perforce.com/changes/22970) - Merge pull request #57 from jfperusse-bhvr/fix-changelog-errors. Fix changelog issues caused by NullPointerException in getSwarm. JENKINS-47336


### Release 1.7.6 (major features/fixes)

[@22952](https://swarm.workshop.perforce.com/changes/22952) - POM commons-lang3:3.5 used at runtime

[@22942](https://swarm.workshop.perforce.com/changes/22942) - POM Update.  Remove dependancy on workflow-aggregator and re-add 'test' dependancies for pipeline/workflow. 

[@22934](https://swarm.workshop.perforce.com/changes/22934) - Approve Step: Added description and default fields.

[@22928](https://swarm.workshop.perforce.com/changes/22928) - Swarm approve step for Pipeline and FreeStyle jobs.

[@22926](https://swarm.workshop.perforce.com/changes/22926) - Expose P4\_REVIEW and P4\_REVIEW\_STATUS.  Extended test coverage and removed duplicate code for building the environment.

[@22915](https://swarm.workshop.perforce.com/changes/22915) - Advanced AllHosts login option (login -a)

[@22893](https://swarm.workshop.perforce.com/changes/22893) - Merge pull request #56 from jfperusse-bhvr/fix-unshelve-delete-exclusive.  Manually delete exclusively locked files for reviews.  JENKINS-47141

[@22866](https://swarm.workshop.perforce.com/changes/22866) - Merge pull request #55 from jenkinsci/hth - Global Pipeline Library support. JENKINS-46121 JENKINS-46550

[@22858](https://swarm.workshop.perforce.com/changes/22858) - Merge pull request #54 from mihailogazda/master.  P4Unshelve build step can now be skipped successfully if the changelist ID is not set.

[@22856](https://swarm.workshop.perforce.com/changes/22856) - Remove guessBrowser() and lookup browser when used. guessBrowser() gets called a lot and each lookup opens a Perforce connection. JENKINS-46810

[@22841](https://swarm.workshop.perforce.com/changes/22841) - Switch to `P4.Swarm.URL` property for Swarm url. JENKINS-45464

[@22840](https://swarm.workshop.perforce.com/changes/22840) - Merge pull request #49 from aosterkamp/master - add support for "P4Trigger" declarative pipeline trigger

[@22830](https://swarm.workshop.perforce.com/changes/22830) - Prevent log spam by changing log level to fine. pr/49 @rebnridgway


### Release 1.7.5 (major features/fixes)

[@22822](https://swarm.workshop.perforce.com/changes/22822) - Pipeline trigger fixes for Graph.  Fetch JobList outside ExecutorService.

[@22821](https://swarm.workshop.perforce.com/changes/22821) - Merge pull request #53 from jenkinsci/TagAction-order.  Get Last TagAction. JENKINS-37618 JENKINS-45613

[@22818](https://swarm.workshop.perforce.com/changes/22818) - Remove duplicate cleanup and add alias for old name.

[@22817](https://swarm.workshop.perforce.com/changes/22817) - Raise warning if a static label is used during checkout.  JENKINS-44852

[@22816](https://swarm.workshop.perforce.com/changes/22816) - Merge pull request #51 from jenkinsci/limit-describe.  Limit output from describe. JENKINS-46671

[@22815](https://swarm.workshop.perforce.com/changes/22815) - Provide repo with connection.getCommitObject().  The `p4 graph cat-file commit` requires `super` level access if the repo name is not provided.  JENKINS-46595

[@22808](https://swarm.workshop.perforce.com/changes/22808) - Create directories as required when printing locked file.  JENKINS-37868

[@22807](https://swarm.workshop.perforce.com/changes/22807) - Merge pull request #52 from jfperusse-bhvr/fix-unshelve-exclusive.  Fix unshelving of exclusively checked out files.  JENKINS-46599

[@22806](https://swarm.workshop.perforce.com/changes/22806) - Remove debug `jenkins/` endpoint from sample trigger.

[@22796](https://swarm.workshop.perforce.com/changes/22796) - Update plugin POM 2.33

[@22758](https://swarm.workshop.perforce.com/changes/22758) - Use '/' in getPathBuilder.  Depot syntax will always be '/' only local syntax requires File.separator. JENKINS-46414


### Release 1.7.4 (major features/fixes)

[@22702](https://swarm.workshop.perforce.com/changes/22702) - Fix polling if Jenkinsfile and project workspace names are similar.  Removes an old patch for JENKINS-43877 as concurrent build IDs are now filtered out of the syncID.

[@22701](https://swarm.workshop.perforce.com/changes/22701) - SyncID to ignore clone ID in workspace name.  Tracking build history during concurrent builds.

[@22684](https://swarm.workshop.perforce.com/changes/22684) - Added @Symbol to Extension classes for Pipeline Syntax.

[@22683](https://swarm.workshop.perforce.com/changes/22683) - Implement a FlushOnly sync for "Populate Options" (abbec)

[@22678](https://swarm.workshop.perforce.com/changes/22678) - MultiBranch support for Helix4Git (GitHub PR model).  Update MultiBranch product names to Helix.


### Release 1.7.3 (major features/fixes)

[@22632](https://swarm.workshop.perforce.com/changes/22632) - Update P4Java 2017.2.1535715 - Unicode buffer fixes for syncing UFT16 files.  JENKINS-45453 JENKINS-45580

[@22557](https://swarm.workshop.perforce.com/changes/22557) - Downgraded due to hpi:run error in parent pom.


### Release 1.7.2 (major features/fixes)

[@22555](https://swarm.workshop.perforce.com/changes/22555) - Update P4Java 2017.2.1531685.  Patch for parallel sync - thread authentication on Edge/Commit servers.

[@22538](https://swarm.workshop.perforce.com/changes/22538) - Added 'Force' option to 'SyncOnly'.  To get 'sync -f' set both 'have' and 'force'; for 'sync -p' just set 'force'.  JENKINS-45127

[@22537](https://swarm.workshop.perforce.com/changes/22537) - Use Decoded URL path for local delete.  Local client syntax uses URL @ encoding, but the local filesystem needs the decoded path.  JENKINS-45339

[@22536](https://swarm.workshop.perforce.com/changes/22536) - Modified getAction to return uppercase icon name.  JENKINS-45407 (tpeths)

[@22532](https://swarm.workshop.perforce.com/changes/22532) - Return empty list on error for listRepos().  JENKINS-45420

[@22514](https://swarm.workshop.perforce.com/changes/22514) - Raise Errors from StreamingCallbacks.

[@22513](https://swarm.workshop.perforce.com/changes/22513) - Lock resource on P4ChangeSet.  Prevent parallel writers from getting the same file.


### Release 1.7.1 (major features/fixes)

[@22408](https://swarm.workshop.perforce.com/changes/22408) - Set SCM Environment for Jenkins 2.60+. JENKINS-37584 JENKINS-40885

[@22400](https://swarm.workshop.perforce.com/changes/22400) - (tpeths) Enabling Graph support in p4sync DSL.

[@22398](https://swarm.workshop.perforce.com/changes/22398) - Pass Perforce ticket using BasicAuth to Swarm API.

[@22396](https://swarm.workshop.perforce.com/changes/22396) - MultiBranch support for Perforce Swarm Reviews. A work in progress - adds Swarm API support to find branches and reviews from a Swarm project.

[@22384](https://swarm.workshop.perforce.com/changes/22384) - Remove duplicate code for Stream name auto completion.

[@22372](https://swarm.workshop.perforce.com/changes/22372) - Update Repository Browser for use with Pipeline. May fix the following: JENKINS-43069 JENKINS-37094

[@22349](https://swarm.workshop.perforce.com/changes/22349) - Parallel Sync using P4Java. Update to P4Java 2017.2; using RPC level parallel sync and threaded within the JVM. Native parallel sync no longer requires a 'p4d' binary.

[@22322](https://swarm.workshop.perforce.com/changes/22322) - Option to disable clone for StaticSyncImpl. JENKINS-43281

[@22317](https://swarm.workshop.perforce.com/changes/22317) - Find changelog file for Pipeline builds and add to HUDSON_CHANGELOG_FILE environment.

[@22315](https://swarm.workshop.perforce.com/changes/22315) - Pending change cleanup for unshelve. Unshelve would leave files open for add/edit/delete, the 'tidy' option will 'revert -k' the files leaving the content in the workspace, but removing the 'have' list data. Unshelve can now specify a Credential and Workspace, important for situations where more than one sync occurs in a pipeline script. JENKINS-43430

[@22305](https://swarm.workshop.perforce.com/changes/22305) - Update Unshelve Step to use 'Step' and 'SynchronousNonBlockingStepExecution'

[@22257](https://swarm.workshop.perforce.com/changes/22257) - Streaming Asynchronous Callback for Submit. JENKINS-44427 

[@22256](https://swarm.workshop.perforce.com/changes/22256) - Update Publish Step to use 'Step' and 'SynchronousNonBlockingStepExecution'. JENKINS-44427

[@22245](https://swarm.workshop.perforce.com/changes/22245) - P4Trigger use locally defined job and not super class. JENKINS-44251

[@22231](https://swarm.workshop.perforce.com/changes/22231) - Fixed 'No such file(s)' on unshelve check. JENKINS-43430

[@22219](https://swarm.workshop.perforce.com/changes/22219) - Use JenkinsLocationConfiguration to fetch the URL.  If Jenkins getRootUrl() returns null try JenkinsLocationConfiguration.

[@22183](https://swarm.workshop.perforce.com/changes/22183) - Ticket Credential; an empty path will use system default.


### Release 1.7.0 (major features/fixes)

[@22168](https://swarm.workshop.perforce.com/changes/22168) - Check all login messages. Fix for 'pre-user-login' triggers. JENKINS-44166

[@22164](https://swarm.workshop.perforce.com/changes/22164) - Jelly Configuration Documentation for Workspace Spec.

[@22163](https://swarm.workshop.perforce.com/changes/22163) - Manual Workspace support for READONLY and PARTITIONED. Change Type to Enum. JENKINS-39753

[@22159](https://swarm.workshop.perforce.com/changes/22159) - Minor fix on Graph commit change summary. Skip query when listing commits between the same SHAs.

[@22140](https://swarm.workshop.perforce.com/changes/22140) - Update class check on ReviewActionFactory. Fixes Review end-point for Pipeline builds.

[@22139](https://swarm.workshop.perforce.com/changes/22139) - Change MultiBranch pipeline Include box to textarea.

[@22128](https://swarm.workshop.perforce.com/changes/22128) - Helix Graph support for Jenkins. JENKINS-40354


### Release 1.6.2 (major features/fixes)

[@22091](https://swarm.workshop.perforce.com/changes/22091) - Fix Job form issue in P4Groovy.  A custom job spec would return an extra field 'specFormatted' normally hidden by the C++ API, but exposed in P4Java's execMap results.  P4Groovy needs to remove the field before returning the map to the user.

[@22084](https://swarm.workshop.perforce.com/changes/22084) - Merge pull request #43 from tangkun75.  JENKINS-43877: P4 plugin fails during polling for freestyle job

[@22061](https://swarm.workshop.perforce.com/changes/22061) - Formalise P4Groovy and update documentation.


### Release 1.6.1 (major features/fixes)

[@22043](https://swarm.workshop.perforce.com/changes/22043) - Access remote Channel to find NODE_NAME.  If NODE_NAME is not set in the environment, look at the remote channel, then default to 'master'.  JENKINS-34128  JENKINS-43551

[@22040](https://swarm.workshop.perforce.com/changes/22040) - Merge pull request #41 from lizlam/master.  Strip the trailing slash in the depot path so that p4sync step doesn't fail.

[@22038](https://swarm.workshop.perforce.com/changes/22038) - Merge pull request #42 from tangkun75/JENKINS-43770_p4sync_in_parallel_causes_invalid_SCM_triggering.  JENKINS-43770 p4sync in parallel causes invalid SCM triggering

[@22021](https://swarm.workshop.perforce.com/changes/22021) - Set environment to expand client name for Publish and Remove Client.  JENKINS-43378

[@22019](https://swarm.workshop.perforce.com/changes/22019) - Fix TaskListener serialisation issue.  TaskListener field must be transient as part of the program state, but is marked `Serializable` for the Task as it is sent over the Remoting Channel.  Update p4 groovy to extend Step and not AbstractStepImpl.

[@21937](https://swarm.workshop.perforce.com/changes/21937) - Only reports changes since the last build.  Previously the build summary reported all changes since the last successful build.  JENKINS-40747


### Release 1.6.0 (major features/fixes)

[@21923](https://swarm.workshop.perforce.com/changes/21923) - Added old ClientHelper constructor and mark Deprecated.  ClientHelper constructor breaking change introduced in 1.3.6 #26

[@21865](https://swarm.workshop.perforce.com/changes/21865) - Update P4Java to 2016.1.1499206

[@21856](https://swarm.workshop.perforce.com/changes/21856) - Update min Jenkins version

[@21821](https://swarm.workshop.perforce.com/changes/21821) - Merge pull request #40 from s-sutherland/slaveexec.  P4Groovy execution to run on the slave

[@21795](https://swarm.workshop.perforce.com/changes/21795) - Fix Credential Test to use RSH server.  Jenkins 34825

[@21794](https://swarm.workshop.perforce.com/changes/21794) - Merge pull request #39 from Dohbedoh/JENKINS-34825.  Jenkins 34825


### Release 1.5.1 (major features/fixes)

[@21779](https://swarm.workshop.perforce.com/changes/21779) - Help for MultiBranch include field and update BETA label.  JENKINS-32616

[@21758](https://swarm.workshop.perforce.com/changes/21758) - Merge pull request #38 from jenkinsci/dev.  scm-api 2.0.2 updates


### ~~Release 1.5.0~~ (broken deployment)

### Release 1.4.14 (major features/fixes)

[@21668](https://swarm.workshop.perforce.com/changes/21668) - Identifier fix by adding filtering to resources.

[@21659](https://swarm.workshop.perforce.com/changes/21659) - Cleanup TagAction.  Only call get method on TagAction once per entry.  Fetch values at construction or workspace creation, not on demand.

[@21658](https://swarm.workshop.perforce.com/changes/21658) - Reduce login requests.  Avoid running `p4 login -s` twice.

[@21642](https://swarm.workshop.perforce.com/changes/21642) - Merge pull request #37 from i386/feature/JENKINS-41459.  JENKINS-41459 fixes NPE when credential is null

[@21562](https://swarm.workshop.perforce.com/changes/21562) - Expand variables for Spec Workspace definitions.  Add test to verify variable expansion for name and view.


### Release 1.4.13 (major features/fixes)

[@21449](https://swarm.workshop.perforce.com/changes/21449) - **Rename** and **Deprecated** the old P4Groovy run method.  Force use of variable arg/Array method over a single String.  Add support for List<String> PR #35.  JENKINS-40454

[@21447](https://swarm.workshop.perforce.com/changes/21447) - P4Groovy can take variable args or one string for run.  JENKINS-40454

[@21437](https://swarm.workshop.perforce.com/changes/21437) - Prevent NPE on paths with @ in the parent.  JENKINS-40055


### Release 1.4.12 (major features/fixes)

[@21372](https://swarm.workshop.perforce.com/changes/21372) - Support custom SyncID.  Exclude BUILD_NUMBER from SyncID.  Pushed logging to higher level, so it is not reporting TagActions when building the Environment.  JENKINS-40460

[@21324](https://swarm.workshop.perforce.com/changes/21324) - Create syncID to track syncs when polling.  Original design used the client name to track the sync from previous builds, however as polling is on the master if NODE_NAME is used it breaks with slaves.  JENKINS-40356

[@21272](https://swarm.workshop.perforce.com/changes/21272) - Use ExecutorService to prevent blocking during polling task.  JENKINS-39278 JENKINS-39152

[@21271](https://swarm.workshop.perforce.com/changes/21271) - Prevent NPE on polling if no previous builds. JENKINS-40356


### Release 1.4.11 (major features/fixes)

[@21228](https://swarm.workshop.perforce.com/changes/21228) - Prevent NPE if client is not found.  If the workspace client name is changed in the Jenkins file, then getLastChange() will not find a previous actions and should return null.  JENKINS-40258

[@21215](https://swarm.workshop.perforce.com/changes/21215) - Polling fix for Multi client support in Pipeline.  Jenkins polls for each SCM checkout, so must poll each workplace and therefore need to lookup last build information for each sync.  Polling now uses same lookup as Change Summary.    JENKINS-38401 JENKINS-37462 JENKINS-39652

[@21207](https://swarm.workshop.perforce.com/changes/21207) - Multi client support in Pipeline.  If two or more `p4sync` operations are called in one Pipeline script, they MUST have different client names.  During a build, multiple entries are added to the build history.  JENKINS-38401 JENKINS-37462 JENKINS-39652

[@21190](https://swarm.workshop.perforce.com/changes/21190) - Test for pull request #34 Custom workspace support.

[@21189](https://swarm.workshop.perforce.com/changes/21189) - Merge pull request #34 from DrakkenWulf/cleanChange. Custom workspace support.

[@21163](https://swarm.workshop.perforce.com/changes/21163) - Add P4HOST to parallel sync.

[@21155](https://swarm.workshop.perforce.com/changes/21155) - Update documentation for new P4_CHANGELIST behavior.  JENKINS-37584

[@21154](https://swarm.workshop.perforce.com/changes/21154) - P4HOST support for connection.

[@21153](https://swarm.workshop.perforce.com/changes/21153) - Merge pull request #32 from pyssling/master.  Use latest change for all builds when polling.  JENKINS-40048

[@21151](https://swarm.workshop.perforce.com/changes/21151) - Update Release notes for 1.4.10


### Release 1.4.10 (major features/fixes)

[@21149](https://swarm.workshop.perforce.com/changes/21149) - Null protection for Ticket Value/Path.

[@21133](https://swarm.workshop.perforce.com/changes/21133) - P4 Environment Variables for pipeline.  JENKINS-37584

[@21077](https://swarm.workshop.perforce.com/changes/21077) - Switch polling back to build.xml - [@in Polling behaviour; use last build details in the build.xml file.  It gathers 'last' change details](https://swarm.workshop.perforce.com/changes/in Polling behaviour; use last build details in the build.xml file.  It gathers 'last' change details)

[@21062](https://swarm.workshop.perforce.com/changes/21062) - Avoid dependency errors on WorkflowJob.

### Release 1.4.9 (major features/fixes)

[@21042](https://swarm.workshop.perforce.com/changes/21042) - Use `textarea` for change description (CSS: 10 rows max).  JENKINS-39257

[@21024](https://swarm.workshop.perforce.com/changes/21024) - Allow concurrent use a Client during polling.  Prevents Jenkins from blocking trigger events when polling and a build is in progress.  JENKINS-38425

[@20986](https://swarm.workshop.perforce.com/changes/20986) - Ground work for MultiJob support.  Pushed validate up to ConnectionHelper and added new Perforce functions.

[@20980](https://swarm.workshop.perforce.com/changes/20980) - Functional test upgrade.  Switched to RSH pipe for Perforce connection and upgraded to 15.1 P4D binaries.  Made use of ClassRule for Jenkins (faster startup) and a Rule for Perforce with separate roots (to allow for parallel test runs).

[@20954](https://swarm.workshop.perforce.com/changes/20954) - Add purge -S<n> option for the Submit stage of Publish.  If a value is provided it will set the purge option on all files found as part of the reconcile (ADD/EDIT).  It will not remove the purge option once set.  JENKINS-36112

[@20950](https://swarm.workshop.perforce.com/changes/20950) - Add variable expansion to SpecFile based workspaces.

[@20908](https://swarm.workshop.perforce.com/changes/20908) - Filter for WorkFlowJob.  Avoids duplicate Review buttons in FreeStyle Jobs.

[@20903](https://swarm.workshop.perforce.com/changes/20903) - Document BasicAuth from Reviews.

[@20902](https://swarm.workshop.perforce.com/changes/20902) - Enable Swarm builds on pipeline.  JENKINS-38233

[@20897](https://swarm.workshop.perforce.com/changes/20897) - Report all P4Java errors on validate.  Ignore 'no such file' warning on populate, caused by empty file list on sync -k.

[@20893](https://swarm.workshop.perforce.com/changes/20893) - Update trigger documentation to include using a CRUMB for security.

[@20889](https://swarm.workshop.perforce.com/changes/20889) - Document downstream changelists. Workaround for JENKINS-33163.

[@20877](https://swarm.workshop.perforce.com/changes/20877) - Merge pull request #29 from tangkun75/master.  Not throw AbortException when unknown errors (except for those ignoring ones ) occurred during P4 task post-validation

### Release 1.4.8 (major features/fixes)

[@20873](https://swarm.workshop.perforce.com/changes/20873) - Support Pin to a change in a counter.

[@20820](https://swarm.workshop.perforce.com/changes/20820) - Find bug fix for null checking on Jenkins.getInstance()

[@20818](https://swarm.workshop.perforce.com/changes/20818) - Global limits for changes and files.  Max number of change lists to show for a build (default 10.  Max number of files to list in a change (default 50)

[@20814](https://swarm.workshop.perforce.com/changes/20814) - Merge pull request #31 from psytale/discoverable_mails.  Make P4AddressResolver discoverable

[@20788](https://swarm.workshop.perforce.com/changes/20788) - Fix for findbugs

[@20787](https://swarm.workshop.perforce.com/changes/20787) - Polling Fix for use with quiet period.  Switched all uses of change/label to P4Revision object and implemented Comparable.  The changes to build are now calculated at build time (after the quiet period) not during the polling phase.  JENKINS-36883

[@20786](https://swarm.workshop.perforce.com/changes/20786) - Upgrade p4java 2015.2.1365273

### Release 1.4.7 (major features/fixes)

[@20667](https://swarm.workshop.perforce.com/changes/20667) - Merge pull request #28 from tangkun75/master.  Workspace's root path overwritten to "null" when enabling polling in Pipeline job

[@20620](https://swarm.workshop.perforce.com/changes/20620) - Change 'now' to the latest change at the point of sync.  JENKINS-36883

[@20598](https://swarm.workshop.perforce.com/changes/20598) - P4Credentials Interfaces for Mixins.  Updated test cases to match changes from CredentialsNameProvider.

[@20593](https://swarm.workshop.perforce.com/changes/20593) - Unshelve options in Pipeline.  Update merge options and support unshelve ListBoxModel Option in Pipeline.

[@20418](https://swarm.workshop.perforce.com/changes/20418) - Replace FileCallable and checkRoles with MasterToSlaveCallable.

[@20308](https://swarm.workshop.perforce.com/changes/20308) - P4Groovy (experimental).  Get a P4 object in groovy.  Supporting basic functions: run (to run perforce commands), fetch and save (to access Perforce specs).

### Release 1.4.6 (major features/fixes)

[@20247](https://swarm.workshop.perforce.com/changes/20247) - Access 'author' User and User url through the current P4ChangeEntry

[@20246](https://swarm.workshop.perforce.com/changes/20246) - Convert merge options for unshelve to ListBoxModel Option (from basic jelly) @atiniir

[@20245](https://swarm.workshop.perforce.com/changes/20245) - Don't disconnect after an abort.  Don't disconnect from the P4 server immediately after an abort is detected.  JENKINS-37487 @stuartr

[@20226](https://swarm.workshop.perforce.com/changes/20226) - Support for Multiple SCMs Plugin.  Optional dependency on multiple-scms and helper method for safely casting SCM object to PerforceSCM instance, especially in the scenario where the SCM object is a MultiSCM instance.  JENKINS-32064

[@20224](https://swarm.workshop.perforce.com/changes/20224) - Implementing View Mask Filter.  JENKINS-30622  JENKINS-28225 @stuartr

[@20208](https://swarm.workshop.perforce.com/changes/20208) - Parallel sync for SSL connections.  JENKINS-37476

[@20207](https://swarm.workshop.perforce.com/changes/20207) - Added implementation for getAffectedFiles()

[@20201](https://swarm.workshop.perforce.com/changes/20201) - Moved set to null checked block.

[@20190](https://swarm.workshop.perforce.com/changes/20190) - Expose HUDSON_CHANGELOG_FILE environment variable.  JENKINS-37442 @p4karl

[@20179](https://swarm.workshop.perforce.com/changes/20179) - Javadoc fixes for java 8 builds.

[@20174](https://swarm.workshop.perforce.com/changes/20174) - Adding retries (5) to P4Server.clean @stuartr.  JENKINS-26764

### Release 1.4.5 (major features/fixes)

Not a release.

### Release 1.4.4 (major features/fixes)

[@20038](https://swarm.workshop.perforce.com/changes/20038) - Add Help buttons to ReviewBuild page.  JENKINS-35437

[@20015](https://swarm.workshop.perforce.com/changes/20015) - Force sort order for 'p4 cstat' and 'p4 changes'.  Resolve issue for Polling.  JENKINS-37124

[@20012](https://swarm.workshop.perforce.com/changes/20012) - Logging for changelist calculation at checkout.

[@20011](https://swarm.workshop.perforce.com/changes/20011) - @mjoubert Add parallel sync options to Sync Only

[@19918](https://swarm.workshop.perforce.com/changes/19918) - Use StreamingCallback for Reconcile.  Reduce memory for servers 14.1 and above.

### Release 1.4.3 (major features/fixes)

[@19890](https://swarm.workshop.perforce.com/changes/19890) - Set environment before removing workspace.  Verify NPE with test case.  JENKINS-36422

[@19881](https://swarm.workshop.perforce.com/changes/19881) - Change label form submit button to input.

[@19880](https://swarm.workshop.perforce.com/changes/19880) - Use system logger to avoid serialisation of listener.

[@19878](https://swarm.workshop.perforce.com/changes/19878) - Export getTimestamp() and getCommitId() for email-ext.  JENKINS-36409

### Release 1.4.2 (major features/fixes)

[@19830](https://swarm.workshop.perforce.com/changes/19830) - @mjoubert Add checks for sync CL and head CL

[@19790](https://swarm.workshop.perforce.com/changes/19790) - Add a propagate delete option to Publish.  Originally only add/edits were permitted to prevent users deleting files with incorrect View maps, but this option allows deletes if enabled.  JENKINS-27885

[@19765](https://swarm.workshop.perforce.com/changes/19765) - A Post Build 'P4 Cleanup' with DSL support.  Can be added as a PostBuild action to remove the client workspace spec.  It uses the options set in the Global configuration to delete the Client Spec and/or Local Files.

[@19764](https://swarm.workshop.perforce.com/changes/19764) - Simplify RemoveClientTask by using AbstractTask.

[@19752](https://swarm.workshop.perforce.com/changes/19752) - Remove 'modtime' option from ForceClean

[@19750](https://swarm.workshop.perforce.com/changes/19750) - Add default workspace name to Manual Client.

[@19716](https://swarm.workshop.perforce.com/changes/19716) - Login error to include exception message.

### Release 1.4.1 (major features/fixes)

[@19712](https://swarm.workshop.perforce.com/changes/19712) - NPE fix when code line has no changes.

[@19641](https://swarm.workshop.perforce.com/changes/19641) - Merge pull request #26 from Dohbedoh/hotfix/JENKINS-25249. Fixed Null Pointer dereference

[@19633](https://swarm.workshop.perforce.com/changes/19633) - Merge pull request #25 from amuniz/JENKINS-35210. Fix for SECURITY-170

[@19630](https://swarm.workshop.perforce.com/changes/19630) - Merge pull request #24 from Dohbedoh/JENKINS-25249. Changes to make the P4 Trigger pipeline compatible. Added some tests of the P4 trigger for both Pipeline/Non Pipeline jobs.

[@19612](https://swarm.workshop.perforce.com/changes/19612) - Polling fix, if no previous build.

### Release 1.4.0 (major features/fixes)

[@19593](https://swarm.workshop.perforce.com/changes/19593) - More minor fixes to satisfy FindBugs Analysis.

[@19581](https://swarm.workshop.perforce.com/changes/19581) - Minor fixes to satisfy FindBugs Analysis.

[@19578](https://swarm.workshop.perforce.com/changes/19578) - Upgrade plugin to 2.9

### Release 1.3.10 (major features/fixes)

[@19574](https://swarm.workshop.perforce.com/changes/19574) - Support for guessBrowser and getKey.

[@19519](https://swarm.workshop.perforce.com/changes/19519) - Jelly header update.  <?jelly escape-by-default='true'?>

[@19516](https://swarm.workshop.perforce.com/changes/19516) - Merge pull request #23 from Dohbedoh/JENKINS-29979

[@19455](https://swarm.workshop.perforce.com/changes/19455) - Minor package fix.

[@19454](https://swarm.workshop.perforce.com/changes/19454) - Hide `P4_TICKET` for secure systems.  An option in the global configuration to hide the `P4_TICKET` variable (not set by default).  JENKINS-24591

[@19431](https://swarm.workshop.perforce.com/changes/19431) - Raise failed submit as Exception and mark build as failed.  JENKINS-34770

### Release 1.3.9 (major features/fixes)

[@19374](https://swarm.workshop.perforce.com/changes/19374) - Poll on Master (without workspace).  New Polling Filter Poll on Master using Last Build.  Only polls on the master and fetches it change from the last Build. Note that 0 is assumed if no previous build is found.  JENKINS-32814

[@19365](https://swarm.workshop.perforce.com/changes/19365) - Track syncs with a 'Change' field in the Workspace Description.  Polling used p4 cstat to determine the last synced change in a workspace.  However, if the have list is empty (such as in the Force Clean Populate mode) it would appear as if no changes have been synced.  JENKINS-32518 JENKINS-32523

[@19357](https://swarm.workshop.perforce.com/changes/19357) - Populate class support for DSL p4sync.  JENKINS-34318

[@19325](https://swarm.workshop.perforce.com/changes/19325) - ParallelSync needs to be Serializable.  JENKINS-29228

[@19324](https://swarm.workshop.perforce.com/changes/19324) - Added Parallel Sync.  An Advanced Populate option.  Must specify the PATH to the p4 executable as parallel sync is not supported by p4java.  JENKINS-29228

[@19303](https://swarm.workshop.perforce.com/changes/19303) - Protect against null when no jobs are in a changelist.  JENKINS-33993

[@19296](https://swarm.workshop.perforce.com/changes/19296) - Typo missmatch.  JENKINS-34541

[@19294](https://swarm.workshop.perforce.com/changes/19294) - Schedule build on trigger for subscribed Jobs.  JENKINS-33858

[@19090](https://swarm.workshop.perforce.com/changes/19090) - Revert files before deleting a Workspace.  JENKINS-24070

### Release 1.3.8 (major features/fixes)

[@18889](https://swarm.workshop.perforce.com/changes/18889) - Asynchronous sync operation.  To avoid memory issues with very large sync operations.  #review-18773

[@18809](https://swarm.workshop.perforce.com/changes/18809) - Use p4 describe -s for listing files in a change.  Previously used p4 files @=<change>, but without a client to limit view.  

[@18744](https://swarm.workshop.perforce.com/changes/18744) - Added optional dependancy for Mailer 1.16

[@18723](https://swarm.workshop.perforce.com/changes/18723) - Add a revert option to SyncOnly Populate mode.  Remove the revert -k option from unshelve task and leave it to the Populate cleanup step.

[@18616](https://swarm.workshop.perforce.com/changes/18616) - Fix for critial bug.  Unable to get current change: com.perforce.p4java.exception.RequestException: Can't use a pending changelist number for this command.

[@18550](https://swarm.workshop.perforce.com/changes/18550) - Add Perforce email address if Jenkins User is undefined.  Other email plugins use P4UserProperty, but for if UserProperty is undefined update this one too.  JENKINS-32879

[@18440](https://swarm.workshop.perforce.com/changes/18440) - Prevent Delete actions on the Publish step.  Limit reconcile to -ae (add/edit).  JENKINS-28448

[@18336](https://swarm.workshop.perforce.com/changes/18336) - Merge pull request #22 from ADTRAN/master.  Fix polling on workflow jobs.  (JENKINS-29598)

[@18335](https://swarm.workshop.perforce.com/changes/18335) - Slave support for Clean Up Workspace.  Will remove the client and/or versioned files.  Other non versioned files are removed by Jenkins not the p4 plugin. Uncomment lines 67,68 in RemoveClientTask.java to fix Jenkins bug.  JENKINS-24003

### Release 1.3.7 (major features/fixes)

[@18211](https://swarm.workshop.perforce.com/changes/18211) - Fix to DSL example in doc Note.  Populate should not be a child of Workspace.

[@18160](https://swarm.workshop.perforce.com/changes/18160) - Update test case testFreeStyleProject_buildLabel for Jenkins 1.625.3.  Find the form submit button and call a click() instead of the internal submit() method.

[@17835](https://swarm.workshop.perforce.com/changes/17835) - Draft: documentation notes page.  Hints and Tips from Jenkins Users.

[@17834](https://swarm.workshop.perforce.com/changes/17834) - Helix branding update.

[@17303](https://swarm.workshop.perforce.com/changes/17303) - Unsync Perforce versioned files on cleanup.  Dont rely on Jenkins to Delete Perforce versioned files as some OS ACLs cant delete them.  JENKINS-24003

### Release 1.3.6 (major features/fixes)

[@17264](https://swarm.workshop.perforce.com/changes/17264) - Missing retry increment for tests.

[@17263](https://swarm.workshop.perforce.com/changes/17263) - Added enable/disable option for auto versioning. Global option to activate Perforce versioning of Jenkins Configurations.

[@16836](https://swarm.workshop.perforce.com/changes/16836) - Versioning for Jenkins configuration pages. Add credential, depot path location and client workspace name, everything else should be automatic.	JENKINS-25145

[@16820](https://swarm.workshop.perforce.com/changes/16820) - Delete client workspace and files on a delete Jenkins Job. Global Perforce options for delete files and/or delete client. JENKINS-32454

[@16819](https://swarm.workshop.perforce.com/changes/16819) - Clear change-list cache after build (polling or build now). JENKINS-31862

[@16815](https://swarm.workshop.perforce.com/changes/16815) - Enable early binding for CHARSET. Expose CHARSET to AbstractTask to allow the charset to be set at the point the client workspace is set as current in ClientHelper.

[@16793](https://swarm.workshop.perforce.com/changes/16793) - Upgrade p4java 15.2. Fix for 2G limit. JENKINS-25364

[@16791](https://swarm.workshop.perforce.com/changes/16791) - (joel_kovisto) Inject quotes around paths for label view in TaggingTask.java. JENKINS-32351

[@16770](https://swarm.workshop.perforce.com/changes/16770) - Enable P4_CLIENT in client view mapping. 	Store full client name in expand options for use in view. JENKINS-32282

### Release 1.3.5 (major features/fixes)

[@16722](https://swarm.workshop.perforce.com/changes/16722) - Enable Unicode Support for workspace. Originally hardcoded to UTF8 for *nix systems and web browsers.

[@16674](https://swarm.workshop.perforce.com/changes/16674) - Merge pull request #20 from KangTheTerrible/master.  Allow expansion of stream name in manual workspaces

[@16628](https://swarm.workshop.perforce.com/changes/16628) - Set the Client Owner field on update as well as creation. (support for p4maven)

[@16625](https://swarm.workshop.perforce.com/changes/16625) - Set the Workspace Owner at creation (not on update).


### Release 1.3.4 (major features/fixes)

[@16585](https://swarm.workshop.perforce.com/changes/16585) - Fetch full change description.  The 'p4 changes' optimisation only fetched the first 32 characters.  Added the '-l' flag to fetch the full description. JENKINS-31748


### Release 1.3.3 (major features/fixes)

[@16530](https://swarm.workshop.perforce.com/changes/16530) - Skip unshelve task if change is 0 or less.

[@16529](https://swarm.workshop.perforce.com/changes/16529) - DSL for Unshelve task. DSL example: p4unshelve resolve: 'at', shelf: '10831'

[@16516](https://swarm.workshop.perforce.com/changes/16516) - Use empty email "" for deleted users. JENKINS-31169

[@16515](https://swarm.workshop.perforce.com/changes/16515) - Extend support for 'now' revision specifier. If 'now' is used it will be substituted for the latest change within the scope of the workspace view.

[@16514](https://swarm.workshop.perforce.com/changes/16514) - Unshelve and resolve build step. Implements a classic Jenkins Build step (i.e. not Workflow).  Must provide a shelf change number and resolve options. The shelf change number supports variable expansion `${VAR}`.

[@16503](https://swarm.workshop.perforce.com/changes/16503) - If author is not set use unknown. JENKINS-31169

[@16502](https://swarm.workshop.perforce.com/changes/16502) - Advanced Credentials option for RPC_SOCKET_SO_TIMEOUT_NICK. JENKINS-31196

[@16497](https://swarm.workshop.perforce.com/changes/16497) - Fix rest api for affectedPaths. JENKINS-31306

[@16409](https://swarm.workshop.perforce.com/changes/16409) - Use Fix records for Job information. 'p4 fixes' is lighter and still provides enough useful data.  Includes jelly fix and extra job data in changelog.xml.

[@16404](https://swarm.workshop.perforce.com/changes/16404) - Fix flags used during have list cleanup. Flags were inherited from Populate options and being used in the cleanup stage, when they should only be applied to the populate stage.

[@16397](https://swarm.workshop.perforce.com/changes/16397) - Fix for Force Clean with sync -p. Fixed sync to use ...#0 (#none) to remove the have list as part of the cleanup.

[@16390](https://swarm.workshop.perforce.com/changes/16390) - Update change/file reporting. Use `p4 changes @=1234` to get change summary and `p4 files -m50 @=1234` to get limited file list.

[@16389](https://swarm.workshop.perforce.com/changes/16389) - Fix for Force Clean sync flags. You cannot use `-f` and `-p` together.

[@16325](https://swarm.workshop.perforce.com/changes/16325) - Enable 'Populate have list' to function in all modes. Previously if 'Populate have list' was unselected in Force Clean mode it would have no effect. @ksong

[@16297](https://swarm.workshop.perforce.com/changes/16297) - Set P4IGNORE filename to defaults. With slaves and different users I cant rely on the P4IGNORE in the environment, so I set the default .p4ignore or p4ignore.txt on Windows. [#14](https://github.com/p4paul/p4-jenkins/issues/14)

[@16272](https://swarm.workshop.perforce.com/changes/16272) - Filter reported changelists. Remove -1 and duplicate changes.


### Release 1.3.2 (major features/fixes)

[@16086](https://swarm.workshop.perforce.com/changes/16086) - Fix to enable -m on reconcile.

[@16085](https://swarm.workshop.perforce.com/changes/16085) - If Change number is missing try looking for the original. Use p4 change -O if a RequestException is thrown. JENKINS-30525

[@15881](https://swarm.workshop.perforce.com/changes/15881) - Updated testcase to use Template workspace as the Static workspace does not update the Root.

[@15880](https://swarm.workshop.perforce.com/changes/15880) - Remove client host from test workspaces.

[@15879](https://swarm.workshop.perforce.com/changes/15879) - Copy IClientOptions explicitly. JENKINS-30546

[@15866](https://swarm.workshop.perforce.com/changes/15866) - Abort if unshelve fails. JENKINS-30525

[@15858](https://swarm.workshop.perforce.com/changes/15858) - Docs: Workflow setup

[@15808](https://swarm.workshop.perforce.com/changes/15808) - Make P4Revision Serializable. JENKINS-30425

[@15752](https://swarm.workshop.perforce.com/changes/15752) - Update workspace before parallel cloning. Parallel builds clone the workspace using a template, but the base workspace needs to save any changes before the clone.

[@15750](https://swarm.workshop.perforce.com/changes/15750) - 
Use a P4Revision object and not int/String as Object. Insure that there is no ambiguity with the revision specifier. Should fix change summary when using the Workflow plugin. JENKINS-30425

[@15746](https://swarm.workshop.perforce.com/changes/15746) - JENKINS-30387 : Fix problem with saving hours in 12 hour format.


### Release 1.3.1 (major features/fixes)

[@15665](https://swarm.workshop.perforce.com/changes/15665) - Create a template workspace for parallel builds.  If Jenkins attempts a parallel build it creates a workspace@2 directory. This change creates a new template workspace (appended with .clone2) and substitutes the `@` to `%40` in the root path.  JENKINS-29387

[@15663](https://swarm.workshop.perforce.com/changes/15663) - Added P4_USER and P4_TICKET environment variables.  JENKINS-24591

[@15656](https://swarm.workshop.perforce.com/changes/15656) - Updated credentials to extend BaseStandardCredentials.  Allows users to set the ID at creation. JENKINS-29702

[@15645](https://swarm.workshop.perforce.com/changes/15645) - Missing if statement in parseLineEnd.  JENKINS-24025

[@15569](https://swarm.workshop.perforce.com/changes/15569) - Merge pull request #18 from stuartrowe/master [FIXED JENKINS-30163] P4TICKETS file credential doesn't work

[@15557](https://swarm.workshop.perforce.com/changes/15557) - Simplification of ReviewNotifier. Remove Apache HttpClient dependancy and separate setup environment step.  Notification triggered onCompleted event, called after a build is completed.


### Release 1.3.0 (major features/fixes)

[@15515](https://swarm.workshop.perforce.com/changes/15515) - Update P4Java to 2015.1.1210288

[@15503](https://swarm.workshop.perforce.com/changes/15503) - Created P4UserProperty to store Email address. P4UserProperty extends UserProperty to store the Perforce Users email. Then retrieves it with P4AddressResolver by extending MailAddressResolver. JENKINS-28421

[@15491](https://swarm.workshop.perforce.com/changes/15491) - This fix is to expand the Template name. @mjoubert When using a Template the name does not expand (unlike the client name) if it contains variables.

[@15490](https://swarm.workshop.perforce.com/changes/15490) - Check for empty param values. JENKINS-29943

[@15430](https://swarm.workshop.perforce.com/changes/15430) - Trap User Abort and stop Perforce. Uses the tick function on Progress to check if the Thread has been interrupted. If a user aborts the build then the Perforce connection is dropped at the next tick. JENKINS-26650

[@15419](https://swarm.workshop.perforce.com/changes/15419) - Updates README with 'change' vs 'P4_CHANGELIST' issue

[@15403](https://swarm.workshop.perforce.com/changes/15403) - Perforce triggered polling BETA. Perforce triggers on a change-submit and sends a POST to the endpoint http://${JENKINS}/p4/change with the data: payload={"change":"12345","p4port":"localhost:1666"}.  Note: change is not used (yet).

[@15394](https://swarm.workshop.perforce.com/changes/15394) - Workflow-DSL functionality. Tested workflow DSL against 1.596.1 older functionality tested against 1.580.1 @sven_erik_knop

[@15379](https://swarm.workshop.perforce.com/changes/15379) - Ground-work for Workflow-DSL @sven_erik_knop


### Release 1.2.7 (major features/fixes)

[@15347](https://swarm.workshop.perforce.com/changes/15347) - Moved the Expand setup into labelBuild() in order to pass listener (and not null) to getEnvironment().

[@15345](https://swarm.workshop.perforce.com/changes/15345) - Fixed issue with workflow-plugin when setting changelog to false.


### Release 1.2.6 (major features/fixes)

[@15293](https://swarm.workshop.perforce.com/changes/15293) - Add retry attempts to Perforce Tasks. If a task fails due to an exception then the task will retry based on the value specified in the connection Credential.

[@15249](https://swarm.workshop.perforce.com/changes/15249) - Null protection if Label Owner is not set.  Fall back to unknown for user.

[@15138](https://swarm.workshop.perforce.com/changes/15138) - StreamName not shown in Manual Workspace config.


### Release 1.2.5 (major features/fixes)

[@14838](https://swarm.workshop.perforce.com/changes/14838) - Check if the workspace exists before cleanup. JENKINS-29030

[@14779](https://swarm.workshop.perforce.com/changes/14779) - Add shelved changes to built changes list. JENKINS-25724

[@14173](https://swarm.workshop.perforce.com/changes/14173) - Support P4D 15.1 'reconcile -m'. Client workspace MODTIME option is no longer required with -m.

[@14150](https://swarm.workshop.perforce.com/changes/14150) - URL Encode/Decode the depot path for changes. Filenames with ampersands was causing Jelly to break when showing the change detail. JENKINS-29017

[@14040](https://swarm.workshop.perforce.com/changes/14040) - Delay polling if a build is in progress.

[@14035](https://swarm.workshop.perforce.com/changes/14035) - Publish on Success option. Added a checkbox to the Publish step to only shelve/submit change if the build succeeded.

[@13994](https://swarm.workshop.perforce.com/changes/13994) - Make TaskListener as transient.


### Release 1.2.4 (major features/fixes)

[@13800](https://swarm.workshop.perforce.com/changes/13800) - 
Updated P4Java to 15.1

[@13795](https://swarm.workshop.perforce.com/changes/13795) - 
(matthauck) Fix JENKINS-28760: Set line endings explicitly for template workspaces

[@13777](https://swarm.workshop.perforce.com/changes/13777) - 
(matthauck) Fix JENKINS-28726: Allow for default matrix execution strategy

[@13701](https://swarm.workshop.perforce.com/changes/13701) - 
Move Labelling into a Task.

[@13681](https://swarm.workshop.perforce.com/changes/13681) - 
Abstracted Expand class from Workspace.  Added support for Label variable expansion in the name and description.

[@13676](https://swarm.workshop.perforce.com/changes/13676) - 
Added support for `p4 clean`. If the Perforce server is 14.1 or greater then the `-w` flag is used (p4 clean), otherwise the original auto clean up code.


### Release 1.2.3 (major features/fixes)

[@13619](https://swarm.workshop.perforce.com/changes/13619) - 
Document building at a change. JENKINS-28301

[@13604](https://swarm.workshop.perforce.com/changes/13604) - 
Improved error handling and fixed test case issue.

[@13603](https://swarm.workshop.perforce.com/changes/13603) - 
Improved Error for Publish step when connection is down.


### Older Releases

Please refer to the [Activity](https://swarm.workshop.perforce.com/projects/p4-jenkins/activity) feed.
