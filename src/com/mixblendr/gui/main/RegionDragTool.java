/**
 *
 */
package com.mixblendr.gui.main;

import static com.mixblendr.util.Debug.debug;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.net.URL;
import java.util.List;

/**
 * A utility class used by the track panel for drag'n'drop of regions.
 * 
 * @author Florian Bomers
 */
class RegionDragTool {

	private static final boolean DEBUG = false;

	private static DataFlavor urlFlavor;

	static {
		try {
			urlFlavor = new DataFlavor(
					"application/x-java-url; class=java.net.URL");
		} catch (ClassNotFoundException cnfe) {
			cnfe.printStackTrace();
		}
	}

	/** result for drag/drop types */
	public enum Type {
		T_NONE, T_URL, T_FILE, T_AUDIO_REGION;
	}

	private static boolean isUrlType(DataFlavor flavor) {
		return flavor.equals(urlFlavor);
	}

	private static boolean isRegionType(DataFlavor flavor) {
		return flavor.equals(RegionTransferable.regionFlavor);
	}

	private static boolean isFileType(DataFlavor flavor) {
		return flavor.isFlavorJavaFileListType();
	}

	/**
	 * given the transfer flavors, return the type that we can accept, or
	 * T_NONE, if nothing is supported
	 */
	public static Type getTransferType(DataFlavor[] transferFlavors) {
		int i = 1;
		for (DataFlavor df : transferFlavors) {
			try {
				if (DEBUG) {
					debug("DataFlavor " + i + ": " + df);
					i++;
				}
				if (isUrlType(df)) {
					return Type.T_URL;
				}
				if (isRegionType(df)) {
					return Type.T_AUDIO_REGION;
				}
				if (isFileType(df)) {
					return Type.T_FILE;
				}
			} catch (Exception e) {
				if (DEBUG) e.printStackTrace();
				debug(e);
			}
		}
		return Type.T_NONE;
	}

	/**
	 * Return the URL from this transferable. Make sure that getTransferType
	 * returned T_URL! Returns null if there is no valid URL.
	 */
	@SuppressWarnings("unchecked")
	public static URL getURL(Transferable t) {
		try {
			if (t.isDataFlavorSupported(urlFlavor)) {
				Object td = t.getTransferData(urlFlavor);
				if (td != null && (td instanceof URL)) {
					if (DEBUG) {
						debug("getURL: directly got this URL:" + td);
					}
					return (URL) td;
				}
			} else if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
				Object fl = t.getTransferData(DataFlavor.javaFileListFlavor);
				if (fl instanceof List<?>) {
					List<File> l = (List<File>) fl;
					if (l.size() > 0) {
						String f = l.get(0).getAbsolutePath().replace('\\', '/');
						return new URL("file:///"+f);
					}
				}
			}
		} catch (Exception e) {
			debug(e);
			if (DEBUG) e.printStackTrace();
		}

		int i = 1;
		for (DataFlavor df : t.getTransferDataFlavors()) {
			if (DEBUG) {
				debug("getURL: flavor " + i + ": " + df);
				i++;
			}
			try {
				if (isUrlType(df)) {
					Object td = t.getTransferData(df);
					if (td instanceof URL) {
						return (URL) td;
					}
					if (DEBUG) {
						if (td == null) {
							debug("URL transferdata is null!");
						} else {
							debug("URL is of type " + td.getClass() + ": " + td);
						}
					}
				} else if (DEBUG && df.isFlavorTextType()) {
					Object td = t.getTransferData(df);
					debug(" -> string='" + td + "'");
				}
			} catch (Exception e) {
				debug(e);
				if (DEBUG) e.printStackTrace();
			}
		}
		return null;
	}

	/**
	 * Return the region graph from this transferable. Make sure that
	 * getTransferType returned T_AUDIO_REGION! Returns null if there is no
	 * valid region.
	 */
	public static RegionGraph getRegionGraph(Transferable t) {
		try {
			if (t instanceof RegionTransferable) {
				return ((RegionTransferable) t).getAudioRegion();
			}
			Object td = t.getTransferData(RegionTransferable.regionFlavor);
			if (td != null && td instanceof RegionGraph) {
				if (DEBUG) {
					debug("getRegionGraph: got " + td);
				}
				return (RegionGraph) td;
			}
		} catch (Exception e) {
			debug(e);
			if (DEBUG) e.printStackTrace();
		}
		if (DEBUG) {
			debug("getRegionGraph: could not get an instance.");
		}
		return null;
	}

}
