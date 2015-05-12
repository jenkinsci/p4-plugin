package org.jenkinsci.plugins.p4.credentials;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsScope;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public class P4Credentials implements Credentials {

	private static final long serialVersionUID = 1L;

	@CheckForNull
	private final CredentialsScope scope;

    protected P4Credentials() {
        this.scope = null;
    }
    
	/**
	 * Create instance with specified scope.
	 * 
	 * @param scope
	 */
	public P4Credentials(@CheckForNull CredentialsScope scope) {
		this.scope = scope;
	}

	@CheckForNull
	public CredentialsScope getScope() {
		return scope;
	}

	@NonNull
	public CredentialsDescriptor getDescriptor() {
		return (CredentialsDescriptor) Jenkins.getInstance().getDescriptorOrDie(
				getClass());
	}

}
