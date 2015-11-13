# Perforce plugin for Jenkins (p4-plugin)

## Contents

* [Release notes](RELEASE.md)
* [Setup guide](SETUP.md)
* [Jenkins page](https://wiki.jenkins-ci.org/display/JENKINS/P4+Plugin)

## Requirements

* Jenkins 1.509 or greater.
* P4D 2012.1 or greater.
* Minimum Perforce Protection of `open` for the Jenkins user.
* Review Build feature requires Swarm 2014.2 or greater.

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

Lastest SNAPSHOT builds available [here](https://jenkins.ci.cloudbees.com/job/plugins/job/p4-plugin/lastBuild/org.jenkins-ci.plugins$p4/).

## Manual install

1. Open Jenkins in a browser; e.g. http://jenkins_host:8080
2. Browse to 'Manage Jenkins' --> 'Manage Plugins’ and Select the 'Advanced' tab.
3. Press the 'Choose File' button under the 'Upload Plugin' section
4. Find the location of the 'p4.hpi' file and Select Upload
5. Choose the 'Download now and install after restart' button (this might be different on the newer version)

