package org.ptst.net;

import java.util.TimerTask;

public class HttpConnectionReaper extends TimerTask {
	HttpConnection con;
	
	public void run() {
		
		System.out.println("ConnectionReaper closing timed out socket " + con.transport);
		
		HttpConnectionFactory.closeConnection(con);
		
		con = null;
	}
}
