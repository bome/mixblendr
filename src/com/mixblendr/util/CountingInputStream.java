/*
 * $Id: CountingInputStream.java 42 2011-05-22 08:41:25Z florian $
 *
 * (c) by Bome Software
 * All rights reserved.
 */

package com.mixblendr.util;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author florian
 */
public class CountingInputStream extends InputStream {

	private final InputStream stream;
	private long readBytes;
	private boolean closed;

	/**
	 * @param stream
	 */
	public CountingInputStream(InputStream stream) {
		super();
		this.stream = stream;
		readBytes = 0;
		closed = false;
	}

	/**
	 * @return the number of bytes read in one of the read() methods. skip() and
	 *         reset() are not considered.
	 */
	public final long getReadBytes() {
		return readBytes;
	}
	
	/**
	 * @return if the stream was closed via the close() method or by returning -1 in one of the read methods.
	 */
	public boolean isClosed() {
		return closed;
	}

	@Override
	public int read(byte[] b) throws IOException {
		int ret = stream.read(b);
		if (ret > 0) {
			readBytes += ret;
		} else if (ret < 0 || (b.length > 0 && ret == 0)) {
			closed = true;
		}
		return ret;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int ret = stream.read(b, off, len);
		if (ret > 0) {
			readBytes += ret;
		} else if (ret < 0 || (len > 0 && ret == 0)) {
			closed = true;
		}
		return ret;
	}

	@Override
	public int read() throws IOException {
		int ret = stream.read();
		if (ret >= 0) {
			readBytes++;
		} else if (ret < 0) {
			closed = true;
		}
		return ret;
	}

	@Override
	public long skip(long n) throws IOException {
		return stream.skip(n);
	}

	@Override
	public int available() throws IOException {
		return stream.available();
	}

	@Override
	public void close() throws IOException {
		stream.close();
		closed = true;
	}

	@Override
	public synchronized void mark(int readlimit) {
		stream.mark(readlimit);
	}

	@Override
	public synchronized void reset() throws IOException {
		stream.reset();
	}

	@Override
	public boolean markSupported() {
		return stream.markSupported();
	}

}
