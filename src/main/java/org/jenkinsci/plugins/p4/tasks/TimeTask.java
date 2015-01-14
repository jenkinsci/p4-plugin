package org.jenkinsci.plugins.p4.tasks;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public class TimeTask {

	long start;

	public TimeTask() {
		start = new Date().getTime();
	}

	private long getDuration() {
		long stop = new Date().getTime();
		return stop - start;
	}

	public String toString() {
		if (start == 0) {
			return "Timer not started";
		}

		long ms = getDuration();
		if (ms < 1000) {
			return "(" + ms + "ms)";
		}

		long mins = TimeUnit.MILLISECONDS.toMinutes(ms);
		ms -= TimeUnit.MINUTES.toMillis(mins);
		long secs = TimeUnit.MILLISECONDS.toSeconds(ms);
		return mins + "m " + secs + "s";
	}
}
