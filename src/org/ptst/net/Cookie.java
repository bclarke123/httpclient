package org.ptst.net;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimerTask;

public class Cookie extends TimerTask implements Serializable {

	public static final long serialVersionUID = 1;
	
	private static final String PATTERN_RFC1123 = "EEE, dd MMM yyyy HH:mm:ss zzz";
	private static final String PATTERN_RFC1036 = "EEEE, dd-MMM-yy HH:mm:ss zzz";
	private static final String PATTERN_ASCTIME = "EEE MMM d HH:mm:ss yyyy";
	
	private static final String[] DEFAULT_PATTERNS = {
		PATTERN_RFC1123,
		PATTERN_RFC1036,
		PATTERN_ASCTIME
	};
	
	private Map<String, String> values = new HashMap<String, String>();
	private String name, value;
	private String domain;
	private Date expires;

	public String getDomain() {
		return domain;
	}

	public Date getExpires() {
		return expires;
	}

	public void setExpires(Date expires) {
		this.expires = expires;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}

	private Cookie() {}
	
	public static Cookie parseSetCookieHeader(String headerValue) {
		Cookie result = new Cookie();
		String[] pairs = headerValue.split("; ");
		boolean first = true;
		for(String pair : pairs) {
			if(pair.indexOf("=") == -1) {
				result.values.put(pair, "true"); // secure / httponly
			} else {
				String name = pair.substring(0, pair.indexOf("="));
				String value = pair.substring(pair.indexOf("=") + 1);
				result.values.put(name, value);
				if(first) {
					result.name = name;
					result.value = value;
				}
			}
			first = false;
		}
		
		result.parseExpiry();
		
		return result;
	}
	
	private void parseExpiry() {
		String strExpires = values.get("expires");
		if(strExpires != null) {
			for(String pattern : DEFAULT_PATTERNS) {
				SimpleDateFormat fmt = new SimpleDateFormat(pattern);
				try {
					expires = fmt.parse(strExpires);
					return;
				} catch(Exception e) {}
			}
		}
	}

	public String getName() {
		return name;
	}

	public String getValue() {
		return value;
	}

	public Map<String, String> getProperties() {
		return values;
	}
	
	public String getProperty(String name) {
		return this.values.get(name);
	}
	
	public String toString() {
		StringBuffer buf = new StringBuffer();
		buf.append(this.name);
		buf.append("=");
		buf.append(this.value);
		return buf.toString();
	}
	
	@Override
	public void run() {
		System.out.println("Closing cookie " + name + " for domain " + domain);
		CookieManager.unregisterCookie(this);
	}
	
}
