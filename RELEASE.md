## Release notes

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

[@21077](https://swarm.workshop.perforce.com/changes/21077) - Switch polling back to build.xml - Change in Polling behaviour; use last build details in the build.xml file.  It gathers 'last' change details on the master thread and not a slave thread.  JENKINS-37462

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

[@20308](https://swarm.workshop.perforce.com/changes/20308) - P4Groovy (experimental).  Get a P4 object in groovy.  Supporting basic functions: ’run’ (to run perforce commands), ‘fetch’ and ‘save’ (to access Perforce specs).

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

[@19374](https://swarm.workshop.perforce.com/changes/19374) - Poll on Master (without workspace).  New Polling Filter ‘Poll on Master using Last Build’.  Only polls on the master and fetches it change from the last Build. Note that 0 is assumed if no previous build is found.  JENKINS-32814

[@19365](https://swarm.workshop.perforce.com/changes/19365) - Track syncs with a 'Change' field in the Workspace Description.  Polling used ‘p4 cstat’ to determine the last sync’ed change in a workspace.  However, if the have list is empty (such as in the Force Clean Populate mode) it would appear as if no changes have been synced.  JENKINS-32518 JENKINS-32523

[@19357](https://swarm.workshop.perforce.com/changes/19357) - Populate class support for DSL p4sync.  JENKINS-34318

[@19325](https://swarm.workshop.perforce.com/changes/19325) - ParallelSync needs to be Serializable.  JENKINS-29228

[@19324](https://swarm.workshop.perforce.com/changes/19324) - Added Parallel Sync.  An Advanced Populate option.  Must specify the PATH to the ‘p4’ executable as parallel sync is not supported by p4java.  JENKINS-29228

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

[@17303](https://swarm.workshop.perforce.com/changes/17303) - Unsync Perforce versioned files on cleanup.  Don’t rely on Jenkins to Delete Perforce versioned files as some OS ACLs can’t delete them.  JENKINS-24003

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

[@16297](https://swarm.workshop.perforce.com/changes/16297) - Set P4IGNORE filename to defaults. With slaves and different users I can’t rely on the P4IGNORE in the environment, so I set the default ’.p4ignore’ or ‘p4ignore.txt’ on Windows. [#14](https://github.com/p4paul/p4-jenkins/issues/14)

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

[@15503](https://swarm.workshop.perforce.com/changes/15503) - Created P4UserProperty to store Email address. P4UserProperty extends UserProperty to store the Perforce User’s email. Then retrieves it with P4AddressResolver by extending MailAddressResolver. JENKINS-28421

[@15491](https://swarm.workshop.perforce.com/changes/15491) - This fix is to expand the Template name. @mjoubert When using a Template the name does not expand (unlike the client name) if it contains variables.

[@15490](https://swarm.workshop.perforce.com/changes/15490) - Check for empty param values. JENKINS-29943

[@15430](https://swarm.workshop.perforce.com/changes/15430) - Trap User Abort and stop Perforce. Uses the ‘tick’ function on Progress to check if the Thread has been interrupted. If a user aborts the build then the Perforce connection is dropped at the next tick. JENKINS-26650

[@15419](https://swarm.workshop.perforce.com/changes/15419) - Updates README with 'change' vs 'P4_CHANGELIST' issue

[@15403](https://swarm.workshop.perforce.com/changes/15403) - Perforce triggered polling BETA. Perforce triggers on a change-submit and sends a POST to the endpoint http://${JENKINS}/p4/change with the data: payload={"change":"12345","p4port":"localhost:1666"}.  Note: ‘change’ is not used (yet).

[@15394](https://swarm.workshop.perforce.com/changes/15394) - Workflow-DSL functionality. Tested workflow DSL against 1.596.1 older functionality tested against 1.580.1 @sven_erik_knop

[@15379](https://swarm.workshop.perforce.com/changes/15379) - Ground-work for Workflow-DSL @sven_erik_knop


### Release 1.2.7 (major features/fixes)

[@15347](https://swarm.workshop.perforce.com/changes/15347) - Moved the Expand setup into labelBuild() in order to pass listener (and not null) to getEnvironment().

[@15345](https://swarm.workshop.perforce.com/changes/15345) - Fixed issue with workflow-plugin when setting changelog to false.


### Release 1.2.6 (major features/fixes)

[@15293](https://swarm.workshop.perforce.com/changes/15293) - Add retry attempts to Perforce Tasks. If a task fails due to an exception then the task will retry based on the value specified in the connection Credential.

[@15249](https://swarm.workshop.perforce.com/changes/15249) - Null protection if Label Owner is not set.  Fall back to “unknown” for user.

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