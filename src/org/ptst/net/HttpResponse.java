package org.ptst.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

public class HttpResponse {

	private static final boolean DEBUG = true;
	
	private static final int MAX_LINE_LENGTH = 65536;
	private static final int MAX_HEADERS = 256;
	private static final int MAX_CHUNKS = 10;
	private static final int BUFFER_SIZE = 1024;
	private static final int DISK_CACHE_CUTOFF = 1048576;
	private static final Pattern STATUS_PATTERN = Pattern.compile("^HTTP/1\\.[0|1]\\s(\\d+)\\s(.*)$");
	
	private Map<String, String> headers = new HashMap<String, String>();
	private int status;
	private int contentLength;
	private String statusLine;
	private String statusMessage;
	private byte[] responseBody;
	private File responseFile;
	
	private HttpResponse() {}
	
	public static HttpResponse parse(String host, InputStream in) throws IOException {

//		System.out.println(Thread.currentThread().getName() + ": HttpResponse.parse()");
		
		HttpResponse response = new HttpResponse();
		response.statusLine = readLine(in);
		
		Matcher matcher = STATUS_PATTERN.matcher(response.statusLine);
		if(matcher.find()) {
			response.status = Integer.parseInt(matcher.group(1));
			response.statusMessage = matcher.group(2);
		}
		
		String header = null;
		String name, value;
		int i=0, len=0;
		do {
			header = readLine(in);
			if(header != null) {
				
				if(header.indexOf(':') == -1) {
					throw new IOException("Invalid HTTP response header found: " + header);
				}
				
				debug(header);
				
				name = header.substring(0, header.indexOf(": "));
				value = header.substring(header.indexOf(": ") + 2);
				
				response.headers.put(name, value);
				
				if("Set-Cookie".equals(name)) {
					Cookie cookie = Cookie.parseSetCookieHeader(value);
					String domain = host;
					if(cookie.getProperty("domain") != null) {
						domain = cookie.getProperty("domain");
					}
					cookie.setDomain(domain);
					CookieManager.registerCookie(cookie);
				}
				
			}
		} while(header != null && i++ < MAX_HEADERS);
		
		len = response.contentLength = response.getIntHeader("Content-Length");
		if(len == 0) { // we're done here
			return response;
		}
		
		OutputStream out;
		boolean bufferToFile = len == -1 || len > DISK_CACHE_CUTOFF;
		
		if(bufferToFile) {
			
			response.responseFile = File.createTempFile("tst", ".bin");
			response.responseFile.deleteOnExit();
			
			out = new FileOutputStream(response.responseFile);
			
		} else {
			
			out = new ByteArrayOutputStream();
			
		}
		
		if("chunked".equalsIgnoreCase(response.getHeader("Transfer-Encoding"))) {
			
			i = len = 0;
			int hex;
			String strHex;
			
			do {
				
				strHex = readLine(in).trim();
				hex = Integer.parseInt(strHex, 16);
				
//				debug("Reading " + hex + " bytes in this chunk");
				len += readBody(in, out, hex);
				
				if(hex > 0) {
					readLine(in); // there's a blank line before the content
				}
				
			} while(hex > 0 && i++ < MAX_CHUNKS);
			
			response.contentLength = len;
			
		} else {
			
			readBody(in, out, len);
			
// this is sort of ugly, but meh
			if(!bufferToFile) { 
				response.responseBody = ((ByteArrayOutputStream)out).toByteArray();
			}
		}
		
		return response;
	}
	
	private static String readLine(InputStream in) throws IOException {
		StringBuffer buf = new StringBuffer();
		
		int i=0;
		char c;
		while((c = (char)(in.read() & 0xFF)) != '\n' && c != -1 && i++ < MAX_LINE_LENGTH) {
			if(c == '\r') { continue; }
			buf.append(c);
		}
		
		if(i > 1) {
			return buf.toString();
		} else {
			return null;
		}
	}
	
	private static int readBody(InputStream in, OutputStream out, int len) throws IOException {
		
		int n, x = BUFFER_SIZE, i=0;
		byte[] buf = new byte[BUFFER_SIZE];
		
		while(i < len && (n = in.read(buf, 0, x)) > 0) {
			out.write(buf, 0, n);
			x = Math.min(BUFFER_SIZE, len - (i += n));
		}
		
		return i;
	}

	public int getStatus() {
		return status;
	}

	public String getStatusLine() {
		return statusLine;
	}

	public String getHeader(String name) {
		return headers.get(name);
	}
	
	public Set<String> getHeaderNames() {
		return headers.keySet();
	}
	
	public int getIntHeader(String name) {
		int result = -1;
		try {
			result = Integer.parseInt(headers.get(name));
		} catch(Exception e) {}
		return result;
	}

	public String getStatusMessage() {
		return statusMessage;
	}

	public int getContentLength() {
		return contentLength;
	}

/**
 * Return the response body as a String
 * WARNING:  If you call this method on
 * a very large response, it will load
 * the whole thing into memory, which
 * is almost certainly not what you want.
 * Use getResponseBodyAsStream for large
 * responses, where it will be streamed 
 * from disk.
 * 
 * @return the http response body
 */
	public String getResponseBodyAsString() throws IOException, FileNotFoundException {
		return new String(getResponseBody());
	}
	
/**
 * Return the response body as a byte array
 * WARNING:  If you call this method on
 * a very large response, it will load
 * the whole thing into memory, which
 * is almost certainly not what you want.
 * Use getResponseBodyAsStream for large
 * responses, where it will be streamed 
 * from disk.
 * 
 * @return the http response body
 */
	public byte[] getResponseBody() throws FileNotFoundException, IOException {
		
		String encoding = headers.get("Content-Encoding");
		if(responseFile == null && encoding == null) {
			return responseBody;
		}
		
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		InputStream in;
		
		if(responseFile != null) {
			in = new FileInputStream(responseFile);
		} else {
			in = new ByteArrayInputStream(responseBody);
		}
		
		if("gzip".equals(encoding)) {
			in = new GZIPInputStream(in);
		} else 
		if("deflate".equals(encoding)) {
			in = new ZipInputStream(in);
		}
		
		try {
			int n;
			byte[] buf = new byte[1024];
			while((n = in.read(buf)) > 0) {
				out.write(buf, 0, n);
			}
			return out.toByteArray();
		} finally {
			in.close();
		}
	}
	
	public InputStream getResponseBodyAsStream() throws FileNotFoundException, IOException {
		
		String encoding = headers.get("Content-Encoding");
		InputStream in;
		
		if(responseFile != null) {
			in = new FileInputStream(responseFile);
		} else {
			in = new ByteArrayInputStream(responseBody);
		}
		
		if("gzip".equals(encoding)) {
			in = new GZIPInputStream(in);
		} else 
		if("deflate".equals(encoding)) {
			in = new ZipInputStream(in);
		}
		
		return in;
		
	}
	
	private static void debug(String message) {
		if(DEBUG) {
			System.out.println(message);
		}
	}
}
