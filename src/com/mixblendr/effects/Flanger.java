/**
 *
 */
package com.mixblendr.effects;

import java.awt.Font;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.event.ChangeEvent;

import org.tritonus.share.sampled.FloatSampleBuffer;
import org.w3c.dom.Element;

import com.mixblendr.audio.*;
import com.mixblendr.util.GUIUtils;
import com.mixblendr.util.XmlPersistent;

import static com.mixblendr.util.Debug.*;

/**
 * A flanger effect.
 * 
 * @author Florian Bomers
 */
public class Flanger extends GUIEffectsBase implements XmlPersistent {

	private static final boolean DEBUG_FLANGER = false;

	/**
	 * the mean delay time, around which the delay time oscillates, in
	 * milliseconds
	 */
	private double delayTimeMillis;
	/**
	 * a relative percentage determining the amplitude of the oscillator.<br>
	 * An amplitude of 0 makes this effect a regular delay, a value of 1 will
	 * double the delay at its peak, and have half the delay at its negative
	 * peak.
	 */
	private double amplitude;
	/** freq in hertz */
	private double freq;
	private double feedback;
	private double balance;

	// derived values
	private double delayTimeSamples;

	// runtime state
	private FloatSampleBuffer delayBuffer;
	private double delayBufferReadPos;
	private double delayBufferWritePos;
	private double lfoInc;
	private double lfoCurr;

	// automation support
	private static AutomationHandler delayTimeHandler = AutomationManager.getHandler(DelayTimeAutomation.class);
	private static AutomationHandler amplitudeHandler = AutomationManager.getHandler(AmplitudeAutomation.class);
	private static AutomationHandler freqHandler = AutomationManager.getHandler(FreqAutomation.class);
	private static AutomationHandler feedbackHandler = AutomationManager.getHandler(FeedbackAutomation.class);
	private static AutomationHandler balanceHandler = AutomationManager.getHandler(BalanceAutomation.class);

	static {
		AutomationManager.registerXML(DelayTimeAutomation.class, DelayTimeAutomation.XML_ELEMENT_NAME);
		AutomationManager.registerXML(AmplitudeAutomation.class, AmplitudeAutomation.XML_ELEMENT_NAME);
		AutomationManager.registerXML(FreqAutomation.class, FreqAutomation.XML_ELEMENT_NAME);
		AutomationManager.registerXML(FeedbackAutomation.class, FeedbackAutomation.XML_ELEMENT_NAME);
		AutomationManager.registerXML(BalanceAutomation.class, BalanceAutomation.XML_ELEMENT_NAME);
	}

	/** create a new instance of the Delay effect */
	public Flanger() {
		super("Flanger");
	}

	/**
	 * @return the delay time in milliseconds
	 */
	public double getDelayTimeMillis() {
		return delayTimeMillis;
	}

	/**
	 * Set delay time in millis, recalculate minDelay and maxDelay and fade
	 * buffer for smooth transition to this new delay time.
	 * 
	 * @param delayTimeMillis the delay time in millis to set
	 */
	public void setDelayTimeMillis(double delayTimeMillis) {
		if (state == null) return;
		synchronized (lock) {
			this.delayTimeMillis = delayTimeMillis;
			delayTimeSamples = state.millis2sample(delayTimeMillis);
			if (DEBUG_FLANGER) debug("delayTimeSamples = " + delayTimeSamples);
		}
	}

	/**
	 * @return the amplitude, [0...1]
	 */
	public double getAmplitude() {
		return amplitude;
	}

	/**
	 * @param amplitude the amplitude to set [0..1]
	 */
	public void setAmplitude(double amplitude) {
		this.amplitude = amplitude;
	}

	/**
	 * @return the frequency in Hz [1...30]
	 */
	public double getFrequency() {
		return freq;
	}

	/**
	 * @param frequency the frequency to set [1..30]
	 */
	public void setFrequency(double frequency) {
		if (frequency < 0.00001) {
			frequency = 0.00001;
		}
		this.freq = frequency;
		// set up LFO
		// ramp:
		// - period = samplerate/freq
		// - one ramp is half period
		// - LFO oscillates between -1...0...+1
		// - therefore increase per sample is 2 / (half period)
		double newLfoInc = 4 * freq / state.getSampleRate();
		if (lfoInc < 0) {
			lfoInc = -newLfoInc;
		} else {
			lfoInc = newLfoInc;
		}
		if (DEBUG_FLANGER) debug("lfoInc = " + lfoInc);
	}

	/**
	 * @return the feedback [0..1]
	 */
	public double getFeedback() {
		return feedback;
	}

	/**
	 * @param feedback the feedback to set [0..1]
	 */
	public void setFeedback(double feedback) {
		this.feedback = feedback;
	}

	/**
	 * @return the balance [-1...0...+1]
	 */
	public double getBalance() {
		return balance;
	}

	/**
	 * @param balance the balance to set [-1...0...+1]
	 */
	public void setBalance(double balance) {
		this.balance = balance;
	}

	// --------------------------------- AudioEffect methods

	private final static int MAX_FLANGER_BUFFER_SIZE_SAMPLES = 44100;

	@Override
	public void initImpl() {
		if (state == null) return;
		if (delayBuffer == null) {
			delayBuffer = new FloatSampleBuffer(state.getChannels(),
					MAX_FLANGER_BUFFER_SIZE_SAMPLES, state.getSampleRate());
		}
		// default values
		setDelayTimeMillis(4);
		setAmplitude(0.5);
		setFrequency(1);
		setFeedback(0.6);
		setBalance(0.0);
	}

	@Override
	public void exitImpl() {
		delayBuffer = null;
	}

	/**
	 * given the current lfo value, return the current delay time offset
	 * 
	 * @param lfo the current lfo value [-1...0...+1]
	 * @return the delay offset to the delayTimeSample
	 */
	private final double getDelayOffsetSamples(double lfo) {
		return lfo * amplitude * amplitude * (delayTimeSamples / 2);
	}

	/**
	 * the actual delay processor: feed the current buffer to the circular delay
	 * buffer, and add the current delay buffer contents to the output buffer
	 */
	@Override
	public boolean process(long samplePos, FloatSampleBuffer buffer,
			int offset, int sampleCount) {
		// sanity
		if (delayBuffer == null || sampleCount == 0) return false;
		synchronized (lock) {
			double srcVol;
			double delayVol;
			if (balance < 0) {
				srcVol = 1.0;
				delayVol = (1 + balance);
			} else {
				srcVol = 1 - balance;
				delayVol = 1.0;
			}

			int delayBufferCount = delayBuffer.getSampleCount();

			// by which read/write pos are increased for every sample, to
			// account for non-integral delay times
			double writePosInc = ((int) delayTimeSamples) / delayTimeSamples;
			double readPosInc = writePosInc;

			// local variables as optimization
			double writePos = delayBufferWritePos;
			double readPos = delayBufferReadPos;

			// if delay time has changed, read pos (dependent on write Pos)
			// needs
			// to slowly drift away/towards writePos so that it will reach the
			// target delay eventually
			double targetReadPos = writePos - delayTimeSamples
					+ getDelayOffsetSamples(lfoCurr);
			if (targetReadPos < 0) {
				targetReadPos += delayBufferCount;
			}
			if (readPos == writePos) {
				// at beginning, no transition necessary
				readPos = targetReadPos;
			} else {
				readPosInc += (targetReadPos - readPos) / sampleCount;
				// do not increase too much, otherwise will be audible
				// high-pitched...
				if (readPosInc > 3) {
					readPosInc = 1.2;
				}
			}

			double startReadPos = readPos;
			double lLfoCurr = lfoCurr;
			double lLfoInc = lfoInc;

			// TODO: linear interpolation

			for (int c = 0; c < buffer.getChannelCount(); c++) {
				int thisCount = sampleCount;
				float[] delay = delayBuffer.getChannel(c);
				float[] inout = buffer.getChannel(c);
				int thisOffset = offset;

				// re-initialize for every channel
				lLfoCurr = lfoCurr;
				lLfoInc = lfoInc;
				writePos = delayBufferWritePos;
				readPos = startReadPos;

				while (thisCount > 0) {
					// the actual delay line!
					float io = inout[thisOffset];
					int writeOffset = (int) writePos;
					int readOffset = (int) (readPos + getDelayOffsetSamples(lLfoCurr));
					if (readOffset < 0) {
						readOffset += delayBufferCount;
					}
					if (readOffset >= delayBufferCount) {
						readOffset -= delayBufferCount;
					}

					float dr = delay[readOffset];
					inout[thisOffset] = (float) (dr * delayVol + io * srcVol);
					delay[writeOffset] = (float) ((dr + io) * feedback);
					thisOffset++;
					thisCount--;
					// update write position
					writePos += writePosInc;
					if ((int) writePos >= delayBufferCount) {
						writePos -= delayBufferCount;
					}
					// update read position
					readPos += readPosInc;
					if ((int) readPos >= delayBufferCount) {
						readPos -= delayBufferCount;
					}
					// update lfo
					lLfoCurr += lLfoInc;
					if (lLfoCurr >= 1.0 || lLfoCurr <= -1.0) {
						lLfoInc = -lLfoInc;
					}
				}
			}
			// store state for next audio block
			lfoCurr = lLfoCurr;
			lfoInc = lLfoInc;
			delayBufferReadPos = readPos;
			delayBufferWritePos = writePos;

			return true;
		}
	}

	// --------------------------------- GUI stuff

	private SliderStrip sDelayTime;
	private SliderStrip sAmplitude;
	private SliderStrip sFreq;
	private SliderStrip sFeedback;
	private SliderStrip sBalance;
	private boolean guiInited = false;
	/**
	 * if this flag is non-zero, controls are currently set programmatically
	 * rather than from user interaction
	 */
	private int noUpdate = 0;

	@Override
	protected void initGUI(JPanel main) {
		main.setLayout(new BoxLayout(main, BoxLayout.PAGE_AXIS));
		main.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		// Title
		JLabel title = GUIUtils.createLabel("Flanger Effect",
				SwingConstants.CENTER);
		title.setFont(title.getFont().deriveFont(Font.BOLD));
		main.add(title);
		main.add(new JSeparator(SwingConstants.HORIZONTAL));

		main.add((sDelayTime = new SliderStrip("Delay:", -10, 28, 2, "short",
				"long")));
		main.add((sAmplitude = new SliderStrip("Amplitude:", 0, 100, 50,
				"small", "large")));
		main.add((sFreq = new SliderStrip("Frequency:", -19, 58, 20, "slow",
				"fast")));
		main.add((sFeedback = new SliderStrip("Feedback:", 0, 100, 50, "none",
				"full")));
		main.add((sBalance = new SliderStrip("Balance:", -100, 100, 0, "dry",
				"wet")));
		guiInited = true;


		// init sliders and labels
		updateGUIDelayTime();
		updateGUIDelayTimeLabel();
		updateGUIAmplitude();
		updateGUIAmplitudeLabel();
		updateGUIFreq();
		updateGUIFreqLabel();
		updateGUIFeedback();
		updateGUIFeedbackLabel();
		updateGUIBalance();
		updateGUIBalanceLabel();
	}

	private static final double TIME_ZERO = 1.0;
	private static final double MIN_TIME = 0.5;
	private static final double MAX_TIME = 15;

	// ------------ DELAY TIME

	/** update the GUI with the current delay time */
	protected void updateGUIDelayTime() {
		if (!guiInited) return;
		int index = 0;
		if (delayTimeMillis < TIME_ZERO) {
			index = (int) Math.round(((delayTimeMillis - TIME_ZERO) * sDelayTime.slider.getMinimum())
					/ (MIN_TIME - TIME_ZERO));
		} else {
			index = (int) Math.round(((delayTimeMillis - TIME_ZERO) * sDelayTime.slider.getMaximum())
					/ (MAX_TIME - TIME_ZERO));
		}
		noUpdate++;
		try {
			// will cause change event and update the label
			sDelayTime.slider.setValue(index);
		} finally {
			noUpdate--;
		}
	}

	/**
	 * read the current value from the slider and set the internal value
	 * accordingly
	 */
	protected void updateDelayTimeFromGUI() {
		if (!guiInited) return;
		int index = sDelayTime.slider.getValue();
		if (index < 0) {
			setDelayTimeMillis(TIME_ZERO - index * (TIME_ZERO - MIN_TIME)
					/ sDelayTime.slider.getMinimum());
		} else {
			setDelayTimeMillis(TIME_ZERO + index * (MAX_TIME - TIME_ZERO)
					/ sDelayTime.slider.getMaximum());
		}
	}

	/** update the label of the with the current delay time */
	private void updateGUIDelayTimeLabel() {
		if (!guiInited) return;
		String s = Double.toString((Math.round(delayTimeMillis * 100)) / 100.0);
		if (s.length() > 2 && s.charAt(s.length() - 2) == '.') s += "0";
		sDelayTime.label.setText(s + " ms");
	}

	// ------------ AMPLITUDE

	/** update the GUI with the current amplitude */
	protected void updateGUIAmplitude() {
		if (!guiInited) return;
		noUpdate++;
		try {
			// will cause change event and update the label
			sAmplitude.slider.setValue((int) (amplitude * 100.0));
		} finally {
			noUpdate--;
		}
	}

	/**
	 * read the current value from the slider and set the internal value
	 * accordingly
	 */
	protected void updateAmplitudeFromGUI() {
		if (!guiInited) return;
		int index = sAmplitude.slider.getValue();
		setAmplitude(index / 100.0);
	}

	/** update the label of the with the current amplitude */
	private void updateGUIAmplitudeLabel() {
		if (!guiInited) return;
		sAmplitude.label.setText(Integer.toString(sAmplitude.slider.getValue())
				+ " %");
	}

	// ------------ FREQUENCY

	private static final double FREQ_ZERO = 1.0;
	private static final double MIN_FREQ = 0.05;
	private static final double MAX_FREQ = 30;

	/** update the GUI with the current freq */
	protected void updateGUIFreq() {
		if (!guiInited) return;
		int index = 0;
		if (freq < FREQ_ZERO) {
			index = (int) Math.round(((freq - FREQ_ZERO) * sFreq.slider.getMinimum())
					/ (MIN_FREQ - FREQ_ZERO));
		} else {
			index = (int) Math.round(((freq - FREQ_ZERO) * sFreq.slider.getMaximum())
					/ (MAX_FREQ - FREQ_ZERO));
		}
		noUpdate++;
		try {
			// will cause change event and update the label
			sFreq.slider.setValue(index);
		} finally {
			noUpdate--;
		}
	}

	/**
	 * read the current value from the slider and set the internal value
	 * accordingly
	 */
	protected void updateFreqFromGUI() {
		if (!guiInited) return;
		int index = sFreq.slider.getValue();
		if (index < 0) {
			setFrequency(FREQ_ZERO - index * (FREQ_ZERO - MIN_FREQ)
					/ sFreq.slider.getMinimum());
		} else {
			setFrequency(FREQ_ZERO + index * (MAX_FREQ - FREQ_ZERO)
					/ sFreq.slider.getMaximum());
		}
	}

	/** update the label of the with the current freq */
	private void updateGUIFreqLabel() {
		if (!guiInited) return;
		String s = Double.toString((Math.round(freq * 100)) / 100.0);
		if (s.length() > 2 && s.charAt(s.length() - 2) == '.') s += "0";
		sFreq.label.setText(s + " Hz");
	}

	// ------------ FEEDBACK

	/** update the GUI with the current feedback */
	protected void updateGUIFeedback() {
		if (!guiInited) return;
		// will cause change event and update the label
		noUpdate++;
		try {
			sFeedback.slider.setValue((int) (feedback * 100.0));
		} finally {
			noUpdate--;
		}
	}

	/**
	 * read the current value from the slider and set the internal value
	 * accordingly
	 */
	protected void updateFeedbackFromGUI() {
		if (!guiInited) return;
		int index = sFeedback.slider.getValue();
		setFeedback(index / 100.0);
	}

	/** update the label of the with the current feedback */
	private void updateGUIFeedbackLabel() {
		if (!guiInited) return;
		sFeedback.label.setText(Integer.toString(sFeedback.slider.getValue())
				+ " %");
	}

	// ------------ BALANCE

	/** update the GUI with the current balance */
	protected void updateGUIBalance() {
		if (!guiInited) return;
		// will cause change event and update the label
		noUpdate++;
		try {
			sBalance.slider.setValue((int) (balance * 100.0));
		} finally {
			noUpdate--;
		}
	}

	/**
	 * read the current value from the slider and set the internal value
	 * accordingly
	 */
	protected void updateBalanceFromGUI() {
		if (!guiInited) return;
		int index = sBalance.slider.getValue();
		setBalance(index / 100.0);
	}

	/** update the label of the with the current balance */
	private void updateGUIBalanceLabel() {
		if (!guiInited) return;
		sBalance.label.setText(Integer.toString(sBalance.slider.getValue())
				+ " %");
	}

	// --------------------------------- interface MouseListener

	/**
	 * if automation is currently enabled, add an appropriate automation event,
	 * depending on the given GUI control
	 */
	private void addAutomationEvent(Object src) {
		if ((track != null) && track.isAutomationEnabled()) {
			if (src == sDelayTime.slider) {
				track.addAutomationObject(new DelayTimeAutomation(this));
			} else if (src == sAmplitude.slider) {
				track.addAutomationObject(new AmplitudeAutomation(this));
			} else if (src == sFreq.slider) {
				track.addAutomationObject(new FreqAutomation(this));
			} else if (src == sFeedback.slider) {
				track.addAutomationObject(new FeedbackAutomation(this));
			} else if (src == sBalance.slider) {
				track.addAutomationObject(new BalanceAutomation(this));
			}
		}
	}

	/** set tracking for the selected GUI object on or off. */
	private void setTracking(Object src, boolean on) {
		if (src == sDelayTime.slider) {
			delayTimeHandler.setTracking(track, on);
			if (DEBUG_FLANGER) debug("Delay Time tracking: " + on);
		} else if (src == sAmplitude.slider) {
			amplitudeHandler.setTracking(track, on);
			if (DEBUG_FLANGER) debug("Amplitude tracking: " + on);
		} else if (src == sFreq.slider) {
			freqHandler.setTracking(track, on);
			if (DEBUG_FLANGER) debug("Frequency tracking: " + on);
		} else if (src == sFeedback.slider) {
			feedbackHandler.setTracking(track, on);
			if (DEBUG_FLANGER) debug("Feedback tracking: " + on);
		} else if (src == sBalance.slider) {
			balanceHandler.setTracking(track, on);
			if (DEBUG_FLANGER) debug("Balance tracking: " + on);
		}
		// add initial automation state
		if (on) {
			addAutomationEvent(src);
		}
	}

	/**
	 * called when the user clicks on a slider. In response, notify the engine
	 * that we're tracking this automation object.
	 */
	@Override
	public void mousePressed(MouseEvent e) {
		setTracking(e.getSource(), true);
	}

	/**
	 * called when the user releases the mouse button from a slider. Notify the
	 * engine that we're not tracking this automation object anymore.
	 */
	@Override
	public void mouseReleased(MouseEvent e) {
		setTracking(e.getSource(), false);
	}

	// ----------------------------------------- interface ChangeListener

	/**
	 * Called when the user or the implementation moves a slider. Update the
	 * slider labels. If not currently set by the implementation, update the
	 * internal value. If automation is active, create an automation object and
	 * add it to the track.
	 */
	@Override
	public void stateChanged(ChangeEvent e) {
		Object src = e.getSource();
		if (src == sDelayTime.slider) {
			if (noUpdate == 0) {
				updateDelayTimeFromGUI();
			}
			updateGUIDelayTimeLabel();
		} else if (src == sAmplitude.slider) {
			if (noUpdate == 0) {
				updateAmplitudeFromGUI();
			}
			updateGUIAmplitudeLabel();
		} else if (src == sFreq.slider) {
			if (noUpdate == 0) {
				updateFreqFromGUI();
			}
			updateGUIFreqLabel();
		} else if (src == sFeedback.slider) {
			if (noUpdate == 0) {
				updateFeedbackFromGUI();
			}
			updateGUIFeedbackLabel();
		} else if (src == sBalance.slider) {
			if (noUpdate == 0) {
				updateBalanceFromGUI();
			}
			updateGUIBalanceLabel();
		}
		if (noUpdate == 0) {
			addAutomationEvent(src);
		}
	}
	
	// PERSISTENCE

	@Override
	public Element xmlExport(Element element) {
		element = super.xmlExport(element);
		element.setAttribute("DelayTimeMillis", String.valueOf(delayTimeMillis));
		element.setAttribute("Amplitude", String.valueOf(amplitude));
		element.setAttribute("Frequency", String.valueOf(freq));
		element.setAttribute("Feedback", String.valueOf(feedback));
		element.setAttribute("Balance", String.valueOf(balance));
		return element;
	}

	@Override
	public void xmlImport(Element element) throws Exception {
		super.xmlImport(element);
		String val = element.getAttribute("DelayTimeMillis");
		if (val.length() > 0) {
			setDelayTimeMillis(Double.parseDouble(val));
		}
		val = element.getAttribute("Amplitude");
		if (val.length() > 0) {
			setAmplitude(Double.parseDouble(val));
		}
		val = element.getAttribute("Frequency");
		if (val.length() > 0) {
			setFrequency(Double.parseDouble(val));
		}
		val = element.getAttribute("Feedback");
		if (val.length() > 0) {
			setFeedback(Double.parseDouble(val));
		}
		val = element.getAttribute("Balance");
		if (val.length() > 0) {
			setBalance(Double.parseDouble(val));
		}
	}


	// ----------------------------------------- AUTOMATION

	/** the automation object to record a change in delay time */
	public static class DelayTimeAutomation extends AutomationObjectDouble {
		private static final String XML_ELEMENT_NAME = "FlangerDelayTime";
		private Flanger target;

		/**
		 * Create an instance with default values, should only be used before
		 * xml import.
		 */
		public DelayTimeAutomation() {
			super(null, XML_ELEMENT_NAME, 0, 20.0);
		}

		/**
		 * Create a delay time automation object capturing the current playback
		 * time and the current delay time.
		 */
		public DelayTimeAutomation(Flanger target) {
			super(target.state, XML_ELEMENT_NAME,
					target.state.getSamplePosition(),
					target.getDelayTimeMillis());
			this.target = target;
		}

		/*
		 * (non-Javadoc)
		 * @see
		 * com.mixblendr.audio.AutomationObject#executeImpl(com.mixblendr.audio
		 * .AudioTrack)
		 */
		@Override
		protected void executeImpl(AudioTrack aTrack) {
			if (target == null) {
				// try to derive the target
				if (owner != null && owner.getOwner() != null) {
					target = (Flanger) owner.getOwner().getEffect(Flanger.class);
				}
				if (target == null || target.state == null) return;
			}
			target.setDelayTimeMillis(value);
			target.updateGUIDelayTime();
		}

		/**
		 * @return a string representation of this object (mainly for debugging
		 *         purposes)
		 */
		@Override
		public String toString() {
			return super.toString() + ", delay time=" + value + "ms";
		}
	}

	/** the automation object to record a change in amplitude */
	public static class AmplitudeAutomation extends AutomationObjectDouble {
		private static final String XML_ELEMENT_NAME = "FlangerAmplitude";
		private Flanger target;

		/**
		 * Create an instance with default values, should only be used before
		 * xml import.
		 */
		public AmplitudeAutomation() {
			super(null, XML_ELEMENT_NAME, 0, 0.5);
		}

		/**
		 * Create a amplitude automation object capturing the current playback
		 * time and the current amplitude.
		 */
		public AmplitudeAutomation(Flanger target) {
			super(target.state, XML_ELEMENT_NAME,
					target.state.getSamplePosition(), target.getAmplitude());
			this.target = target;
		}

		/*
		 * (non-Javadoc)
		 * @see
		 * com.mixblendr.audio.AutomationObject#executeImpl(com.mixblendr.audio
		 * .AudioTrack)
		 */
		@Override
		protected void executeImpl(AudioTrack aTrack) {
			if (target == null) {
				// try to derive the target
				if (owner != null && owner.getOwner() != null) {
					target = (Flanger) owner.getOwner().getEffect(Flanger.class);
				}
				if (target == null || target.state == null) return;
			}
			target.setAmplitude(value);
			target.updateGUIAmplitude();
		}

		/**
		 * @return a string representation of this object (mainly for debugging
		 *         purposes)
		 */
		@Override
		public String toString() {
			return super.toString() + ", amplitude=" + value;
		}
	}

	/** the automation object to record a change in frequency */
	public static class FreqAutomation extends AutomationObjectDouble {
		private static final String XML_ELEMENT_NAME = "FlangerFreq";
		private Flanger target;

		/**
		 * Create an instance with default values, should only be used before
		 * xml import.
		 */
		public FreqAutomation() {
			super(null, XML_ELEMENT_NAME, 0, 1.0);
		}

		/**
		 * Create a frequency automation object capturing the current playback
		 * time and the current frequency.
		 */
		public FreqAutomation(Flanger target) {
			super(target.state, XML_ELEMENT_NAME,
					target.state.getSamplePosition(), target.getFrequency());
			this.target = target;
		}

		/*
		 * (non-Javadoc)
		 * @see
		 * com.mixblendr.audio.AutomationObject#executeImpl(com.mixblendr.audio
		 * .AudioTrack)
		 */
		@Override
		protected void executeImpl(AudioTrack aTrack) {
			if (target == null) {
				// try to derive the target
				if (owner != null && owner.getOwner() != null) {
					target = (Flanger) owner.getOwner().getEffect(Flanger.class);
				}
				if (target == null || target.state == null) return;
			}
			target.setFrequency(value);
			target.updateGUIFreq();
		}

		/**
		 * @return a string representation of this object (mainly for debugging
		 *         purposes)
		 */
		@Override
		public String toString() {
			return super.toString() + ", frequency=" + value;
		}
	}

	/** the automation object to record a change in feedback */
	public static class FeedbackAutomation extends AutomationObjectDouble {
		private static final String XML_ELEMENT_NAME = "FlangerFeedback";
		private Flanger target;

		/**
		 * Create an instance with default values, should only be used before
		 * xml import.
		 */
		public FeedbackAutomation() {
			super(null, XML_ELEMENT_NAME, 0, 0.6);
		}

		/**
		 * Create a feedback automation object capturing the current playback
		 * time and the current feedback.
		 */
		public FeedbackAutomation(Flanger target) {
			super(target.state, XML_ELEMENT_NAME,
					target.state.getSamplePosition(), target.getFeedback());
			this.target = target;
		}

		/*
		 * (non-Javadoc)
		 * @see
		 * com.mixblendr.audio.AutomationObject#executeImpl(com.mixblendr.audio
		 * .AudioTrack)
		 */
		@Override
		protected void executeImpl(AudioTrack aTrack) {
			if (target == null) {
				// try to derive the target
				if (owner != null && owner.getOwner() != null) {
					target = (Flanger) owner.getOwner().getEffect(Flanger.class);
				}
				if (target == null || target.state == null) return;
			}
			target.setFeedback(value);
			target.updateGUIFeedback();
		}

		/**
		 * @return a string representation of this object (mainly for debugging
		 *         purposes)
		 */
		@Override
		public String toString() {
			return super.toString() + ", feedback=" + value;
		}
	}

	/** the automation object to record a change in balance */
	public static class BalanceAutomation extends AutomationObjectDouble {
		private static final String XML_ELEMENT_NAME = "FlangerBalance";
		private Flanger target;

		/**
		 * Create an instance with default values, should only be used before
		 * xml import.
		 */
		public BalanceAutomation() {
			super(null, XML_ELEMENT_NAME, 0, 0.0);
		}

		/**
		 * Create a balance automation object capturing the current playback
		 * time and the current balance.
		 */
		public BalanceAutomation(Flanger target) {
			super(target.state, XML_ELEMENT_NAME,
					target.state.getSamplePosition(), target.getBalance());
			this.target = target;
		}

		/*
		 * (non-Javadoc)
		 * @see
		 * com.mixblendr.audio.AutomationObject#executeImpl(com.mixblendr.audio
		 * .AudioTrack)
		 */
		@Override
		protected void executeImpl(AudioTrack aTrack) {
			if (target == null) {
				// try to derive the target
				if (owner != null && owner.getOwner() != null) {
					target = (Flanger) owner.getOwner().getEffect(Flanger.class);
				}
				if (target == null || target.state == null) return;
			}
			target.setBalance(value);
			target.updateGUIBalance();
		}

		/**
		 * @return a string representation of this object (mainly for debugging
		 *         purposes)
		 */
		@Override
		public String toString() {
			return super.toString() + ", balance=" + value;
		}
	}

	/* satisfy compiler */
	private static final long serialVersionUID = 0;
}
