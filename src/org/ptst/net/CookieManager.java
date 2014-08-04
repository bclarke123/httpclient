package org.ptst.net;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ptst.util.Configuration;
import org.ptst.util.CryptoUtils;

public class CookieManager {
	private static Map<String, Map<String, Cookie>> cookies = 
		Collections.synchronizedMap(new HashMap<String, Map<String, Cookie>>());
	
	public static void registerCookie(Cookie cookie) {
		String domain = cookie.getDomain();
		Map<String, Cookie> list = cookies.get(domain);
		if(list == null) {
			cookies.put(domain, list = new HashMap<String, Cookie>());
		}

		Cookie existing = list.get(cookie.getName());
		if(existing != null) {
			existing.cancel();
		}
		
		list.put(cookie.getName(), cookie);
		
		Date date = cookie.getExpires();
		if(date != null) {
			HttpTimer.schedule(cookie, cookie.getExpires());
		}
		
		persistCookie(cookie);
		
//		System.out.println("Added cookie " + cookie.getName() + " for domain " + domain);
	}
	
	public static List<Cookie> getCookiesForDomain(String domain) {
		List<Cookie> result = new ArrayList<Cookie>();
		
		synchronized(cookies) {
			Set<String> domains = cookies.keySet();
			Map<String, Cookie> thisDomain;
			for(String aDomain : domains) {
				if(domain.endsWith(aDomain)) {
					thisDomain = cookies.get(aDomain);
					result.addAll(thisDomain.values());
				}
			}
		}
		
//		System.out.println("Found " + result.size() + " cookies for domain " + domain);
		
		return result;
	}
	
	private static void persistCookie(Cookie cookie) {
		
//		char sep = File.separatorChar;
//		String name = CryptoUtils.hexDigest((cookie.getDomain() + "::" + cookie.getName()).getBytes());
//		File file = new File(Configuration.getCookieDir() + sep + name + ".bin");
//		
//		FileOutputStream out = null;
//		try {
//			out = new FileOutputStream(file);
//			byte[] data = CryptoUtils.encryptObject(cookie);
//			out.write(data);
//		} catch(Exception e) {
//			e.printStackTrace();
//			//TODO show an error message
//		} finally {
//			if(out != null) {
//				try { out.close(); } catch(Exception e) {}
//			}
//		}
	}
	
	public static void unregisterCookie(Cookie cookie) {
		String domain = cookie.getDomain();
		String name = cookie.getName();
		
		Map<String, Cookie> thisDomain = cookies.get(domain);
		if(thisDomain != null) {
			thisDomain.remove(name);
		}
	}
	
	public static void preCacheCookies() {
		File file = new File(Configuration.getCookieDir());
		FilenameFilter filter = new FilenameFilter() {
			public boolean accept(File dir, String file) {
				return file.endsWith(".bin");
			}
		};
		File[] serializedCookies = file.listFiles(filter);
		ByteArrayOutputStream bout;
		FileInputStream in;
		Cookie cookie;
		byte[] buf = new byte[1024];
		int n;
		try {
			for(File aFile : serializedCookies) {
				System.out.println("Loading " + aFile.getAbsolutePath());
				in = new FileInputStream(aFile);
				bout = new ByteArrayOutputStream();
				while((n = in.read(buf)) > 0) {
					bout.write(buf, 0, n);
				}
				cookie = (Cookie)CryptoUtils.decryptObject(bout.toByteArray());
				registerCookie(cookie);
			}
		} catch(Exception e) {
			e.printStackTrace();
			//TODO show error message
		}
	}
}
