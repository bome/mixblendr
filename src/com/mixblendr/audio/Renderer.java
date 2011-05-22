/**
 *
 */

package com.mixblendr.audio;

import static com.mixblendr.util.Debug.debug;

import java.io.OutputStream;

import javax.sound.sampled.AudioFormat;

import org.tritonus.share.sampled.AudioUtils;
import org.tritonus.share.sampled.FloatSampleBuffer;
import org.tritonus.share.sampled.FloatSampleInput;

/**
 * Class to render the mixer output to a PCM stream. You can use one instance
 * only once.
 * 
 * @author Florian Bomers
 */
public class Renderer {
	private final static boolean DEBUG = false;

	protected AudioFormat format;
	protected AudioState state;
	protected OutputStream outStream;

	private volatile boolean stopRequested;

	private long currSample;
	private long sampleCount;
	private FloatSampleBuffer floatBuffer;
	private byte[] byteBuffer;

	/**
	 * create an instance of the stream Renderer
	 * 
	 * @param stream the stream to write to, it is not closed after rendering.
	 */
	public Renderer(AudioState state, OutputStream stream) {
		format = new AudioFormat(state.getSampleRate(), 16,
				state.getChannels(), true, false);
		this.state = state;
		stopRequested = false;
		outStream = stream;
	}

	public void setBitsPerSample(int bits) {
		format = new AudioFormat(state.getSampleRate(), bits,
				format.getChannels(), bits > 8, false);
	}

	public int getBitsPerSample() {
		return format.getSampleSizeInBits();
	}

	/** asynchronously request the render method to stop */
	public void requestStop() {
		stopRequested = true;
	}

	public double getProgressPercentage() {
		return ((double) currSample) / sampleCount;
	}

	/**
	 * called by the render() method before rendering starts. create the
	 * temporary float and byte buffers for reading
	 */
	protected void init() throws Exception {
		int sliceSizeSamples = state.getSliceSizeSamples();
		int sliceSizeBytes = sliceSizeSamples * format.getFrameSize();
		if (DEBUG) {
			debug("Renderer: slice time: " + sliceSizeBytes + " bytes = "
					+ AudioUtils.bytes2MillisD(sliceSizeBytes, format) + "ms");
		}
		// calculate number of samples
		floatBuffer = new FloatSampleBuffer(format.getChannels(),
				sliceSizeSamples, format.getSampleRate());
		byteBuffer = new byte[sliceSizeBytes];
	}

	/** called by the render() method after each buffer was read. */
	protected void onRenderedBuffer(FloatSampleBuffer buffer) throws Exception {
		// convert to byte
		int n = floatBuffer.convertToByteArray(byteBuffer, 0, format);
		// write the audio data to the stream
		outStream.write(byteBuffer, 0, n);
	}

	/** called by the render method when rendering is done. */
	protected void done() throws Exception {
		// nothing
	}

	/**
	 * Render the stream from the given input stream to the output. This method
	 * can only be called once. After that, use a new instance of Renderer.
	 * 
	 * @return the number of rendered samples
	 */
	public long render(FloatSampleInput input, long renderSampleCount)
			throws Exception {
		state.resetSamplePositionInterpolation();
		this.sampleCount = renderSampleCount;
		this.currSample = 0;
		init();
		long remainingSamples = renderSampleCount;
		if (remainingSamples == 0) {
			throw new Exception("no audio data to render.");
		}
		while (!stopRequested && remainingSamples > 0) {
			// read from the input line
			if (floatBuffer.getSampleCount() > remainingSamples) {
				floatBuffer.changeSampleCount((int) remainingSamples, false);
			}
			input.read(floatBuffer);
			onRenderedBuffer(floatBuffer);
			currSample += floatBuffer.getSampleCount();
			remainingSamples -= floatBuffer.getSampleCount();
		}
		done();
		return currSample;
	}

}
