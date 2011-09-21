/**
 *
 */
package com.mixblendr.audio;

import java.net.URL;

import org.tritonus.share.sampled.*;
import org.w3c.dom.Element;

import static com.mixblendr.util.Debug.*;

/**
 * Class representing one chunk of audio data in a playlist of a track.
 * <p>
 * One region object can only belong to one playlist at a time.
 * 
 * @author Florian Bomers
 */
public class AudioRegion extends AutomationObject implements
		AudioFileURL.Listener, Cloneable {

	private final static boolean TRACE = false;

	/** the XML element when exporting/importing this region */
	public final static String EXPORT_XML_ELEMENT = "Region";

	public static enum State {
		DOWNLOAD_START, DOWNLOAD_PROGRESS, DOWNLOAD_END
	}

	/** the audio file of this region */
	private AudioFile af;

	/** the offset in the audio file, where this region starts */
	private long audioFileOffset;

	/** the duration in samples, -1 for end of audio file */
	private long duration;

	/** the current read position, 0 to duration */
	private long playbackPos;
	
	/** if true, this region is muted and the read() method will return silence */
	private boolean muted;
	
	/** the linear level of this region (0.0 ... 1.0), by default it's 1.0 */
	private double level;

	static {
		AutomationManager.registerXML(AudioRegion.class, EXPORT_XML_ELEMENT);
	}
	
	/**
	 * Create an instance with default values, should only be used for
	 * xml import.
	 */
	AudioRegion() {
		this(null, (AudioFile) null);
	}

	/**
	 * Create a new audio region object, initially empty.
	 * 
	 * @param state
	 */
	public AudioRegion(AudioState state, AudioFile audioFile) {
		super(state);
		this.audioFileOffset = 0;
		this.duration = (audioFile!=null)?audioFile.getDurationSamples():-1;
		this.playbackPos = 0;
		this.muted = false;
		this.level = 1.0;
		setAudioFile(audioFile);
	}
	
	/**
	 * Create a new region from the XML element. The element should
	 * be named like EXPORT_XML_ELEMENT.
	 */
	public AudioRegion(AudioState state, Element element) throws Exception {
		this(state, (AudioFile) null);
		xmlImport(element);
	}
	
	/** create a cloned copy of this region */
	@Override
	public Object clone() {
		AudioRegion ar = new AudioRegion(getState(), af);
		assignTo(ar);
		return ar;
	}

	/** copy all parameters of this automation object to ao, except the audio file itself. */
	@Override
	public void assignTo(AutomationObject ao) {
		super.assignTo(ao);
		if (ao instanceof AudioRegion) {
			AudioRegion ar = (AudioRegion) ao;
			ar.duration = duration;
			ar.audioFileOffset = audioFileOffset;
			ar.playbackPos = playbackPos;
			ar.level = level;
			ar.muted = muted;
		}
	}

	@Override
	protected void setOwner(Playlist playlist) {
		super.setOwner(playlist);
		if (playlist == null) {
			close();
		}
	}

	/** this method should be called whenever this region is not used anymore. */
	public void close() {
		removeAudioFileDependency();
	}

	/**
	 * remove itself from the audio file's list of listeners, to prevent pinning
	 * of the audio file class.
	 */
	private void removeAudioFileDependency() {
		if (af != null && (af instanceof AudioFileURL)) {
			((AudioFileURL) af).removeListener(this);
		}
	}

	/**
	 * @return the audio file
	 */
	public AudioFile getAudioFile() {
		return af;
	}
	
	private void setAudioFile(AudioFile af) {
		removeAudioFileDependency();
		this.af = af;
		if (af != null && (af instanceof AudioFileURL)) {
			((AudioFileURL) af).addListener(this);
		}
	}

	/**
	 * Get the offset of this region in the underlying AudioFile.
	 * 
	 * @return the audioFileOffset in samples
	 */
	public long getAudioFileOffset() {
		return audioFileOffset;
	}

	/**
	 * Set the offset of this region in the underlying AudioFile.
	 * 
	 * @param audioFileOffset the audioFileOffset to set in samples
	 */
	public synchronized void setAudioFileOffset(long audioFileOffset) {
		this.audioFileOffset = audioFileOffset;
	}

	/**
	 * Get the duration of this region. The region covers the portion of the
	 * underlying AudioFile from AudioFileOffset to AudioFileOffset+Duration.
	 * 
	 * @return the duration in samples, or -1 if play to the end of the audio
	 *         file
	 */
	public long getDuration() {
		return duration;
	}

	/**
	 * @return the duration in samples, or if the set duration is -1, the
	 *         available samples
	 */
	public long getEffectiveDurationSamples() {
		if (duration < 0) {
			return getAvailableSamples();
		}
		return getDuration();
	}

	/**
	 * Set the duration of this region, in samples. The region covers the
	 * portion of the underlying AudioFile from AudioFileOffset to
	 * AudioFileOffset+Duration.
	 * 
	 * @param duration the duration to set, in samples.
	 */
	public synchronized void setDuration(long duration) {
		this.duration = duration;

	}

	/**
	 * @return how many samples are already available. If duration = available
	 *         then the entire region is playable.
	 */
	public long getAvailableSamples() {
		if (af == null) {
			return 0;
		}
		long avail = af.getAvailableSamples() - getAudioFileOffset();
		if (avail < 0) {
			avail = 0;
		} else if (duration >= 0 && avail > duration) {
			avail = duration;
		}
		return avail;
	}

	/**
	 * Return the current playback position, relative to the beginning of this
	 * region. This is used by the playlist during playback.
	 * 
	 * @return the current playback position in samples
	 */
	public long getPlaybackPosition() {
		return playbackPos;
	}

	/**
	 * Set the current playback position - should usually only be called from
	 * the playlist.
	 * 
	 * @param pos the current playback position, relative to the beginning of
	 *            this region, in samples.
	 */
	public void setPlaybackPosition(long pos) {
		if (pos < 0) {
			pos = 0;
		}
		this.playbackPos = pos;
		if (TRACE) debug("AudioRegion: set playbackPos to " + pos);
	}
	
	
	/**
	 * @return true if this region is muted
	 */
	public boolean isMuted() {
		return muted;
	}

	/**
	 * @param muted set to true to mute this region
	 */
	public void setMuted(boolean muted) {
		this.muted = muted;
	}

	/**
	 * @return the linear level of this region, 0.0 .. 1.0
	 */
	public double getLevel() {
		return level;
	}

	/**
	 * Set the level of this region.
	 * @param level the level to set, 0.0 ... 1.0
	 */
	public void setLevel(double level) {
		this.level = level;
	}

	/**
	 * Determine if this region has played out. This is usually called during
	 * playback from the playlist.
	 * 
	 * @return true if this region has played out.
	 */
	public boolean isPlaybackEndReached() {
		long eff = getEffectiveDurationSamples();
		return (eff >= 0) && (playbackPos >= eff);
	}

	/**
	 * @return true if this region is not at the start of the underlying file,
	 *         so a click would be caused if not faded in.
	 */
	public boolean needFadeInToPreventClick() {
		return audioFileOffset > 0;
	}

	/**
	 * @return true if this region's playback position does not end at the end
	 *         of the underlying file, so a click would be caused if not faded
	 *         out.
	 */
	public boolean needFadeOutAtCurrentPlaybackPosition() {
		return audioFileOffset + getEffectiveDurationSamples() < af.getDurationSamples();
	}

	/**
	 * @param buffer the buffer to apply the region's level
	 * @param offset offset in buffer where to start applying the level
	 * @param count number of samples to process
	 */
	private final void applyLevel(FloatSampleBuffer buffer, int offset, int count) {
		if (level == 0.0 || muted) {
			buffer.makeSilence(offset, count);
		} else {
			final int cc = buffer.getChannelCount();
			final int endPos = offset + count;
			final float lev = (float) level;
			for (int c = 0; c < cc; c++) {
				float[] channel = buffer.getChannel(c);
				for (int p = offset; p < endPos; p++) {
					channel[p] *= lev;
				}
			}
		}
	}

	/**
	 * Fill a buffer with a linearly faded in buffer of samples that occur prior
	 * to the actual start of this region. The entire buffer is overwritten,
	 * even if the pre-load portion is too small to fill the entire buffer.
	 */
	public final void fillFadeInBuffer(FloatSampleBuffer buffer) {
		int count = buffer.getSampleCount();
		int offset = 0;
		if (audioFileOffset < count) {
			int diff = (int) (count - audioFileOffset);
			buffer.makeSilence(offset, diff);
			offset += diff;
			count -= diff;
		}
		if (count > 0) {
			af.read(audioFileOffset - count, buffer, offset, count);
			// apply level&mute
			applyLevel(buffer, offset, count);
			// now fade in
			buffer.linearFade(0f, 1f, offset, count);
		}
	}

	/**
	 * Fill a buffer with a linearly faded out buffer of samples that occur
	 * after the actual end of this region. The entire buffer is overwritten,
	 * even if the available post-play portion is too small to fill the entire
	 * buffer.
	 */
	public void fillFadeOutBuffer(FloatSampleBuffer buffer) {
		int count = buffer.getSampleCount();
		long thisSampleCount = getEffectiveDurationSamples();
		long afSampleCount = af.getDurationSamples() - audioFileOffset;
		// if audiofile can't provide any samples to use, return silence
		if (afSampleCount <= thisSampleCount) {
			buffer.makeSilence();
			return;
		}

		int offset = 0;
		if (afSampleCount - thisSampleCount < count) {
			int diff = (int) (count - (afSampleCount - thisSampleCount));
			count -= diff;
			buffer.makeSilence(count, diff);
		}
		if (count > 0) {
			af.read(audioFileOffset + thisSampleCount, buffer, offset, count);
			// apply level and mute
			applyLevel(buffer, offset, count);
			// now fade out
			buffer.linearFade(1f, 0f, offset, count);
		}
	}

	/**
	 * Read from this region (and the underlying AudioFile) using the current
	 * read position. After successful read, the position will advance by count
	 * samples.
	 * <p>
	 * If the audio file is only partially available, or position is partially
	 * beyond the duration of this region, silence is filled for the specified
	 * length. The return value will only reflect the actual audio samples, not
	 * the appended silence.
	 * <p>
	 * If nothing can be read, this method returns 0 and will not silence the
	 * buffer - it will still advance the playback position.
	 * 
	 * @param buffer the buffer to read into
	 * @param offset the offset where to write into buffer
	 * @param count the number of samples to read
	 * @return the number of samples written to buffer
	 */
	public int read(FloatSampleBuffer buffer, int offset, int count) {
		int canWrite;
		long effectiveDuration = getEffectiveDurationSamples();
		if (af == null) {
			canWrite = 0;
		} else {
			canWrite = count;
			if (playbackPos + canWrite > effectiveDuration) {
				canWrite = (int) (effectiveDuration - playbackPos);
			}
			long fileOffset = this.audioFileOffset + playbackPos;
			if (fileOffset < 0) {
				return 0;
			}
			long avail = af.getAvailableSamples();
			if (fileOffset + canWrite > avail) {
				canWrite = (int) (avail - fileOffset);
			}
			if (canWrite < 0) {
				canWrite = 0;
			} else if (canWrite > count) {
				canWrite = count;
			}
			if (canWrite > 0) {
				if (!af.read(fileOffset, buffer, offset, canWrite)) {
					// nothing written
					canWrite = 0;
				} else {
					// apply level and mute
					applyLevel(buffer, offset, canWrite);
					if (TRACE) {
						debug("AudioRegion: Read " + canWrite
								+ " samples from " + af + " at position "
								+ fileOffset);
					}
				}
			}
		}

		if (canWrite > 0 && canWrite < count) {
			// fill the remainder with silence
			buffer.makeSilence(offset + canWrite, count - canWrite);
		}
		if (canWrite > 0 && af.isFullyLoaded()) {
			playbackPos += canWrite;
		} else {
			// if still loading, just advance by the full amount
			playbackPos += count;
			if (playbackPos >= getDuration() && getDuration() >= 0) {
				playbackPos = getDuration();
			}
		}
		if (TRACE) {
			debug("AudioRegion: return " + canWrite
					+ " samples, new position is " + playbackPos);
		}
		return canWrite;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AutomationObject#executeImpl(com.mixblendr.audio.AudioTrack)
	 */
	@Override
	protected void executeImpl(AudioTrack track) {
		// nothing to do here
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioFileURL.Listener#audioFileDownloadEnd(com.mixblendr.audio.AudioFile)
	 */
	public void audioFileDownloadEnd(AudioFile source) {
		// notify audio listeners
		if (owner != null) {
			getState().getAudioEventDispatcher().dispatchAudioRegionStateChange(
					owner.getOwner(), this, State.DOWNLOAD_END);
		}
		// do not require further notifications anymore
		removeAudioFileDependency();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioFileURL.Listener#audioFileDownloadError(com.mixblendr.audio.AudioFile)
	 */
	public void audioFileDownloadError(AudioFile source) {
		// do not notify audio listeners, should be handled globally when this
		// audio file caused an error
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioFileURL.Listener#audioFileDownloadStart(com.mixblendr.audio.AudioFile)
	 */
	public void audioFileDownloadStart(AudioFile source) {
		// notify audio listeners
		if (owner != null) {
			getState().getAudioEventDispatcher().dispatchAudioRegionStateChange(
					owner.getOwner(), this, State.DOWNLOAD_START);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioFileURL.Listener#audioFileDownloadUpdate(com.mixblendr.audio.AudioFile)
	 */
	public void audioFileDownloadUpdate(AudioFile source) {
		// if we've already reached the end, treat it as end of download
		if (getAvailableSamples() == duration) {
			audioFileDownloadEnd(source);
		} else {
			// notify audio listeners
			if (owner != null) {
				getState().getAudioEventDispatcher().dispatchAudioRegionStateChange(
						owner.getOwner(), this, State.DOWNLOAD_PROGRESS);
			}
		}
	}
	
	// PERSISTENCE
	
	@Override
	public Element xmlExport(Element element) {
		element = super.xmlExport(element, EXPORT_XML_ELEMENT);
		if (af != null) {
			element.setAttribute("File", af.getSource());
		}
		if (audioFileOffset != 0) {
			element.setAttribute("FileOffset", String.valueOf(audioFileOffset));
		}
		if (duration != -1) {
			element.setAttribute("Duration", String.valueOf(duration));
		}
		if (muted) {
			/** if true, this region is muted and the read() method will return silence */
			element.setAttribute("Muted", "yes");
		}
		if (level != 1.0) {
			element.setAttribute("Level", String.valueOf(level));
		}
		return element;
	}

	
	/*
	 * (non-Javadoc)
	 * @see com.mixblendr.audio.AutomationObject#xmlImport(org.w3c.dom.Element)
	 */
	@Override
	public void xmlImport(Element element) throws Exception {
		removeAudioFileDependency();

		element = super.xmlImport(element, EXPORT_XML_ELEMENT);

		String val = element.getAttribute("File");
		if (val.length() > 0) {
			setAudioFile(getState().getAudioFileFactory().getAudioFile(new URL(val)));
		} else {
			setAudioFile(null);
		}
		val = element.getAttribute("FileOffset");
		if (val.length() > 0) {
			setAudioFileOffset(Long.parseLong(val));
		} else {
			setAudioFileOffset(0);
		}
		val = element.getAttribute("Duration");
		if (val.length() > 0) {
			setDuration(Long.parseLong(val));
		} else {
			setDuration(-1);
		}
		val = element.getAttribute("Muted");
		if (val.length() > 0 && (val.charAt(0) == 'y' || val.charAt(0) == 't' || val.charAt(0) == '1')) {
			setMuted(true);
		} else {
			setMuted(false);
		}
		val = element.getAttribute("Level");
		if (val.length() > 0) {
			setLevel(Double.parseDouble(val));
		} else {
			setLevel(1.0);
		}
	}


	@Override
	public String toString() {
		return "Region at "
				+ getState().sample2seconds(getStartTimeSamples())
				+ "s,  duration="
				+ ((duration >= 0) ? getState().sample2seconds(duration) + "s"
						: "until end") + ", playbackPos="
				+ getState().sample2seconds(this.playbackPos) + "s"
				+ ", offset=" + getState().sample2seconds(this.audioFileOffset)
				+ "s" + " in " + af;
	}

}
