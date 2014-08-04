package org.ptst.net;

import java.net.*;
import java.io.*;
import java.util.*;

public class LoginHandler
{	
	public static boolean doLogin(URL u, String login, String password)
	{
		try {
			String data = URLEncoder.encode("username", "UTF-8") + "=" + URLEncoder.encode(login, "UTF-8");
			data += "&" + URLEncoder.encode("password", "UTF-8") + "=" + URLEncoder.encode(password, "UTF-8");
			
			HttpURLConnection conn = (HttpURLConnection)u.openConnection();
			conn.setRequestMethod("POST");
			OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
			wr.write(data);
			wr.flush();
			
			if (readCookies(u, conn))
				return true;
		}
		catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return false;
	}
	
	private static boolean readCookies(URL u, HttpURLConnection conn)
	{		
		String cookieValue = null;
		
		CookieHandler handler = CookieHandler.getDefault();
		try {
			if (handler != null) {
				Map<String, List<String>> headers = handler.get(u.toURI(), new HashMap<String, List<String>>());
				List<String> values = headers.get("Cookie");
				for (Iterator<String> it = values.iterator(); it.hasNext();) {
					String v = it.next();
					if (cookieValue == null)
						cookieValue = v;
					else
						cookieValue += ";" + v;
				}
			}
		}
		catch (IOException e) {
			e.printStackTrace();
			return false;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		
		if (cookieValue != null || !cookieValue.trim().equals("")) {
			//use Configuration to write this out to disk
			return true;
		}
		return false;
	}
}