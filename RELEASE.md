## Release notes

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