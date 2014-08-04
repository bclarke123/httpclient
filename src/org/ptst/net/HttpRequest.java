package org.ptst.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class HttpRequest {
	private static final String HTTP_REQUEST = "%s %s HTTP/1.1";
	private static final String EOL = "\r\n";
	
	private static enum DefaultHeader {
		UserAgent 		("User-Agent", 		"Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US; rv:1.8.1.11) Gecko/20071127 Firefox/2.0.0.11"),
		Accept			("Accept", 			"text/xml,application/xml,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5"),
		AcceptLanguage	("Accept-Language", "en-us,en;q=0.5"),
		AcceptEncoding	("Accept-Encoding", "gzip,deflate"),
		AcceptCharSet	("Accept-Charset", 	"ISO-8859-1,utf-8;q=0.7,*;q=0.7"),
		KeepAlive		("Keep-Alive",		String.valueOf(HttpConnection.KEEP_ALIVE_SECS)),
		Connection		("Connection",		"keep-alive");
		
		String name, value;
		
		DefaultHeader(String name, String value) {
			this.name = name;
			this.value = value;
		}
	}
	
	protected URL url;
	protected Map<String, String> headers = new HashMap<String, String>();
	
	protected HttpRequest(URL url) {
		this.url = url;
	}

	protected abstract String method();
	
	protected void writeRequestBody(OutputStream out) throws IOException {}
	
	public void setHeader(String name, String value) {
		headers.put(name, value);
	}
	
	private void writeHeaders(PrintWriter writer, int port) {
		List<String> used = new ArrayList<String>();
		DefaultHeader[] defaultHeaders = DefaultHeader.values();
		
		String host = url.getHost();
		
		if(port != url.getDefaultPort()) {
			host += ":" + port;
		}
		writer.printf("Host: %s%s", host, EOL);
		
		for(DefaultHeader header : defaultHeaders) {
			String value = header.value;
			if(headers.get(header.name) != null) {
				value = headers.get(header.name);
			}
			writer.printf("%s: %s%s", header.name, value, EOL);
			used.add(header.name);
		}
		
		List<Cookie> cookies = CookieManager.getCookiesForDomain(url.getHost());
		for(Cookie cookie : cookies) {
			writer.printf("Cookie: %s%s", cookie.toString(), EOL);
		}
		
		Set<String> headerKeys = headers.keySet();
		for(String key : headerKeys) {
			if(!used.contains(key)) {
				String value = headers.get(key);
				writer.printf("%s: %s%s", key, value, EOL);
			}
		}
	}
	
	private static void parseKeepAlive(HttpResponse response, HttpConnection con) throws IOException {
		if("close".equalsIgnoreCase(response.getHeader("Connection"))) {
			HttpConnectionFactory.closeConnection(con);
			return;
		}
		
		String keepAlive = response.getHeader("Keep-Alive");
		if(keepAlive != null) {
			String[] pairs = keepAlive.split(", ");
			for(String pair : pairs) {
				String[] kv = pair.split("=");
				if("timeout".equals(kv[0])) {
					try {
						HttpConnectionFactory.setMaxAge(con, Integer.parseInt(kv[1]) * 1000);
//						System.err.println("Max age is now " + con.maxAge);
					} catch(Exception e) {
						System.out.println("Found bad timeout in keepalive: " + pair);
					}
				} else
				if("max".equals(kv[0])) {
					try {
						con.maxUses = Integer.parseInt(kv[1]);
//						System.err.println("Max uses is now " + con.maxUses);
					} catch(Exception e) {
						System.out.println("Found bad max in keepalive: " + pair);
					}
				}
			}
		}
	}
	
	public HttpResponse doRequest() throws IOException, KeyManagementException, NoSuchAlgorithmException {
		
		if(url == null) {
			throw new IOException("Request url was not specified");
		}
		
		String method = method();
		if(method == null) {
			throw new IOException("Http method was not specified");
		}

		HttpResponse response = null;
		
		boolean secure = "https".equalsIgnoreCase(url.getProtocol());
		int port = (url.getPort() == -1 ? url.getDefaultPort() : url.getPort());
		
		HttpConnection con = 
			HttpConnectionFactory.getConnection(url.getHost(), port, secure);
		Socket requestSocket = con.transport;
		
		try {
		
			OutputStream out = requestSocket.getOutputStream();
			PrintWriter writer = new PrintWriter(out);
			InputStream in = requestSocket.getInputStream();
	
			String file;
			if(url.getFile() != null && url.getFile().length() > 0) {
				file = url.getFile();
			} else {
				file = "/";
			}
			
			writer.printf(HTTP_REQUEST + EOL, method, file);

			writeHeaders(writer, port);
			writer.print(EOL);
			
			writer.flush();
			
			writeRequestBody(out);
			out.flush();
			
			response = HttpResponse.parse(url.getHost(), in);
			
			parseKeepAlive(response, con);
		
		} finally {
			HttpConnectionFactory.returnConnection(con);
		}
			
		return response;
	}
}
