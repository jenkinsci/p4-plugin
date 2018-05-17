# Perforce Triggered Build
Perforce can trigger Jenkins to build based on an event, such as a submitted change. 
To enable builds to be triggered by Perforce, select **Perforce triggered build** in the Freestyle job page. 
A triggered build also requires an administrator to add a Perforce trigger to the Perforce server. For information about adding a trigger, see [Using triggers to customize behavior](https://www.perforce.com/perforce/doc.current/manuals/p4sag/chapter.scripting.html) in [Helix Core Server Administrator Guide: Fundamentals](https://www.perforce.com/perforce/doc.current/manuals/p4sag/index.html#P4SAG/about.html).
The trigger needs to POST a JSON payload to the Jenkins end-point  `p4/change/`. The JSON payload must contain the  `p4port`  string that matchs the P4Port field specified in the **Perforce Credential** (please note that the field  `change`  is not currently used, it has been added for future compatibility).
**For example:**
- A simple  `change-commit`  or  `graph-push-complete`  trigger can use curl:
```
#!/bin/bash
CHANGE=$1

P4PORT=perforce:1666
JUSER=admin
JPASS=pass
JSERVER=http://localhost:8080

curl --header 'Content-Type: application/json' \
     --request POST \
     --silent \
     --user $JUSER:$JPASS \
     --data payload="{change:$CHANGE,p4port:\"$P4PORT\"}" \
     $JSERVER/p4/change
```
- It must  have an entry in  `p4 triggers`  for changes on  `//depot/...`:
```
jenkins    change-commit        //depot/...   "/p4/common/bin/triggers/jdepot.sh %change%"
```
- or for Graph content:
```
helix4git  graph-push-complete  //repos/...   "/p4/common/bin/triggers/jgraph.sh %depotName% %repoName% %pusher%"
```
## Server Authentication
If your Jenkins server needs authentication you will also need to provide a security `CRUMB`. The following is an example of how you can get this and use it to trigger a job:
```
#!/bin/bash
CHANGE=$1

P4PORT=perforce:1666
JUSER=admin
JPASS=pass
JSERVER=http://localhost:8080

# Get CRUMB
CRUMB=$(curl --silent --user $JUSER:$JPASS $JSERVER/crumbIssuer/api/xml?xpath=concat\(//crumbRequestField,%22":"%22,//crumb\))

# Trigger builds across all triggered jobs (where relevant)
curl --header "$CRUMB" \
     --request POST \
     --silent \
     --user $JUSER:$JPASS \
     --data payload="{change:$CHANGE,p4port:\"$P4PORT\"}" \
     $JSERVER/p4/change
```

Click the browser **Back** button to go back to the previous page. 
