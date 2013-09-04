package de.clemensloos.elan.receiver;


import java.io.File;
import java.io.IOException;
import java.util.Properties;


public class MyNanoHTTPD extends NanoHTTPD {

	
//	private ElanReceiverActivity activity;
	private ElanServerService activity;
	
	public MyNanoHTTPD(int port, ElanServerService activity) throws IOException {
		super(port, new File("alwaysdenyaccess"));
		
		this.activity = activity;
	}

	
	public Response serve(String uri, String method, Properties header, Properties parms, Properties files) {
		
		String song = parms.getProperty("song");
		if (song == null) {
			song = "";
		}
		activity.newValue(song);
		
		String msg = "";
		return new NanoHTTPD.Response( HTTP_OK, MIME_PLAINTEXT, msg );
	}
	
	
}
