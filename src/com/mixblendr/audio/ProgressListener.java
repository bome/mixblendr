/**
 *
 */

package com.mixblendr.audio;

/**
 * Listener to report progress
 * @author Florian Bomers
 */
public interface ProgressListener {
	/**
	 * Called regularly during a lengthy operation. 
	 * @param value
	 * @param max
	 */
	void onProgress(int value, int max);
}
