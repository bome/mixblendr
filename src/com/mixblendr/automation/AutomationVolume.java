/**
 *
 */
package com.mixblendr.automation;

import com.mixblendr.audio.AudioState;
import com.mixblendr.audio.AudioTrack;
import com.mixblendr.audio.AutomationManager;
import com.mixblendr.audio.AutomationObjectDouble;

/**
 * An instance of an automation object that changes volume
 * 
 * @author Florian Bomers
 */
public class AutomationVolume extends AutomationObjectDouble {
	private static final String XML_ELEMENT_NAME = "Volume";

	static {
		AutomationManager.registerXML(AutomationVolume.class, XML_ELEMENT_NAME);
	}

	/**
	 * Create an instance with default values, should only be used before
	 * xml import.
	 */
	public AutomationVolume() {
		super(null, XML_ELEMENT_NAME, 0, 1.0);
	}

	/**
	 * Create a new volume automation object
	 * 
	 * @param state
	 * @param volume the linear volume [0..1]
	 * @param startSample the sample time when to execute this volume change
	 */
	public AutomationVolume(AudioState state, double volume, long startSample) {
		super(state, XML_ELEMENT_NAME, startSample, volume);
	}

	/**
	 * @return the linear volume [0..1]
	 */
	public double getVolume() {
		return value;
	}

	/**
	 * Change the track's volume to this object's stored volume.
	 * 
	 * @see com.mixblendr.audio.AutomationObject#executeImpl(com.mixblendr.audio.AudioTrack)
	 */
	@Override
	protected void executeImpl(AudioTrack track) {
		track.setVolume(value);
		// Debug.debug(toString());
	}

	/**
	 * @return a string representation of this object (mainly for debugging
	 *         purposes)
	 */
	@Override
	public String toString() {
		return super.toString() + ", linear vol=" + value;
	}
}
