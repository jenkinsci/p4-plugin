# Building Jobs
This section describes how to start a build. 

## Before You Start
The job must be configured, there are a number of options for this. For details about configuring Jobs, see one of the following guides:   
* [FreeStyle setup guide](https://github.com/jenkinsci/p4-plugin/blob/master/SETUP.md)
* [Pipeline setup guide](https://github.com/jenkinsci/p4-plugin/blob/master/WORKFLOW.md)
* [MultiBranch guide](https://github.com/jenkinsci/p4-plugin/blob/master/MULTI.md)

## Manual Build Using Build Now
**Start a build manually:**
1. From the Jenkins dashboard, click the project link. 
2. Click **Build Now** in the sidebar menu. 
The job will start to build. You can see the build progress in **Build History** in the project sidebar. 

**Tip:** To start a build from a URL, right click on the **Build Now** link and save the link. You can now use this link to start the build from another webpage. 

## Manual Build Using P4 Trigger
Jobs can be manually triggered from the Jenkins home page by using **P4 Trigger** in the sidebar menu
Manually trigger a build with **P4 Trigger**:
1. From the Jenkins dashboard, click **P4 Trigger** in the sidebar menu. 
2. Complete the trigger form. 
![P4 Trigger](docs/images/p4Tconfig.png)
3. Click the **Trigger** button.

**Tip:** You can add a **Perforce triggered build** to a Jenkins project in the **Build Triggers** section of the project, see [Perforce Triggered Build](https://github.com/jenkinsci/p4-plugin/blob/master/BUILDTRIGGERPERFORCE.md).  

## Building at a Change
A Jenkins job can build at any point in the codes history, identified by a Perforce Helix Server change or label.

The Jenkins job can be *pinned* to a Perforce Helix Server change, or label by setting the `Pin build at Perforce Label` field under the Populate options. Any time the Jenkins job is triggered, it will only build up to the pinned point.

If you are using downstream jobs (for example) you can use the [Parameterized Trigger Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Parameterized+Trigger+Plugin) to pass `${P4_CHANGELIST}` as the parameter to the downstream job. The downstream job can then pin the build at the passed in changelist so that both upstream and downstream jobs run against the same point in the history of the code.

Alternatively, a change or label can be passed using the `Build Review` parameters or URL end point (see the _Build Review_ section for details)

## Parallel Builds
The plugin supports parallel execution of Jenkins Jobs. Jenkins will create a new workspace directory `workspace@2` and so on. The plugin will automatically template the current workspace appending `.clone2` for the name of the template. 

## Custom Parallel Builds
Used for custom workspaces, where an alternative location has been set. 
**For example:**
_Advanced_ --> _Use custom workspace_ --> _Provide a Directory_. Then you will need to add the executor number to the end of your path.
```
/Users/aclaybourne/Workspaces/custom@${EXECUTOR_NUMBER}
```
The plugin will then correctly template the workspaces as needed. 

## Build Using a Review
The plugin supports a Build Review Action with a `review/build/` URL endpoint. The endpoint enables you to trigger a build from a review. Parameters can be passed informing Jenkins of Perforce Helix shelf to unshelve and the changelist to sync to. There are also Pass/Fail callback URLs for use with Helix Swarm.
**For example:** 
The URL below would build the review in the shelved change 23980:

`http://jenkins_host:8080/job/myJobID/review/build?status=shelved&review=23980`

The Build Review Action supports the following parameters: 
-   status (shelved or submitted)
-   review (the pending shelved change)
-   change (the submitted change)
-   label (a Perforce label, instead of change)
-   pass (URL to call after a build succeeded)
-   fail (URL to call after a build failed)

**Note:** these parameters are stored in the Environment and can be used with variable expansion for example `${label}`. For this reason, please avoid these names for slaves and matrix axis. 
### Review Authorization
If Jenkins requires users to login, your Perforce trigger needs to use a Jenkins authorized account. You can find the `API Token` for the trigger user under:
1. From the Jenkins dashboard, click  **People** in the sidebar menu.
2. Click the Jenkins authorized user account link in the table.
3.  Click **Configure** in the sidebar menu.
4. Click the **Show API Token...** button in the **API Token** section of the page. 
5. Use the API Token with BasicAuth in the URL, for example:
`https://user:0923840952a90898cf90fe0989@jenkins_host:8080/job/myJobID/review/build?status=shelved&review=23980`
### Manual Review
The Build Review Action can be invoked manually from within Jenkins. 
1. From the Jenkins dashboard, click the job name in the table.
2. Click **Build Review** in the sidebar menu.
Complete the form to specify the parameters for build.
![Manual Configuration for Build](docs/images/manual.png)
3. To trigger the build, click the **Build** button. 

## Post Build Options and Viewing the Build Results
When your build is complete you can view the change summary, and manually tag the build. For more information on you post build options, see [Post Build Actions](https://github.com/jenkinsci/p4-plugin/blob/master/POSTBUILD.md).  

