/*
 *
 */

package com.mixblendr.gui.main;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;

import javax.swing.*;

/**
 * Class for displaying a progress dialog.
 * 
 * @author Florian Bomers
 */
public class ProgressDialog implements ActionListener {

	private Globals globals;

	private DlgWindow dlgWindow = null;
	private boolean successful = false;

	private String loadFromServerURL = "";
	private String saveToServerScriptURL = "";

	private ArrayList<Listener> listeners = new ArrayList<ProgressDialog.Listener>();

	private String operationVerb;
	private String filename = "";
	private File file = null;
	private URL url = null;

	/**
	 * @param globals
	 */
	public ProgressDialog(Globals globals) {
		super();
		this.globals = globals;
		operationVerb = "";
	}

	public void addListener(Listener L) {
		listeners.add(L);
	}

	public void removeListener(Listener L) {
		listeners.remove(L);
	}

	/**
	 * Set the LoadFromServer URL, i.e. the base path from which files are
	 * loaded from.
	 * 
	 * @param url the URL where .mixblendr files are loaded from the web, e.g.
	 *        "http://www.mixblendr.com/files".
	 */
	public void setLoadFromServerURL(String url) {
		loadFromServerURL = url;
	}

	/**
	 * Get the LoadFromServerURL, i.e. the server directory where the saved
	 * files are stored.
	 * 
	 * @return the loadFromServerURL
	 */
	public String getLoadFromServerURL() {
		return loadFromServerURL;
	}

	public boolean canLoadFromServer() {
		return (loadFromServerURL != null) && (loadFromServerURL.length() > 0);
	}

	/**
	 * Get the script URL which accepts uploaded files (when using saveToWeb()
	 * or pulishToWeb() ).
	 * 
	 * @return the saveToServerScriptURL
	 */
	public String getSaveToServerScriptURL() {
		return saveToServerScriptURL;
	}

	/**
	 * Set the server script that accepts files through the "uploaded" named
	 * section. It is used when saving or publishing to web.
	 * 
	 * @param saveToServerScriptURL the saveToServerScriptURL to set
	 */
	public void setSaveToServerScriptURL(String saveToServerScriptURL) {
		this.saveToServerScriptURL = saveToServerScriptURL;
	}

	public boolean canSaveToServer() {
		return (saveToServerScriptURL != null)
				&& (saveToServerScriptURL.length() > 0);
	}

	/** when configured for remote web loading/saving, this is the filename */
	public String getFilename() {
		return filename;
	}

	/** when configured for local file loading/saving, this is the local file */
	public File getFile() {
		return file;
	}

	/** when configured for remote web loading/saving, this is the URL (if filename is not set) */
	public URL getURL() {
		return url;
	}

	/**
	 * Switch to an operation with a filename, usually publishing/saving/loading to/from web.
	 */
	public void setWebMode(String verb, String filename) throws Exception {
		if (isInProgress()) {
			throw new Exception(verb + " in progress...");
		}
		this.filename = filename;
		this.file = null;
		this.url = null;
		this.operationVerb = verb;
	}

	/**
	 * Switch to an operation with a filename, usually publishing/saving/loading to/from web.
	 */
	public void setWebMode(String verb, URL url) throws Exception {
		if (isInProgress()) {
			throw new Exception(verb + " in progress...");
		}
		this.filename = "";
		this.file = null;
		this.url = url;
		this.operationVerb = verb;
	}

	/**
	 * Switch to an operation with a file, usually publishing/saving/loading to/from a local file.
	 */
	public void setFileMode(String verb, File file) throws Exception {
		if (isInProgress()) {
			throw new Exception(verb + " in progress...");
		}
		this.file = file;
		this.filename = "";
		this.url = null;
		this.operationVerb = verb;
	}
	
	/** return if configured in local file mode */
	public boolean isFileMode() {
		return (file != null);
	}
	
	public void setProgressBarVisible(boolean visible) {
		ensureDlgWindow();
		dlgWindow.getProgressBar().setVisible(visible);
	}

	/**
	 * @return true if currently visible
	 */
	public final boolean isInProgress() {
		return (dlgWindow != null) && (dlgWindow.isVisible());
	}

	private Timer guiTimer;
	private static final int GUI_REFRESH_INTERVAL_MILLIS = 40;

	/**
	 * show the blocking progress dialog, must not be called from the swing
	 * thread!
	 */
	public void start() {
		showProgressDialog();
		if (guiTimer == null) {
			guiTimer = new Timer(GUI_REFRESH_INTERVAL_MILLIS, this);
		}
		guiTimer.start();
	}
	
	private void ensureDlgWindow() {
		if (dlgWindow == null) {
			dlgWindow = new DlgWindow(globals.getMasterPanel());
		}
	}

	private void showProgressDialog() {
		assert (!SwingUtilities.isEventDispatchThread());
		ensureDlgWindow();
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				showProgressDialogSwingThread();
			}
		});
	}

	private void showProgressDialogSwingThread() {
		successful = false;
		for (Listener L : listeners) {
			L.onProgressStart();
		}
		dlgWindow.start();
	}

	/** called by the lengthy operation thread to update the text in the dialog */
	public void updateText(String text) {
		if (dlgWindow != null) {
			dlgWindow.updateText(text);
		}
	}

	/** called by the lengthy operation thread to update the progress bar */
	public void updateProgress(double percent) {
		if (dlgWindow != null) {
			dlgWindow.onProgress(percent);
		}
	}

	/** called by the progress dialog window or the lengthy operation thread to signal that the operation is canceled */
	public void cancel() {
		successful = false;
		if (SwingUtilities.isEventDispatchThread()) {
			if (dlgWindow != null) {
				dlgWindow.onCancel();
			}
			for (Listener L : listeners) {
				L.onProgressCanceled();
			}
			hideProgressDialogWindow();
		} else {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					cancel();
				}
			});
		}
	}

	/**
	 * called by the lengthy operation thread to signal that the operation is done and
	 * successful
	 */
	public void doneSuccessful() {
		successful = true;
		if (SwingUtilities.isEventDispatchThread()) {
			if (dlgWindow != null) {
				dlgWindow.onSuccess();
			}
			for (Listener L : listeners) {
				L.onProgressEnd(successful);
			}
			if (guiTimer != null) {
				guiTimer.stop();
			}
		} else {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					doneSuccessful();
				}
			});
		}
	}

	/** called by the lengthy operation thread to signal that the operation has failed */
	public void doneFailed(final String errorMsg) {
		successful = false;
		if (SwingUtilities.isEventDispatchThread()) {
			if (dlgWindow != null) {
				dlgWindow.onFailed(errorMsg);
			}
			for (Listener L : listeners) {
				L.onProgressEnd(successful);
			}
			if (guiTimer != null) {
				guiTimer.stop();
			}
		} else {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					doneFailed(errorMsg);
				}
			});
		}
	}

	private void hideProgressDialogWindow() {
		if (dlgWindow != null) {
			dlgWindow.dispose();
			dlgWindow = null;
			for (Listener L : listeners) {
				L.onProgressDialogDismissed(successful);
			}
			if (guiTimer != null) {
				guiTimer.stop();
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
	 */
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == guiTimer) {
			for (Listener L : listeners) {
				L.onProgressUpdate();
			}
		}
	}

	/** dialog to show ongoing progress */
	private class DlgWindow extends JDialog implements ActionListener {

		private JButton buttonOK = null;
		private JButton buttonCancel = null;
		private JLabel titleLabel = null;
		private JLabel progressLabel = null;
		private JProgressBar progressBar;
		private Container parent;

		public DlgWindow(Container parent) {
			super();
			this.parent = parent;
			initGUI();
		}

		private void initGUI() {
			JPanel panel = new JPanel();
			panel.setLayout(new RigidLineLayout());
			panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

			titleLabel = new JLabel("Working... this may take a few minutes.");
			titleLabel.setFont(new Font("Arial", Font.PLAIN, 12));
			titleLabel.setPreferredSize(new Dimension(300, 75));
			panel.add(titleLabel);

			progressLabel = new JLabel("W");
			progressLabel.setFont(new Font("Arial", Font.PLAIN, 12));
			panel.add(progressLabel);

			progressBar = new JProgressBar(0, 100);
			panel.add(progressBar);

			JPanel buttonPanel = new JPanel();
			buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
			buttonOK = new JButton("OK");
			buttonOK.addActionListener(this);
			buttonPanel.add(buttonOK);
			buttonCancel = new JButton("Cancel");
			buttonCancel.addActionListener(this);
			buttonPanel.add(buttonCancel);
			panel.add(buttonPanel);

			setContentPane(panel);
			pack();

			Dimension size = parent.getSize();
			Point pos = parent.getLocationOnScreen();

			setBounds(pos.x + (size.width / 2) - (getWidth() / 2),
					pos.y + (size.height / 2) - (getHeight() / 2), getWidth(),
					getHeight());
			setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
			setResizable(false);

			buttonOK.setEnabled(false);
			progressLabel.setText("");
		}

		/**
		 * @return the progressBar
		 */
		public JProgressBar getProgressBar() {
			return progressBar;
		}

		public void updateText(String text) {
			progressLabel.setText("<html>" + text + "</html>");
		}

		public void onProgress(double percent) {
			progressBar.setValue((int) (percent * 100));
		}

		public void start() {
			setTitle(operationVerb);
			titleLabel.setText(operationVerb + "... this may take a few minutes.");
			progressBar.setValue(0);
			buttonOK.setEnabled(false);
			buttonCancel.setEnabled(true);
			dlgWindow.setVisible(true);
		}

		public void onSuccess() {
			buttonOK.setEnabled(true);
			buttonCancel.setEnabled(false);
			progressLabel.setText(operationVerb + " successful.");
		}

		public void onFailed(String errorMsg) {
			buttonOK.setEnabled(true);
			buttonCancel.setEnabled(false);
			if (errorMsg.length() > 0) {
				progressLabel.setText(operationVerb + " failed: " + errorMsg);
			} else {
				progressLabel.setText(operationVerb + " failed.");
			}
		}

		public void onCancel() {
			// nothing
		}

		/*
		 * (non-Javadoc)
		 * @see
		 * java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent
		 * )
		 */
		public void actionPerformed(ActionEvent e) {
			if (e.getSource() == buttonOK) {
				hideProgressDialogWindow();
			} else if (e.getSource() == buttonCancel) {
				cancel();
			}
		}

	} // DlgWindow

	public interface Listener {
		/** called in the Swing thread when the progress dialog appears */
		void onProgressStart();

		/**
		 * during the lengthy operation, called in the Swing thread from a
		 * timer in regular intervals
		 */
		void onProgressUpdate();

		/**
		 * called in the Swing thread when the user canceled the lengthy
		 * operation
		 */
		void onProgressCanceled();

		/**
		 * called in the Swing thread when the lengthy process has ended; the
		 * progress dialog is still visible, waiting for the user to dismiss it.
		 */
		void onProgressEnd(boolean success);

		/**
		 * called in the Swing thread when the user dismissed the progress
		 * dialog.
		 */
		void onProgressDialogDismissed(boolean success);
	}

}
