package com.palm1.analogaudio.api;

public interface IAudioFilter {
    /**
     * Processes audio samples.
     * 
     * @param samples    The audio samples normalized to -1.0f to 1.0f.
     * @param channels   The number of channels.
     * @param sampleRate The sample rate of the audio in hertz.
     */
    void process(float[] samples, int channels, int sampleRate);
}
