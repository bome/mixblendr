/********************************************************************
 *                                                                  *
 * THIS FILE IS PART OF THE OggVorbis SOFTWARE CODEC SOURCE CODE.   *
 * USE, DISTRIBUTION AND REPRODUCTION OF THIS LIBRARY SOURCE IS     *
 * GOVERNED BY A BSD-STYLE SOURCE LICENSE INCLUDED WITH THIS SOURCE *
 * IN 'COPYING'. PLEASE READ THESE TERMS BEFORE DISTRIBUTING.       *
 *                                                                  *
 * THE OggVorbis SOURCE CODE IS (C) COPYRIGHT 1994-2002             *
 * by the Xiph.Org Foundation http://www.xiph.org/                  *
 *                                                                  *
 ********************************************************************/

package com.mixblendr.audio;

import java.io.*;

import org.xiph.libvorbis.*;
import org.xiph.libogg.*;

/** render a raw PCM input stream to an ogg output stream */
public class VorbisEncoder {

	/**
	 * render a raw PCM input file to an ogg output stream
	 * 
	 * @param inputFile the input file, raw PCM data stereo, 16 bits signed,
	 *        little endian
	 */
	public static void encode(File inputFile, File outputFile) throws Exception {
		InputStream ins = new FileInputStream(inputFile);
		try {
			OutputStream outs = new FileOutputStream(outputFile);
			try {
				encode(ins, outs);
			} finally {
				outs.close();
			}
		} finally {
			ins.close();
		}
	}

	/**
	 * render a raw PCM input stream to an ogg output stream.
	 * 
	 * @param input the input stream, raw PCM data stereo, 16 bits signed,
	 *        little endian
	 */
	public static void encode(InputStream input, OutputStream output)
			throws Exception {

		// the encoder class
		vorbisenc encoder;
		// take physical pages, weld into a logical stream of packets
		ogg_stream_state os;
		// one Ogg bitstream page. Vorbis packets are inside
		ogg_page og;
		// one raw packet of data for decode
		ogg_packet op;
		// struct that stores all the static vorbis bitstream settings
		vorbis_info vi;
		// struct that stores all the user comments
		vorbis_comment vc;
		// central working state for the packet->PCM decoder
		vorbis_dsp_state vd;
		// local working space for packet->PCM decode
		vorbis_block vb;

		final int sampleSize = 4;
		final int READ = 1024 * 10;
		byte[] readbuffer = new byte[READ * sampleSize + 44];

		DataInputStream dis = new DataInputStream(input);

		boolean eos = false;

		vi = new vorbis_info();

		encoder = new vorbisenc();

		if (!encoder.vorbis_encode_init_vbr(vi, 2, 44100, .3f)) {
			throw new Exception("Failed to Initialize ogg/vorbis encoder");
		}

		vc = new vorbis_comment();
		vc.vorbis_comment_add_tag("ENCODER", "Java Vorbis Encoder");

		vd = new vorbis_dsp_state();

		if (!vd.vorbis_analysis_init(vi)) {
			throw new Exception(
					"Failed to initialize ogg/vorbis encoder [vorbis_dsp_state]");
		}

		vb = new vorbis_block(vd);

		java.util.Random generator = new java.util.Random(); // need to
																// randomize
																// seed
		os = new ogg_stream_state(generator.nextInt(256));

		System.out.print("Writing header...");

		ogg_packet header = new ogg_packet();
		ogg_packet header_comm = new ogg_packet();
		ogg_packet header_code = new ogg_packet();

		vd.vorbis_analysis_headerout(vc, header, header_comm, header_code);

		os.ogg_stream_packetin(header); // automatically placed in its own page
		os.ogg_stream_packetin(header_comm);
		os.ogg_stream_packetin(header_code);

		og = new ogg_page();
		op = new ogg_packet();

		DataOutputStream dos = new DataOutputStream(output);

		while (!eos) {

			if (!os.ogg_stream_flush(og)) break;

			dos.write(og.header, 0, og.header_len);
			dos.write(og.body, 0, og.body_len);
			// System.out.print(".");
		}

		System.out.print("encoding...");
		while (!eos) {

			int bytes = dis.read(readbuffer, 0, READ * sampleSize);

			if (bytes <= 0) {

				// end of file. this can be done implicitly in the mainline,
				// but it's easier to see here in non-clever fashion.
				// Tell the library we're at end of stream so that it can
				// handle
				// the last frame and mark end of stream in the output
				// properly

				vd.vorbis_analysis_wrote(0);

			} else {
				// data to encode

				// expose the buffer to submit data
				float[][] buffer = vd.vorbis_analysis_buffer(READ);

				// uninterleave samples
				final int max = bytes / 4;
				for (int i = 0; i < max; i++) {
					buffer[0][vd.pcm_current + i] = ((readbuffer[i * 4 + 1] << 8) | (0x00ff & readbuffer[i * 4])) / 32768.f;
					buffer[1][vd.pcm_current + i] = ((readbuffer[i * 4 + 3] << 8) | (0x00ff & readbuffer[i * 4 + 2])) / 32768.f;
				}

				// tell the library how much we actually submitted
				vd.vorbis_analysis_wrote(max);
			}

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
					while (!eos) {

						if (!os.ogg_stream_pageout(og)) {
							break;
						}

						dos.write(og.header, 0, og.header_len);
						dos.write(og.body, 0, og.body_len);

						// this could be set above, but for illustrative
						// purposes, I do it here (to show that vorbis does
						// know where the stream ends)
						if (og.ogg_page_eos() > 0) eos = true;
					}
				}
			}
		}
		System.out.println("done.");
	}
}
