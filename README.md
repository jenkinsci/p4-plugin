# p4-jenkins

## Requirements

* Jenkins 1.509 or greater.
* P4D 12.1 or greater. (download)
* Minimum Perforce Protection of 'open' for the Jenkins user. 
If you wish to use the Review Build feature you will need Swarm.  Swarm 2014.2 or greater is required to support Jenkins authentication.

## Building

The Perforce plugin requires p4-java; however at the time of writing this is not in Maven Central.  The jar is shipped in the root directory, so to add it into your local maven repo run the following command:

  mvn install:install-file \
    -Dfile=p4java-2013.2.788582.jar \ 
    -DgroupId=com.perforce \ 
    -DartifactId=p4java \ 
    -Dversion=2013.2.788582 \ 
    -Dpackaging=jar
    
To build the plugin and run the tests use the following:

  mvn package
  
Note: for the tests to run you must have p4d in your PATH, to skip tests use the -DskipTests flag.

## Installing

1. Open Jenkins in a browser; e.g. http://jenkins_host:8080
2. Browse to 'Manage Jenkins' --> 'Manage Plugins’ and Select the 'Advanced' tab.
3. Press the 'Choose File' button under the 'Upload Plugin' section
4. Find the location of the 'p4-client.hpi' file and Select Upload
5. Choose the 'Download now and install after restart' button (this might be different on the newer version)

## Credentials

The plugin makes use of the Jenkins Credential store making it easier to manage the Perforce Server connection for multiple Jenkins jobs.  Perforce Server credentials must be added to the Global or a user defined domain, using one of the two supported Perforce Credentials: 'Perforce Password Credential' or 'Perforce Ticket Credential'.

![Global credentials][docs/images/1.png]

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
 
![alt text][todo]

The Perforce Ticket Credential supports using a ticket file (such as the default P4TICKETS file) or a ticket value (returned by the command p4 login -p).  If Ticket authentication is used for remote builds the Ticket must be valid for the remote host (either login on the remote host or use p4 login -a). 

All Perforce Credential types support SSL for use on Secured Perforce Servers; to use just check the SSL box and provide the Trust fingerprint.

![alt text][todo] 

## Workspaces

Perforce workspaces are configured on the Jenkin Job configuration page and support the following behaviours:

* Static

... The workspace specified must have been previously defined.  The Perforce Jenkins user must either own the workspace or the spec should be unlocked allowing it to make edits.  The workspace View remains static, but Jenkins will update other fields such as the workspace root and clobber option. 

... ![alt text][todo]

* Spec File

... The workspace configuration is loaded from a depot file containing a Client workspace Spec (same output as p4 client -o and the Spec depot '.p4s' format).  The name of the workspace must match the name of the Client workspace Spec.

... ![alt text][todo]

* Manual

... This allows the specified workspace to be created (if it does not exist) or update the spec by setting the various options.  Jenkins will fill out the workspace root and may override the clobber option.

... ![alt text][todo]

* Template & Stream

... In this mode the workspace View is generated using the specified template workspace or stream.  The name of the workspace is generated using the Workspace Name Format field and makes it an ideal choice for matrix builds.

... ![alt text][todo]

## Populating

Perforce will populate the workspace with the file revisions needed for the build, the way the workspace is populated is configured on the Jenkin Job configuration page and support the following behaviours:

* Automatic cleanup and sync

... Perforce will revert any shelved or pending files from the workspace; this includes the removal of files that were added by the shelved or pending change.  Depending on the two check options boxes Perforce will then clean up any extra files or restore any modified or missing files.  Finally, Perforce will sync the required file revisions to the workspace populating the 'have' table.

... ![alt text][todo]

* Forced clean and sync

... Perfore will remove all files from under the workspace root, then force sync the required file revisions to the workspace.  If the populating the 'have' table options is enabled then the 'have' list will be updated.  

... ![alt text][todo]

... This method is not recommended as the cost of IO resources on server and client are high.  Apart from exceptional circumstances the Automatic cleanup and sync option will produce the same result.

* Sync only

... Perforce will not attempt to cleanup the workspace; the sync operation will update all files (as CLOBBER is set) to the required set of revisions.  If the populating the 'have' table options is enabled then the 'have' list will be updated.

... ![alt text][todo]

## Building

Building a Jenkins Job can be triggered using the SCM polling option, Build Now button or calling the build/ URL endpoint.

To enable SCM polling, check the 'Poll SCM' option and provide a Schedule using the Cron format.  For example every 10 minutes Monday to Friday, the 'H' is a time offset (calculated using a Hash of the Job name).

... ![alt text][todo]

To build immediately select the Build now button...

... ![alt text][todo]

Or use the call the build/ URL endpoint e.g. http://jenkins_host:8080/job/myJobID/build

(where myJobID is the name of your job and jenkins_host the name or IP address of your Jenkins server).

## Filtering

When polling is used, changes can be filtered to not trigger a build; the filters are configured on the Jenkin Job configuration page and support the following types:

* Exclude changes from user

... Changes owned by the Perforce user specified in the filter will be excluded.

... ![alt text][todo]

* Exclude changes from Depot path

... Changes where all the file revision's path starting with the String specified in the filter will be excluded.

... ![alt text][todo]

... For example, with a Filter of "//depot/main/tests":

... Case A (change will be filtered):
  Files:
    //depot/main/tests/index.xml
    //depot/main/tests/001/test.xml
    //depot/main/tests/002/test.xml
    
... Case B (change will not be filtered, as build.xml is outside of the filter):
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

The Build Review Action can be invoked manually from within Jenkins by selecting the Build Review button on the left hand side.  This provides a form to specify the parameters for build.

![alt text][todo]

![alt text][todo]

## Changes Summary

After a build Jenkins provides the ability to see the details of a build.  Select the build of interest from the Build History on the left hand side.  You then get a Perforce change summary for the build and clicking on the View Detail link for specific files.

![alt text][todo]

Detailed view...

![alt text][todo]

## Tagging Builds

Jenkins can tag builds automatically as a Post Build Action or allow manual tagging of a build.  The Tags are stored in Perforce as Automatic Labels with the label view based on the workspace at the time of tagging.

Tagging with Post Build Action

Manual Tagging

* Select the build that you wish to tag from the project page.

![alt text][todo]

* Click on the 'Label This Build' link on the left hand panel, if the build has already been tagged the link will read 'Perforce Label'.

![alt text][todo]

* Update the label name and description as required and click 'Label Build' to add the label to Perforce.

![alt text][todo]

* Once the build is labeled you will see the label details appear in a table above.  New labels can be added to the same build or labels can be updated by providing the same label name.

## Repository Browsing

Repository browsing allows Jenkins to use an external browser, like Swarm, P4Web, etc... to navigate files and changes associated with a Jenkins build.

To enable the feature select the Repository browser from the Job Configuration page and provide the full URL to the browser.

![alt text][todo]

Link to change in Swarm

![alt text][todo]
 
## Release notes

### 2014.2 release

Known limitations:
* One Jenkins Job per Swarm branch
