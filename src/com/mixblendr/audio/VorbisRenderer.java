/**
 *
 */

package com.mixblendr.audio;

import java.io.IOException;
import java.io.OutputStream;

import org.tritonus.share.sampled.FloatSampleBuffer;
import org.xiph.libogg.ogg_packet;
import org.xiph.libogg.ogg_page;
import org.xiph.libogg.ogg_stream_state;
import org.xiph.libvorbis.*;

import com.mixblendr.util.Debug;

/**
 * Render a FloatAudioInput to an ogg stream
 * @author Florian Bomers
 *
 */
public class VorbisRenderer extends Renderer {
	private final static boolean DEBUG = false;

	// the encoder class
	private vorbisenc encoder;
	// take physical pages, weld into a logical stream of packets
	private ogg_stream_state os; 
	// one Ogg bitstream page. Vorbis packets are inside
	private ogg_page og;
	// one raw packet of data for decode
	private ogg_packet op; 
	// central working state for the packet->PCM decoder
	private vorbis_dsp_state vd;
	// local working space for packet->PCM decode
	private vorbis_block vb;
	
	/**
	 * @param stream the stream to receive rendered and encoded ogg bytes
	 */
	public VorbisRenderer(AudioState state, OutputStream stream) {
		super(state, stream);
	}

	@Override
	protected void init() throws Exception {
		super.init();
		// struct that stores all the static vorbis bitstream settings
		vorbis_info vi = new vorbis_info();
		encoder = new vorbisenc();

		if (!encoder.vorbis_encode_init_vbr(vi, format.getChannels(), (int)format.getSampleRate(), .3f)) {
			throw new Exception("Failed to Initialize ogg/vorbis encoder");
		}

		// struct that stores all the user comments
		vorbis_comment vc = new vorbis_comment();
		vc.vorbis_comment_add_tag("ENCODER", "Java Vorbis Encoder");

		vd = new vorbis_dsp_state();

		if (!vd.vorbis_analysis_init(vi)) {
			throw new Exception("Failed to initialize ogg/vorbis encoder [vorbis_dsp_state]");
		}

		vb = new vorbis_block(vd);

		java.util.Random generator = new java.util.Random();
		os = new ogg_stream_state(generator.nextInt(256));

		if (DEBUG) Debug.onnl("Writing ogg header...");

		ogg_packet header = new ogg_packet();
		ogg_packet header_comm = new ogg_packet();
		ogg_packet header_code = new ogg_packet();

		vd.vorbis_analysis_headerout(vc, header, header_comm, header_code);

		os.ogg_stream_packetin(header); // automatically placed in its own page
		os.ogg_stream_packetin(header_comm);
		os.ogg_stream_packetin(header_code);

		og = new ogg_page();
		op = new ogg_packet();

		while (true) {
			if (!os.ogg_stream_flush(og)) break;
			outStream.write(og.header, 0, og.header_len);
			outStream.write(og.body, 0, og.body_len);
		}
		if (DEBUG) Debug.onnl("encoding...");
	}
	
	/** write all encoded data to the stream */ 
	private void writeVorbis() throws IOException {
		// vorbis does some data preanalysis, then divvies up blocks for
		// more involved (potentially parallel) processing. Get a single
		// block for encoding now

		while (vb.vorbis_analysis_blockout(vd)) {
			// analysis, assume we want to use bitrate management
			vb.vorbis_analysis(null);
			vb.vorbis_bitrate_addblock();

			while (vd.vorbis_bitrate_flushpacket(op)) {
				// weld the packet into the bitstream
				os.ogg_stream_packetin(op);
				// write out pages (if any)
				while (true) {
					if (!os.ogg_stream_pageout(og)) {
						break;
					}

					outStream.write(og.header, 0, og.header_len);
					outStream.write(og.body, 0, og.body_len);

					// this could be set above, but for illustrative
					// purposes, I do it here (to show that vorbis does
					// know where the stream ends)
					if (og.ogg_page_eos() > 0) {
						break;
					}
				}
			}
		}
	}

	@Override
	protected void onRenderedBuffer(FloatSampleBuffer buffer) throws Exception {
		final int samples = buffer.getSampleCount();
		final int channels = buffer.getChannelCount();
		// expose the buffer to submit data
		final float[][] vBuffer = vd.vorbis_analysis_buffer(samples);
		for (int c = 0; c < channels; c++) {
			float[] channel = buffer.getChannel(c);
			//for (int i = 0; i < samples; i++) {
			//	vBuffer[c][vd.pcm_current + i] = channel[i];
			//}
			System.arraycopy(channel, 0, vBuffer[c], vd.pcm_current, samples);
		}

		// tell the library how much we actually submitted
		vd.vorbis_analysis_wrote(samples);
		writeVorbis();
	}

	@Override
	protected void done() throws Exception {
		super.done();
		vd.vorbis_analysis_wrote(0);
		writeVorbis();
		if (DEBUG) Debug.debug("done.");
	}
}
