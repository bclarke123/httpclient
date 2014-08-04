package org.ptst.net;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class HttpTimer {
	private static Timer timer = new Timer(true);
	
	public static void schedule(TimerTask task, long delay) {
		timer.schedule(task, delay);
	}
	
	public static void schedule(TimerTask task, long delay, long period) {
		timer.schedule(task, delay, period);
	}
	
	public static void schedule(TimerTask task, Date when) {
		timer.schedule(task, when);
	}
	
	public static void schedule(TimerTask task, Date start, long period) {
		timer.schedule(task, start, period);
	}
}
