# p4-jenkins

## Requirements

* Jenkins 1.509 or greater.
* P4D 12.1 or greater.
* Minimum Perforce Protection of 'open' for the Jenkins user. 
If you wish to use the Review Build feature you will need Swarm.  Swarm 2014.2 or greater is required to support Jenkins authentication.

## Install

1. Open Jenkins in a browser; e.g. http://jenkins_host:8080
2. Browse to 'Manage Jenkins' --> 'Manage Plugins' and Select the 'Available' tab.
3. Find the 'P4 Plugin' or use the Filter if needed
4. Check the box and press the 'Install without restart' button

If you are unable to find the plugin, you may need to refresh the Update site.

1. Select the 'Advanced' tab (under 'Manage Plugins')
2. Press the 'Check now' button at the bottom of the page.
3. When 'Done' go back to the update centre and try again.

## Building

To build the plugin and run the tests use the following:

	mvn package
  
Note: for the tests to run you must have p4d in your PATH, to skip tests use the -DskipTests flag.

## Manual install

1. Open Jenkins in a browser; e.g. http://jenkins_host:8080
2. Browse to 'Manage Jenkins' --> 'Manage Plugins’ and Select the 'Advanced' tab.
3. Press the 'Choose File' button under the 'Upload Plugin' section
4. Find the location of the 'p4.hpi' file and Select Upload
5. Choose the 'Download now and install after restart' button (this might be different on the newer version)

## Credentials

The plugin makes use of the Jenkins Credential store making it easier to manage the Perforce Server connection for multiple Jenkins jobs.  Perforce Server credentials must be added to the Global or a user defined domain, using one of the two supported Perforce Credentials: 'Perforce Password Credential' or 'Perforce Ticket Credential'.

![Global credentials](docs/images/1.png)

To add a Perforce Credential:

1. Navigate to the Jenkins Credentials page (select 'Credentials' on the left hand side)
2. Select 'Global credentials' (or add domain if needed)
3. Select 'Add Credentials' from the left hand side
4. Choose 'Perforce Password Credential' from the 'Kind' drop-down select
5. Enter a Description e.g. local test server
6. Enter the P4Port e.g. localhost:1666
7. Enter a valid username and password
8. Press the 'Test Connection' button (you should see Success)
9. Click 'Save' to save.
 
![Perforce Credential](docs/images/2.png)

The Perforce Ticket Credential supports using a ticket file (such as the default P4TICKETS file) or a ticket value (returned by the command p4 login -p).  If Ticket authentication is used for remote builds the Ticket must be valid for the remote host (either login on the remote host or use p4 login -a). 

All Perforce Credential types support SSL for use on Secured Perforce Servers; to use just check the SSL box and provide the Trust fingerprint.

![P4Trust Credential](docs/images/3.png)

## Workspaces

Perforce workspaces are configured on the Jenkin Job configuration page and support the following behaviours:

* Static

The workspace specified must have been previously defined.  The Perforce Jenkins user must either own the workspace or the spec should be unlocked allowing it to make edits.  The workspace View remains static, but Jenkins will update other fields such as the workspace root and clobber option. 

![Static config](docs/images/4.png)

* Spec File

The workspace configuration is loaded from a depot file containing a Client workspace Spec (same output as p4 client -o and the Spec depot '.p4s' format).  The name of the workspace must match the name of the Client workspace Spec.

![Spec config](docs/images/spec.png)

* Manual

This allows the specified workspace to be created (if it does not exist) or update the spec by setting the various options.  Jenkins will fill out the workspace root and may override the clobber option.

![Manual config](docs/images/5.png)

* Template & Stream

In this mode the workspace View is generated using the specified template workspace or stream.  The name of the workspace is generated using the Workspace Name Format field and makes it an ideal choice for matrix builds.

![Stream config](docs/images/6.png)

## Populating

Perforce will populate the workspace with the file revisions needed for the build, the way the workspace is populated is configured on the Jenkins Job configuration page and support the following behaviours:

* Automatic cleanup and sync

Perforce will revert any shelved or pending files from the workspace; this includes the removal of files that were added by the shelved or pending change.  Depending on the two check options boxes Perforce will then clean up any extra files or restore any modified or missing files.  Finally, Perforce will sync the required file revisions to the workspace populating the 'have' table.

![Automatic populate](docs/images/auto_pop.png)

* Forced clean and sync

Perfore will remove all files from under the workspace root, then force sync the required file revisions to the workspace.  If the populating the 'have' table options is enabled then the 'have' list will be updated.  

![Force populate](docs/images/force_pop.png)

This method is not recommended as the cost of IO resources on server and client are high.  Apart from exceptional circumstances the Automatic cleanup and sync option will produce the same result.

* Sync only

Perforce will not attempt to cleanup the workspace; the sync operation will update all files (as CLOBBER is set) to the required set of revisions.  If the populating the 'have' table options is enabled then the 'have' list will be updated.

![Sync populate](docs/images/sync_pop.png)

All populate behaviours have a "Sync build at Perforce Label" field to instruct the workspace to sync only to the specified label. Any other specified change or label will be ignored.

## Building

Building a Jenkins Job can be triggered using the SCM polling option, Build Now button or calling the build/ URL endpoint.

To enable SCM polling, check the 'Poll SCM' option and provide a Schedule using the Cron format.  For example every 10 minutes Monday to Friday, the 'H' is a time offset (calculated using a Hash of the Job name).

![Build trigger](docs/images/trigger.png)

To build immediately select the Build now button...

![Build now](docs/images/now.png)

Or use the call the build/ URL endpoint e.g. http://jenkins_host:8080/job/myJobID/build

(where myJobID is the name of your job and jenkins_host the name or IP address of your Jenkins server).

## Filtering

When polling is used, changes can be filtered to not trigger a build; the filters are configured on the Jenkin Job configuration page and support the following types:

* Exclude changes from user

Changes owned by the Perforce user specified in the filter will be excluded.

![User Filter](docs/images/userF.png)

* Exclude changes from Depot path

Changes where all the file revision's path starting with the String specified in the filter will be excluded.

![Path Filter](docs/images/pathF.png)

For example, with a Filter of "//depot/main/tests":

Case A (change will be filtered):

    Files:
        //depot/main/tests/index.xml
        //depot/main/tests/001/test.xml
        //depot/main/tests/002/test.xml

Case B (change will not be filtered, as build.xml is outside of the filter):

    Files:
        //depot/main/src/build.xml
        //depot/main/tests/004/test.xml
        //depot/main/tests/005/test.xml
 
## Review

The plugin supports a Build Review Action with a review/build/ URL endpoint.  Parameters can be passed informing Jenkins of Perforce shelf to unshelve and changelist to sync to.  There are also Pass/Fail callback URLs for use with Swarm.

An example URL that would build the review in the shelved change 23980:

http://jenkins_host:8080/job/myJobID/review/build?status=shelved&review=23980

The Build Review Action support the following parameters:
* status (shelved or submitted)
* review (the pending shelved change)
* change (the submitted change)
* label (a Perforce label, instead of change)
* pass (URL to call after a build succeeded)
* fail (URL to call after a build failed)

*Please note these paramiter are stored in the Environment and can be used with variable expansion e.g. ${label}; for this reason please avoid these names for slaves and matrix axis.*

The Build Review Action can be invoked manually from within Jenkins by selecting the Build Review button on the left hand side.  This provides a form to specify the parameters for build.

![Build review](docs/images/review.png)

![Build manual](docs/images/manual.png)

## Changes Summary

After a build Jenkins provides the ability to see the details of a build.  Select the build of interest from the Build History on the left hand side.  You then get a Perforce change summary for the build and clicking on the View Detail link for specific files.

![Change summary](docs/images/summaryC.png)

Detailed view...

![Change detail](docs/images/detailC.png)

## Tagging Builds

Jenkins can tag builds automatically as a Post Build Action or allow manual tagging of a build.  The Tags are stored in Perforce as Automatic Labels with the label view based on the workspace at the time of tagging.

Tagging with Post Build Action

![Post Action tag](docs/images/tag.png)

Manual Tagging

* Select the build that you wish to tag from the project page.

![Manual tag](docs/images/manualT.png)

* Click on the 'Label This Build' link on the left hand panel, if the build has already been tagged the link will read 'Perforce Label'.

![Label tag](docs/images/labelT.png)

* Update the label name and description as required and click 'Label Build' to add the label to Perforce.

![Update tag](docs/images/updateT.png)

* Once the build is labeled you will see the label details appear in a table above.  New labels can be added to the same build or labels can be updated by providing the same label name.

## Publishing Build assets

Jenkins can automatically shelve or submit build assets to Perforce.  Select the 'Add post-build action' and select the 'Perforce: Publish assets' from the list.  Select the Credentials and Workspace options, you can connect to a different Perforce server if required.  Update the description if required, ${variables} are expanded.

Shelving with Post Build Action

![Shelve Asset](docs/images/ShelveAsset.png)

Submitting with Post Build Action

![Submit Asset](docs/images/SubmitAsset.png)

## Repository Browsing

Repository browsing allows Jenkins to use an external browser, like Swarm, P4Web, etc... to navigate files and changes associated with a Jenkins build.

To enable the feature select the Repository browser from the Job Configuration page and provide the full URL to the browser.

![Repo list](docs/images/repos.png)

Link to change in Swarm

![Repo list](docs/images/swarm.png)
 
## Release notes

### 2014.2 release

Known limitations:
* One Jenkins Job per Swarm branch
