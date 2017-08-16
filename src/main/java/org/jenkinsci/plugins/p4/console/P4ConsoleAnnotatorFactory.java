package org.jenkinsci.plugins.p4.console;

import hudson.Extension;
import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleAnnotatorFactory;
import org.jenkinsci.Symbol;

@Extension
@Symbol("annotator")
public class P4ConsoleAnnotatorFactory extends ConsoleAnnotatorFactory<Object> {

	@Override
	public ConsoleAnnotator<Object> newInstance(Object context) {

		return new P4ConsoleAnnotator();
	}

}
