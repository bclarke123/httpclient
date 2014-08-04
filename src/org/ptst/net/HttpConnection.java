package org.ptst.net;

import java.net.Socket;

public class HttpConnection {
	
	public static final int KEEP_ALIVE_SECS = 300;
	public static final int MAX_USES = 100;
	
	HttpConnectionReaper reaper;
	Socket transport;

	String domain;
	
	boolean inUse;
	boolean secure;
	
	int maxAge = KEEP_ALIVE_SECS * 1000;
	int maxUses = MAX_USES;
	int uses = 0;
	int port;
	
	public boolean equals(Object obj) {
		if(obj == null) { return false; }
		if(!(obj instanceof HttpConnection)) { return false; }
		
		HttpConnection con = (HttpConnection)obj;
		
		if(this.secure != con.secure) {
			return false;
		}
		
		return this.domain.equals(con.domain) && this.port == con.port;
	}
	
	public int hashCode() {
		return this.port;
	}
	
	public String toString() {
		return this.domain + ":" + this.port + " (" + this.inUse + ")";
	}
}
