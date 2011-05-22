package com.mixblendr.util;

import java.io.*;
import java.net.*;

public class Sender {
	private String hostUrl = "http://localhost/getfile.php";

	private InputStream input;
	private MultiPartFormOutputStream out;
	private volatile boolean stopRequested;

	public Sender(String url) {
		hostUrl = url;
	}

	public boolean sendFile(File tempFile, String filename) throws Exception {
		boolean res;
		FileInputStream fis = new FileInputStream(tempFile);
		try {
			res = sendFile(fis, filename);
		} finally {
			fis.close();
		}
		return res;
	}

	public boolean sendFile(InputStream stream, String filename)
			throws Exception {
		input = stream;
		URL url = new URL(hostUrl);
		String boundary = MultiPartFormOutputStream.createBoundary();
		URLConnection urlConnection = MultiPartFormOutputStream.createConnection(url);
		urlConnection.setRequestProperty("Accept", "*/*");
		urlConnection.setRequestProperty("Content-Type",
				MultiPartFormOutputStream.getContentType(boundary));

		// set some other request headers...
		urlConnection.setRequestProperty("Connection", "Keep-Alive");
		urlConnection.setRequestProperty("Cache-Control", "no-cache");

		// no need to connect because getOutputStream() does it
		out = new MultiPartFormOutputStream(urlConnection.getOutputStream(),
				boundary);
		try {
			// upload the file
			String mimetype = "application/octet-stream";

			if (filename.toLowerCase().endsWith(".ogg")) {
				mimetype = "audio/x-ogg";
			} else if (filename.toLowerCase().endsWith(".mp3")) {
				mimetype = "audio/x-mp3";
			} else if (filename.toLowerCase().endsWith(".wav")) {
				mimetype = "audio/x-wav";
			} else if (filename.toLowerCase().endsWith(".mixblendr")) {
				mimetype = "application/x-mixblendr";
			}
			out.writeFile("uploaded", mimetype, filename, stream);
		} finally {
			out.close();
		}

		if (!stopRequested) {
			// read response from server
			input = urlConnection.getInputStream();
			BufferedReader in = new BufferedReader(new InputStreamReader(input));
			String line = "";
			boolean result = false;
			while ((line = in.readLine()) != null) {
				System.out.println(line);
				if (line.indexOf("OK") != -1) {
					result = true;
				}
			}
			in.close();
			return result;
		}
		return false;
	}
	
	/** while sending, get the number of bytes written from the stream. headers are not counted. */
	public long getUploadedBytes() {
		if (out != null) {
			return out.getWrittenBytes();
		}
		return 0;
	}

	/** request an ongoing upload to be terminated */
	public void requestStop() {
		stopRequested = true;
		if (input != null) {
			try {
				input.close();
			} catch (IOException ioe) {
				// ignore
			}
		}
		if (out != null) {
			try {
				out.abort();
			} catch (IOException ioe) {
				// ignore
			}
		}
	}
}
