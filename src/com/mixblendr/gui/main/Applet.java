/**
 *
 */
package com.mixblendr.gui.main;

import javax.swing.JApplet;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import com.mixblendr.util.Debug;

/**
 * The main GUI as an applet.
 * 
 * @author Florian Bomers
 */
public class Applet extends JApplet {

	protected Main main;

	protected Exception exception;

	@Override
	public String getParameter(String arg) {
		String ret = super.getParameter(arg);
		if (ret == null) {
			return "";
		}
		return ret;
	}

	/**
	 * Method called by browser before display of the applet.
	 */
	@Override
	public void init() {
		exception = null;
		try {
			System.out.println("Start " + Main.NAME + " " + Main.VERSION);
			
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					JLabel label = new JLabel("loading, please wait...");
					label.setHorizontalAlignment(JLabel.CENTER);
					label.setOpaque(true);
					Applet.this.setContentPane(label);
				}
			});

		} catch (Exception e) {
			exception = e;
		}
	}

	/** called by the browser upon starting the applet */
	@Override
	public void start() {
		if (exception != null) {
			Debug.displayErrorDialog(this, exception, "at startup");
		} else {
			// the Java Plugin 6.0 kills the VM if init() or start() takes more than 30 seconds or so.
			// therefore, execute all the init stuff (which will cause loading of classes, etc.)
			// asynchronously
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					try {
						Performance.setDefaultUI();
						Performance.preload();
						main = new Main();

						String url = getParameter("URL");
						String redirectURL = getParameter("REDIRECT_URL");
						String defaultTempo = getParameter("DEFAULT_TEMPO");
						String loadURL = getParameter("LOAD_DIR_URL");
						main.createGUI();
						main.createEngine();
						main.getProgressDialog().setSaveToServerScriptURL(url);
						main.getProgressDialog().setLoadFromServerURL(loadURL);
						main.setRedirectAfterPublishURL(redirectURL);
						main.setApplet(Applet.this);

						try {
							if (defaultTempo != null && defaultTempo.length() > 0) {
								double tempo = Double.parseDouble(defaultTempo);
								main.setDefaultTempo(tempo);
							}
						} catch (NumberFormatException e) {
						}
						
						Applet.this.setContentPane(main.getMasterPanel());

						main.start();
					} catch (Exception e) {
						Debug.displayErrorDialogAsync(Applet.this, e, "at startup");
					}
				}
			});
		}
	}

	/** called by the browser when the user navigates away from this page */
	@Override
	public void stop() {
		if (main != null) {
			main.stop();
		}
	}

	/** called by the browser when removing this applet completely */
	@Override
	public void destroy() {
		if (main != null) {
			main.close();
		}
	}

}
