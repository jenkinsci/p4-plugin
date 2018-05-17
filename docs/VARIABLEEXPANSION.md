# Variable Expansion
Many of the workspace fields can include environment variables to help define their value. 
**For example:** we recommend that the **Workspace name** uses at least the following variables to help identify your builds:
```
jenkins-${NODE_NAME}-${JOB_NAME}-${EXECUTOR_NUMBER}
```
- `NODE_NAME` - Name of the "slave" or "master" the job is built on, such as linux.
- `JOB_NAME` - Name of the project of this build, such as "foo"
- `EXECUTOR_NUMBER` - The unique number that identifies the current executor.
If the job is called 'foo' and it is built on a slave called 'linux' the variables expand the name to:
```
jenkins-linux-foo
```
## Built in environment variables
Jenkins provides a set of environment variables. You can also define your own. Here is a list of built in environment variables:
`BUILD_NUMBER`  - The current build number. For example "153"  
`BUILD_ID`  - The current build id. For example "2018-08-22_23-59-59"  
`BUILD_DISPLAY_NAME`  - The name of the current build. For example "#153".  
`JOB_NAME`  - Name of the project of this build. For example "foo"  
`BUILD_TAG`  - String of "jenkins-${JOB_NAME}-${BUILD_NUMBER}".  
`EXECUTOR_NUMBER`  - The unique number that identifies the current executor.  
`NODE_NAME`  - Name of the "slave" or "master". For example "linux".  
`NODE_LABELS`  - Whitespace-separated list of labels that the node is assigned.  
`WORKSPACE`  - Absolute path of the build as a workspace.  
`JENKINS_HOME`  - Absolute path on the master node for Jenkins to store data.  
`JENKINS_URL`  - URL of Jenkins. For example [http://server:port/jenkins/](http://server:port/jenkins/)  
`BUILD_URL`  - Full URL of this build. For example  [http://server:port/jenkins/job/foo/15/](http://server:port/jenkins/job/foo/15/)  
`JOB_URL`  - Full URL of this job. For example [http://server:port/jenkins/job/foo/](http://server:port/jenkins/job/foo/)

## Variables in workspace view and stream paths
The plugin allows the use of environment variables in fields like **Workspace view** and **Stream path**. For example:
```
//depot/main/proj/... //jenkins-${NODE_NAME}-${JOB_NAME}-${EXECUTOR_NUMBER}/...
```
## Define your own variables
With a Matrix build you might have defined your own variables like  `${OS}`. Remember they can be used anywhere in the mapping:
```
//depot/main/${JOB_NAME}/bin.${OS}/... //jenkins-${NODE_NAME}-${JOB_NAME}-${EXECUTOR_NUMBER}-${OS}/bin/${OS}/... 
```
Click the browser **Back** button to go back to the previous page. 
