package org.ptst.net;

import java.net.URL;

public class GetRequest extends HttpRequest {

	public GetRequest(URL url) {
		super(url);
	}
	
	@Override
	protected String method() {
		return "GET";
	}
	
}
