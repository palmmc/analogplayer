package com.palm1.analogaudio.client.audio.api;

public interface IRadioStreamer {
    void setSettings(float volume, boolean looping);
    void setSpatial(boolean spatial);
    void updatePosition(double x, double y, double z, double pX, double pY, double pZ, double vX, double vY, double vZ, double range, float volumeFactor);
    boolean isPlaying();
    String getCurrentUUID();
    void setCurrentUUID(String uuid);
    float getAmplitude();
    default void setOnTrackEnd(Runnable callback) {
    }
    void stop();
    void start();
    void playTrack(String url, long offsetMs);
    default void setOnError(java.util.function.Consumer<String> callback) {
    }
    default void fetchDuration(String url, java.util.function.Consumer<Long> callback) {
    }
}
