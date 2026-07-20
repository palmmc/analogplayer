package com.palm1.analoglib.api;

@FunctionalInterface
public interface IAudioFilter {
    /**
     * Processes audio samples in-place.
     * @param samples Normalized audio samples between -1.0 and 1.0.
     * @param channels Number of audio channels (1 for mono, 2 for stereo).
     * @param sampleRate Sample rate in Hz (typically 44100 or 48000).
     */
    void process(float[] samples, int channels, int sampleRate);
}
