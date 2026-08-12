package com.palm1.analoglib.client.audio.api;

import com.palm1.analoglib.api.IAudioFilter;

public interface IRadioStreamer {
    void setSettings(float volume, boolean looping);

    void setSpatial(boolean spatial);

    default void setStereoConfig(StereoMode mode, float speakerWidth) {
    }

    default void setAudioFilter(IAudioFilter filter) {
    }

    default void updatePosition(double x, double y, double z, double pX, double pY, double pZ, double vX, double vY,
            double vZ, double range, float volumeFactor, float spatialityThreshold) {
        updatePosition(x, y, z, pX, pY, pZ, vX, vY, vZ, range, volumeFactor);
    }

    void updatePosition(double x, double y, double z, double pX, double pY, double pZ, double vX, double vY, double vZ,
            double range, float volumeFactor);

    boolean isPlaying();

    String getCurrentUUID();

    void setCurrentUUID(String uuid);

    float getAmplitude();

    default void setOnTrackEnd(Runnable callback) {
    }

    void stop();

    void start();

    void playTrack(String url, long offsetMs);

    default void playTrack(String url, long offsetMs, long trueDuration) {
        playTrack(url, offsetMs);
    }

    default void setOnError(java.util.function.Consumer<String> callback) {
    }

    default void fetchDuration(String url, java.util.function.Consumer<Long> callback) {
    }
}
