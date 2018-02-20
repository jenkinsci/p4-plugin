package org.jenkinsci.plugins.p4.PerforceScm;

def l = namespace(lib.JenkinsTagLib)

["P4_CHANGELIST","P4_CLIENT","P4_PORT","P4_USER","P4_TICKET","P4_REVIEW","P4_REVIEW_TYPE"].each { name ->
    l.buildEnvVar(name:name) {
        raw(_("${name}.blurb"))
    }
}