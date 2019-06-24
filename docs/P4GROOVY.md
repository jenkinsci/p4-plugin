_Online documentation for [P4Groovy](https://www.perforce.com/manuals/jenkins/Content/P4Jenkins/pipeline-p4groovy.html)_

# P4Groovy

## Introduction

P4Groovy is a Groovy interface to P4Java that allows you to write Perforce commands in the pipleline DSL.

## Setup

To allow P4Groovy code to be executed uncheck the 'Use Groovy Sandbox' checkbox under the Pipeline script.

The setup P4Groovy create a 'p4' object. For example:

	// Define workspace
	def ws = [$class: 'StreamWorkspaceImpl', 
	charset: 'none', format: 'jenkins-${JOB_NAME}', 
	pinHost: false, streamName: '//streams/projAce']

	// Create object
	def p4 = p4(credential: 'phooey', workspace: ws)

This code can be created for you using the 'p4: P4 Groovy' pipeline syntax snippet generator.

## Running a command

The run command takes a comma separated list of Perforce command and then arguments.

For example:

	p4.run('changes', '-m5', '//depot/path/...')

The command will return an array of Maps (specifically Map<String, Object>[]). For example:

	def changes = p4.run('changes', '-m5', '//depot/path/...')

You can iterate through the response by iterating through each array item and extracting the keys. For example:

	for(def item : changes) {
	        for (String key : item.keySet()) {
			value = item.get(key)
			println ("Key: " + key + " Value: " + value)
		} 
	}

## Working with forms

To retrieve the contents of a form as a map use 'fetch' providing a 'spec type' and 'spec id'. For example:

	def client = p4.fetch('client', 'my_ws')

To save the contents of a form use 'save' providing the 'spec type' and spec as a Map. For example:

	p4.save('client', client)

For example to update a job description:

	def job = p4.fetch('job', 'job000006')
	def desc = job.get('Description')
	desc = desc + env.BUILD_URL
	job.put('Description', desc)
	p4.save('job', job)
	
Adding additional flags to save, e.g. '-f' (do not add '-i' as this is already built into save):

    p4.save('client', client, '-f')

# Getters

P4Groovy provides two custom functions:

	p4.getUserName()   // Get current User name
	p4.getClientName() // Get current workspace/client name


## Known Limitations

* It is not possible to run an interactive resolve with P4Groovy. If a custom resolve is required use 'resolve -n' to display the files that need to be resolved and iterate through the results making changes to the files and resolving as necessary.
