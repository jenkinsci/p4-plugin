package org.jenkinsci.plugins.p4.PerforceScm;

def stream = getClass().getResourceAsStream("buildEnv.properties")
def l = namespace(lib.JenkinsTagLib)
def orderedProps = new LinkedHashMap()

if (stream != null) {
    stream.eachLine { line ->
        line = line.trim()
        if (!line || line.startsWith("#")) return  // Skip empty or commented lines
        def parts = line.split("=", 2)
        if (parts.length == 2 && parts[0].endsWith(".blurb")) {
            orderedProps.put(parts[0], parts[1])
            //orderedProps[parts[0]] = parts[1]
        }
    }

    orderedProps.each { key, value ->
        def name = key.replace(".blurb", "")
        l.buildEnvVar(name: name) {
            raw(value ?: "No description available")
        }
    }
}
