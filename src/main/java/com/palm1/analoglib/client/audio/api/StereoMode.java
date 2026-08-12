package com.palm1.analoglib.client.audio.api;

public enum StereoMode {
    VIRTUAL_SPATIAL,
    MONO_STEREO,
    DIRECT_STEREO;

    public static StereoMode fromString(String name) {
        if (name != null) {
            for (StereoMode mode : values()) {
                if (mode.name().equalsIgnoreCase(name)
                        || (mode == VIRTUAL_SPATIAL)
                        || (mode == MONO_STEREO)) {
                    return mode;
                }
            }
        }
        return VIRTUAL_SPATIAL;
    }
}
