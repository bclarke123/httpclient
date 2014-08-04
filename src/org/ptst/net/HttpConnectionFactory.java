package org.ptst.net;

import java.io.IOException;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.ptst.util.CryptoUtils;

public class HttpConnectionFactory {
	private static final int SO_TIMEOUT = 5000;
	
	private static List<HttpConnection> openSockets = 
		Collections.synchronizedList(new ArrayList<HttpConnection>());
	
	public static HttpConnection getConnection(String domain, int port, boolean secure) 
		throws IOException, KeyManagementException, NoSuchAlgorithmException {
		
		HttpConnection result = null;
		
		HttpConnection con = new HttpConnection();
		con.domain = domain;
		con.port = port;
		con.secure = secure;
		
		synchronized(openSockets) {
			for(HttpConnection c : openSockets) {
				if(c.equals(con) && !c.inUse) {
					result = c;
					break;
				}
			}
		}
		
		if(result == null) {
			result = con;
			openSockets.add(result);
		}
		
		result.inUse = true;
		
		updateConnection(result, domain, port, secure);
		
		return result;
	}
	
	private static void updateConnection(HttpConnection result, String domain, int port, boolean secure) 
		throws IOException, KeyManagementException, NoSuchAlgorithmException {
		
		if(result.uses > result.maxUses && result.transport != null) {
//			System.err.println("Socket has become overused: " + result.transport);
			result.transport.close();
		}
		
		if(result.transport == null || result.transport.isClosed()) {
			
			if(secure) {
				result.transport = CryptoUtils.getSocketFactory().createSocket(domain, port);
			} else {
				result.transport = new Socket(domain, port);
			}
			
			result.transport.setSoTimeout(SO_TIMEOUT);
			result.transport.setTcpNoDelay(true);
			result.uses = 0;
			
//			System.out.println(
//				Thread.currentThread().getName() + ": New socket " + result.transport
//			);
		}
		
		setMaxAge(result, result.maxAge);
		result.uses++;
	}
	
	public static void returnConnection(HttpConnection con) {
		con.inUse = false;
	}
	
	public static void closeConnection(HttpConnection con) {
		
		openSockets.remove(con);
		
		int size = openSockets.size();
		System.err.println(
			"Socket " + con.transport.getLocalPort() + 
			" removed (" + size + " open sockets)");
		
		try {
			con.transport.close();
		} catch(Exception e) {
			e.printStackTrace();
			System.out.println("Could not close socket: " + e.getMessage());
		}
		
		if(con.reaper != null) {
			con.reaper.cancel();
		}
	}
	
	public static void setMaxAge(HttpConnection con, int ms) {
		con.maxAge = ms;
		
		if(con.reaper != null) {
			con.reaper.cancel();
			con.reaper.con = null;
		}
		
		con.reaper = new HttpConnectionReaper();
		con.reaper.con = con;
		HttpTimer.schedule(con.reaper, ms);
	}
}
