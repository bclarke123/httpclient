package org.ptst.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

public class PostRequest extends HttpRequest {
	private String contentType;
	private int contentLength = -1;
	private InputStream requestBodyStream;
	private byte[] requestBody;
	
	public PostRequest(URL url) {
		super(url);
	}

	public int getContentLength() {
		return contentLength;
	}

	public void setContentLength(int contentLength) {
		this.contentLength = contentLength;
	}

	public String getContentType() {
		return contentType;
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	public byte[] getRequestBody() {
		return requestBody;
	}

	public void setRequestBody(byte[] requestBody) {
		this.requestBody = requestBody;
		this.contentLength = requestBody.length;
	}

	public InputStream getRequestBodyStream() {
		return requestBodyStream;
	}

	public void setRequestBodyStream(InputStream requestBodyStream) {
		this.requestBodyStream = requestBodyStream;
	}
	
	@Override
	protected void writeRequestBody(OutputStream out) throws IOException {
		if(this.contentLength < 0 && (this.requestBody != null || this.requestBodyStream != null)) {
			throw new IOException("You MUST set PostRequest.contentLength before calling doRequest()!");
		}
		
		if(this.requestBody != null) {
			out.write(this.requestBody, 0, this.contentLength);
			return;
		}
		
		int n, x, i=0;
		byte[] buf = new byte[1024];
		if(this.requestBodyStream != null) {
			while(i < this.contentLength && (n = this.requestBodyStream.read(buf)) > 0) {
				i += n;
				x = Math.min(this.contentLength - i, n);
				out.write(buf, 0, x);
			}
		}
	}

	@Override
	protected String method() {
		return "POST";
	}
	
	@Override
	public HttpResponse doRequest() throws IOException, NoSuchAlgorithmException, KeyManagementException {
		if(this.contentLength != -1) {
			this.headers.put("Content-Length", String.valueOf(this.contentLength));
		}
		if(this.contentType != null) {
			this.headers.put("Content-Type", this.contentType);
		}
		
		return super.doRequest();
	}
	
}
