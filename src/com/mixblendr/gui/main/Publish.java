/*
 *
 */

package com.mixblendr.gui.main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import com.mixblendr.audio.Renderer;
import com.mixblendr.audio.VorbisRenderer;
import com.mixblendr.util.Debug;
import com.mixblendr.util.Sender;

/**
 * Publish the song to .ogg file, either locally or on server.
 * 
 * @author Florian Bomers
 */
public class Publish {

	private ProgressDialog progressDialog;
	private Globals globals;

	/**
	 * @param globals
	 */
	public Publish(Globals globals) {
		super();
		this.globals = globals;
		progressDialog = globals.getProgressDialog();
	}

	/**
	 * Interactive publishing: publish the rendered mix to a .ogg file, either
	 * to the web through the saveToServerScript URL (when toWeb is true),
	 * otherwise to a local file.
	 * 
	 * @param toWeb if true, and the saveToServerScript URL is set, send the
	 *        rendered mix to that URL. Otherwise, if
	 *        Globals.CAN_PUBLISH_TO_LOCAL_FILE is set, ask for a local filename
	 *        and save the mix to a file.
	 */
	public void publish(boolean toWeb) {
		globals.stopPlayback();
		if (globals.getPlayer().getMixer().isEmpty()) {
			Debug.displayInfoDialogAsync(globals.getMasterPanel(), null,
					"Nothing to publish.");
			return;
		}

		if (Globals.CAN_PUBLISH_TO_LOCAL_FILE
				&& (!toWeb || !progressDialog.canSaveToServer())) {
			final JFileChooser fc = new JFileChooser();
			int returnVal = fc.showSaveDialog(globals.getMasterPanel());
			if (returnVal == JFileChooser.APPROVE_OPTION) {
				File file = fc.getSelectedFile();
				if (!file.getName().toLowerCase().endsWith(".ogg")) {
					file = new File(file.getAbsolutePath() + ".ogg");
				}
				publishToFile(file);
			}
		} else if (progressDialog.canSaveToServer()) {
			double minStartTime = globals.getPlayer().getMixer().getStartTimeSeconds();
			if (minStartTime > 60) {
				Debug.displayInfoDialogAsync(
						globals.getMasterPanel(),
						null,
						"Your song has at least 1 minute of silence at the beginning.<br>"
								+ "To publish your song, please move it to the beginning of the timeline.");
				return;
			}
			// if (confirm("Are you sure you want to publish this track?\n" +
			// "You will not be able to make any further edits after you have published it."))
			// {
			String filename = JOptionPane.showInputDialog(
					globals.getMasterPanel(),
					"Please enter the name of the song to publish",
					"my_song.ogg");
			if (filename != null && !filename.equals("")) {
				if (!filename.endsWith(".ogg")) {
					filename += ".ogg";
				}
				publishToWeb(filename);
			}
		} else {
			Debug.displayErrorDialog(globals.getMasterPanel(), "Error",
					"Publishing is not possible.");
		}
	}

	/**
	 * Publish the rendered mix to a local .ogg file. Publishing will be done
	 * asynchronously. Errors are displayed via Debug.displayErrorDialog.
	 * 
	 * @param file the full path of the file to save, e.g. "C:\my_mix.ogg"
	 */
	public void publishToFile(File file) {
		try {
			progressDialog.setFileMode("Publishing", file);
			globals.stopPlayback();
			if (globals.getPlayer().getMixer().isEmpty()) {
				Debug.displayInfoDialogAsync(globals.getMasterPanel(), null,
						"Nothing to publish.");
				return;
			}
			(new Thread(new PublishWorker())).start();
		} catch (Exception e) {
			Debug.displayErrorDialogAsync(globals.getMasterPanel(), e, "");
		}
	}

	/**
	 * Publish the rendered mix as a .ogg file to the publish URL. Publishing
	 * will be done asynchronously. Errors are displayed via
	 * Debug.displayErrorDialog. Pre-condition: the SaveToServerScriptURL is
	 * set.
	 * 
	 * @param filename the filename without any path, e.g. "my_mix.ogg".
	 */
	public void publishToWeb(String filename) {
		try {
			progressDialog.setWebMode("Publishing", filename);
			globals.stopPlayback();
			if (globals.getPlayer().getMixer().isEmpty()) {
				Debug.displayInfoDialogAsync(globals.getMasterPanel(), null,
						"Nothing to publish.");
				return;
			}
			(new Thread(new PublishWorker())).start();
		} catch (Exception e) {
			Debug.displayErrorDialogAsync(globals.getMasterPanel(), e, "");
		}
	}

	private class PublishWorker implements Runnable, ProgressDialog.Listener {
		/** flag to signal a requested closing of this thread */
		protected volatile boolean closed = false;

		private boolean savedLoopEnabled;
		private long savedPosition;

		private Renderer renderer;
		private Sender sender;
		private long uploadFileSize = 0;

		private long renderSampleStart;
		private long renderSampleCount;

		public void run() {
			File vorbisTempFile = null;
			OutputStream output = null;
			boolean error = false;
			try {
				progressDialog.addListener(this);
				progressDialog.updateText("Setting up...");
				progressDialog.start();
				// give time to display the window
				Thread.yield();

				savedLoopEnabled = globals.getState().isLoopEnabled();
				//$$fb when in loop mode, will not exceed the loop portion anyway
				//globals.getPlayer().setLoopEnabled(false);

				savedPosition = globals.getState().getSamplePosition();

				if (savedLoopEnabled) {
					renderSampleStart = globals.getState().getLoopStartSamples();
					renderSampleCount = globals.getState().getLoopDurationSamples();
				} else {
					renderSampleStart = 0;
					renderSampleCount = globals.getPlayer().getMixer().getDurationSamples();
				}
				if (renderSampleCount == 0) {
					throw new Exception("no audio data to render.");
				}
				globals.getPlayer().setPositionSamples(renderSampleStart);

				File file = progressDialog.getFile();
				if (file == null) {
					// publish to web
					vorbisTempFile = File.createTempFile("mixblendr", "vorbis");
					vorbisTempFile.deleteOnExit();
					file = vorbisTempFile;
				}
				output = new FileOutputStream(file);
				renderer = new VorbisRenderer(globals.getState(), output);
				progressDialog.updateText("Rendering to ogg...");
				Thread.yield();
				renderer.render(globals.getPlayer().getMixer(),
						renderSampleCount);
				output.close();
				output = null;
				Thread.yield();

				if (!closed && !progressDialog.isFileMode()) {
					progressDialog.updateText("Uploading to server...");
					Thread.yield();
					sender = new Sender(
							progressDialog.getSaveToServerScriptURL());
					uploadFileSize = vorbisTempFile.length();
					if (!sender.sendFile(vorbisTempFile,
							progressDialog.getFilename())) {
						if (!closed) {
							error = true;
							progressDialog.doneFailed("upload to server failed");
						}
					}
				}

			} catch (Exception e) {
				if (!closed) {
					error = true;
					progressDialog.doneFailed("error publishing ["
							+ e.getLocalizedMessage() + "]");
					e.printStackTrace();
				}
			}
			progressDialog.removeListener(this);
			if (!closed && !error) {
				progressDialog.doneSuccessful();
			}
			//globals.getPlayer().setLoopEnabled(savedLoopEnabled);
			globals.getPlayer().setPositionSamples(savedPosition);
			if (output != null) {
				try {
					output.close();
				} catch (IOException ioe) {
					// ignore
				}
				Thread.yield();
			}
			if (vorbisTempFile != null) {
				vorbisTempFile.delete();
			}
		}

		public void onProgressStart() {
		}

		/*
		 * (non-Javadoc)
		 * @see com.mixblendr.gui.main.Publisher.Listener#onPublishingUpdate()
		 */
		public void onProgressUpdate() {
			double percent = 0.0;

			if (renderSampleCount > 0) {
				percent = ((double) globals.getState().getSampleSlicePosition() - renderSampleStart)
						/ renderSampleCount;
			}

			if (!progressDialog.isFileMode()) {
				// to web: also consider upload in progress
				double uploadProgress = 0.0;
				if (sender != null && uploadFileSize > 0) {
					uploadProgress = ((double) sender.getUploadedBytes())
							/ uploadFileSize;
				}
				percent = (percent + uploadProgress) / 2.0;
			}
			progressDialog.updateProgress(percent);
		}

		/*
		 * (non-Javadoc)
		 * @see com.mixblendr.gui.main.Publisher.Listener#onPublishingCanceled()
		 */
		public void onProgressCanceled() {
			closed = true;
			if (renderer != null) {
				renderer.requestStop();
			}
			if (sender != null) {
				sender.requestStop();
			}
		}

		public void onProgressEnd(boolean success) {
		}

		public void onProgressDialogDismissed(boolean success) {
		}
	} // PublishWorker

}
