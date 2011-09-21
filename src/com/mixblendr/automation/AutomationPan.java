/**
 *
 */
package com.mixblendr.automation;

import com.mixblendr.audio.AudioState;
import com.mixblendr.audio.AudioTrack;
import com.mixblendr.audio.AutomationManager;
import com.mixblendr.audio.AutomationObjectDouble;

/**
 * An instance of an automation object that changes panorama/balance
 * 
 * @author Florian Bomers
 */
public class AutomationPan extends AutomationObjectDouble {
	private static final String XML_ELEMENT_NAME = "Pan";

	static {
		AutomationManager.registerXML(AutomationPan.class, XML_ELEMENT_NAME);
	}

	/**
	 * Create an instance with default values, should only be used before
	 * xml import.
	 */
	public AutomationPan() {
		super(null, XML_ELEMENT_NAME, 0, 1.0);
	}

	/**
	 * Create a new volume automation object
	 * 
	 * @param state
	 * @param pan [-1..0..+1]
	 * @param startSample the sample time when to execute this pan change
	 */
	public AutomationPan(AudioState state, double pan, long startSample) {
		super(state, XML_ELEMENT_NAME, startSample, pan);
	}

	/**
	 * @return the pan [-1..0..+1]
	 */
	public double getPan() {
		return value;
	}

	/**
	 * Change the track's volume to this object's stored volume.
	 * 
	 * @see com.mixblendr.audio.AutomationObject#executeImpl(com.mixblendr.audio.AudioTrack)
	 */
	@Override
	protected void executeImpl(AudioTrack track) {
		track.setBalance(value);
	}

	/**
	 * @return a string representation of this object (mainly for debugging
	 *         purposes)
	 */
	@Override
	public String toString() {
		return super.toString() + ", linear pan=" + value;
	}

}
