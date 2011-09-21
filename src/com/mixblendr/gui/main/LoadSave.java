/*
 *
 */

package com.mixblendr.gui.main;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import com.mixblendr.util.CountingInputStream;
import com.mixblendr.util.Debug;
import com.mixblendr.util.Sender;

/**
 * Class for handling load and save operations.
 * 
 * @author Florian Bomers
 */
public class LoadSave {

	private ProgressDialog progressDialog;
	private Globals globals;

	/**
	 * @param globals
	 */
	public LoadSave(Globals globals) {
		super();
		this.globals = globals;
		progressDialog = globals.getProgressDialog();
	}

	/**
	 * Interactive saving: prompt for a filename and save the current timeline
	 * as a .mixblendr file. Errors are displayed via Debug.displayErrorDialog.
	 * 
	 * @param toWeb if true, and the saveToServerScript URL is set, send the
	 *        file to that URL. Otherwise, if Globals.CAN_SAVE_TO_LOCAL_FILE is
	 *        set, ask for a local filename and save to a file.
	 */
	public void save(boolean toWeb) {
		if (globals.getPlayer().getMixer().isEmpty()) {
			Debug.displayInfoDialogAsync(globals.getMasterPanel(), null,
					"Nothing to save.");
			return;
		}
		if (Globals.CAN_SAVE_TO_LOCAL_FILE
				&& (!toWeb || !progressDialog.canSaveToServer())) {
			final JFileChooser fc = new JFileChooser();
			int returnVal = fc.showSaveDialog(globals.getMasterPanel());
			if (returnVal == JFileChooser.APPROVE_OPTION) {
				File file = fc.getSelectedFile();
				if (!file.getName().toLowerCase().endsWith(
						Globals.SAVED_FILES_EXTENSION)) {
					file = new File(file.getAbsolutePath()
							+ Globals.SAVED_FILES_EXTENSION);
				}
				saveToFile(file);
			}
		} else if (progressDialog.canSaveToServer()) {
			String filename = JOptionPane.showInputDialog(
					globals.getMasterPanel(),
					"Please enter the name of the track", "song.mixblendr");
			if (filename != null && !filename.equals("")) {
				if (!filename.toLowerCase().endsWith(
						Globals.SAVED_FILES_EXTENSION)) {
					filename = filename + Globals.SAVED_FILES_EXTENSION;
				}
				saveToWeb(filename);
			}
		} else {
			Debug.displayErrorDialog(globals.getMasterPanel(), "Error",
					"Saving is not possible.");
		}
	}

	/**
	 * Save the current timeline as a local .mixblendr file. Errors are
	 * displayed via Debug.displayErrorDialog.
	 * 
	 * @param file the full path of the file to save, e.g. "C:\my_mix.song"
	 */
	public void saveToFile(File file) {
		if (globals.getPlayer().getMixer().isEmpty()) {
			Debug.displayInfoDialogAsync(globals.getMasterPanel(), null,
					"Nothing to save.");
			return;
		}
		try {
			globals.getPlayer().getMixer().xmlExport(file);
		} catch (Exception e) {
			Debug.displayErrorDialogAsync(globals.getMasterPanel(), e,
					"when saving");
		}
	}

	/**
	 * Save the current timeline as a .mixblendr file to the publish URL. Errors
	 * are displayed via Debug.displayErrorDialog. Pre-condition: the
	 * SaveToServerScriptURL is set.
	 * 
	 * @param filename the filename without any path, e.g. "my_song.mixblendr".
	 */
	public void saveToWeb(String filename) {
		try {
			progressDialog.setWebMode("Saving", filename);
			if (globals.getPlayer().getMixer().isEmpty()) {
				Debug.displayInfoDialogAsync(globals.getMasterPanel(), null,
						"Nothing to save.");
				return;
			}
			(new Thread(new LoadSaveWorker(false))).start();
		} catch (Exception e) {
			Debug.displayErrorDialogAsync(globals.getMasterPanel(), e, "");
		}
	}

	private class LoadSaveWorker implements Runnable, ProgressDialog.Listener {
		/** flag to signal a requested closing of this thread */
		protected volatile boolean closed = false;

		private Sender sender;
		private long uploadFileSize = 0;

		private boolean isLoadMode;
		private CountingInputStream inputStream;
		
		public LoadSaveWorker(boolean loadMode) {
			isLoadMode = loadMode;
		}
		
		public void run() {
			String context = "setting up";
			try {
				progressDialog.addListener(this);
				progressDialog.updateText("Setting up...");
				progressDialog.start();
				Thread.yield();

				if (!isLoadMode) {
					context = "sending file to server";
					sender = new Sender(progressDialog.getSaveToServerScriptURL());
					ByteArrayOutputStream outStream = new ByteArrayOutputStream(
							100000);
					progressDialog.updateText("Saving...");
					globals.getPlayer().getMixer().xmlExport(outStream);
					Thread.yield();
					if (!closed) {
						byte[] exportedData = outStream.toByteArray();
						uploadFileSize = exportedData.length;
						ByteArrayInputStream inStream = new ByteArrayInputStream(exportedData);
						progressDialog.updateText("Uploading...");
						if (sender.sendFile(inStream, progressDialog.getFilename())) {
							progressDialog.doneSuccessful();
						} else {
							progressDialog.doneFailed("error sending file to server");
						}
						inStream.close();
					}
					outStream.close();
				} else {
					context = "loading file from server";
					inputStream = new CountingInputStream(progressDialog.getURL().openStream());
					try {
						globals.getPlayer().getMixer().xmlImport(inputStream);
					} finally {
						inputStream.close();
					}
					progressDialog.doneSuccessful();
				}
			} catch (Exception e) {
				progressDialog.doneFailed("error " + context + " [" + e.getLocalizedMessage() + "]");
			}
			progressDialog.removeListener(this);
		}
		
		public void onProgressStart() {
		}

		/*
		 * (non-Javadoc)
		 * @see com.mixblendr.gui.main.Publisher.Listener#onPublishingUpdate()
		 */
		public void onProgressUpdate() {
			if (!isLoadMode) {
				// to web: also consider upload in progress
				double uploadProgress = 0.0;
				if (sender != null && uploadFileSize > 0) {
					uploadProgress = ((double) sender.getUploadedBytes())
							/ uploadFileSize;
				}
				progressDialog.updateProgress(uploadProgress);
			} else {
				if (inputStream != null && inputStream.getReadBytes() > 0) {
					if (!inputStream.isClosed()) {
						progressDialog.updateText("Loading..." + inputStream.getReadBytes() + " bytes");
					}
				} else {
					progressDialog.updateText("Loading...");
				}
			}
		}

		/*
		 * (non-Javadoc)
		 * @see com.mixblendr.gui.main.Publisher.Listener#onPublishingCanceled()
		 */
		public void onProgressCanceled() {
			closed = true;
			if (sender != null) {
				sender.requestStop();
			}
		}

		public void onProgressEnd(boolean success) {
		}

		public void onProgressDialogDismissed(boolean success) {
		}
	} // LoadSaveWorker

	/**
	 * Interactive loading: prompt for a filename and load a complete song from
	 * a .mixblendr file. Errors are displayed via Debug.displayErrorDialog.
	 * 
	 * @param fromWeb if true, and the LoadFromServerURL is set, load the file
	 *        from that URL, appended with the filename entered by the user.
	 *        Otherwise, if Globals.CAN_LOAD_FROM_LOCAL_FILE is set, prompt for
	 *        a local filename and load from a local file.
	 */
	public void load(boolean fromWeb) {
		if (!globals.getPlayer().getMixer().isEmpty()) {
			if (!globals.confirm("Loading a song will clear the current song.\nAre you sure you want to continue?")) {
				return;
			}
		}
		// ensure dynamic loading of effect classes (and registering with
		// automation manager)
		EffectManager.getEffectNames();
		try {
			if (Globals.CAN_LOAD_FROM_LOCAL_FILE
					&& (!fromWeb || !progressDialog.canLoadFromServer())) {
				final JFileChooser fc = new JFileChooser();
				int returnVal = fc.showOpenDialog(globals.getMasterPanel());
				if (returnVal == JFileChooser.APPROVE_OPTION) {
					File file = fc.getSelectedFile();
					loadFromFile(file);
				}
			} else if (progressDialog.canLoadFromServer()) {
				String filename = JOptionPane.showInputDialog(
						globals.getMasterPanel(),
						"Please enter the name of the song to load",
						"song.mixblendr");
				if (filename != null && !filename.equals("")) {
					if (!filename.toLowerCase().endsWith(
							Globals.SAVED_FILES_EXTENSION)) {
						filename = filename + Globals.SAVED_FILES_EXTENSION;
					}
					loadFromWeb(filename);
				}
			} else {
				Debug.displayErrorDialog(globals.getMasterPanel(), "Error",
						"Loading is not possible.");
			}
		} catch (Exception e) {
			Debug.displayErrorDialogAsync(globals.getMasterPanel(), e,
					"when loading");
		}
	}

	/**
	 * Load a complete song from a .mixblendr file. You should call
	 * Main.updateAll() after having loaded the file. Errors are displayed via
	 * Debug.displayErrorDialog.
	 * 
	 * @param file the full file path to the .mixblendr file to be loaded.
	 */
	public void loadFromFile(File file) {
		try {
			globals.getPlayer().getMixer().xmlImport(file);
		} catch (Exception e) {
			Debug.displayErrorDialogAsync(globals.getMasterPanel(), e,
					"when loading");
		}
	}

	/**
	 * Load a complete song from a .mixblendr file from the web site set in
	 * LoadFromServerURL. You should call Main.updateAll() after having loaded
	 * the file. Errors are displayed via Debug.displayErrorDialog.
	 * 
	 * @param filename the filename without any path, e.g. "my_song.mixblendr",
	 *        resulting in the full URL getLoadFromServerURL() + "/" + filename
	 */
	public void loadFromWeb(String filename) {
		try {
			loadFromWeb(new URL(new URL(progressDialog.getLoadFromServerURL()),
					filename));
		} catch (Exception e) {
			Debug.displayErrorDialogAsync(globals.getMasterPanel(), e,
					"when loading");
		}
	}

	/**
	 * Load a complete song from a .mixblendr file from the given web file. You
	 * should call Main.updateAll() after having loaded the file. Errors are
	 * displayed via Debug.displayErrorDialog.
	 * 
	 * @param url the URL where to load the .mixblendr file from, e.g.
	 *        "http://www.mixblendr.com/files/my_song.mixblendr"
	 */
	public void loadFromWeb(URL url) {
		try {
			progressDialog.setWebMode("Loading", url);
			progressDialog.setProgressBarVisible(false);
			(new Thread(new LoadSaveWorker(true))).start();
		} catch (Exception e) {
			Debug.displayErrorDialogAsync(globals.getMasterPanel(), e, "");
		}
	}

}
