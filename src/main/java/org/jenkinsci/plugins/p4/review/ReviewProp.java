package org.jenkinsci.plugins.p4.review;

public enum ReviewProp {

	STATUS("status"), 
	CHANGE("change"), 
	LABEL("label"), 
	REVIEW("review"), 
	PASS("pass"), 
	FAIL("fail");

	public static String NAMESPACE = "p4.";
	final private String prop;

	ReviewProp(String prop) {
		this.prop = prop;
	}

	public String toString() {
		return NAMESPACE + prop;
	}
	
	public String getProp() {
		return prop;
	}
	
	public static boolean isProp(String prop) {
		for(ReviewProp p : ReviewProp.values()) {
			if(p.getProp().equals(prop))
				return true;
		}
		return false;
	}
}
