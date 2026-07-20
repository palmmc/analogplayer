package com.palm1.analoglib.lavaplayer;

public class Mp3DurationParser {
    public static long getMp3XingDuration(String filePath) {
        try {
            java.io.File file = new java.io.File(filePath);
            if (!file.exists()) return -1;
            byte[] bytes = new byte[8192];
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                int read = fis.read(bytes);
                if (read < 100) return -1;
            }
            int xingPos = -1;
            for (int i = 0; i < bytes.length - 16; i++) {
                if ((bytes[i] == 'X' && bytes[i+1] == 'i' && bytes[i+2] == 'n' && bytes[i+3] == 'g') ||
                    (bytes[i] == 'I' && bytes[i+1] == 'n' && bytes[i+2] == 'f' && bytes[i+3] == 'o')) {
                    xingPos = i;
                    break;
                }
            }
            if (xingPos == -1) return -1;
            int frameHeaderPos = -1;
            for (int i = xingPos - 4; i >= 0; i--) {
                if ((bytes[i] & 0xFF) == 0xFF && (bytes[i+1] & 0xE0) == 0xE0) {
                    frameHeaderPos = i;
                    break;
                }
            }
            if (frameHeaderPos == -1) return -1;
            byte[] header = new byte[] { bytes[frameHeaderPos], bytes[frameHeaderPos+1], bytes[frameHeaderPos+2], bytes[frameHeaderPos+3] };
            int version = (header[1] >> 3) & 3;
            int layer = (header[1] >> 1) & 3;
            int srIdx = (header[2] >> 2) & 3;
            int sampleRate = 44100;
            if (version == 3) {
                if (srIdx == 0) sampleRate = 44100;
                else if (srIdx == 1) sampleRate = 48000;
                else if (srIdx == 2) sampleRate = 32000;
            } else if (version == 2) {
                if (srIdx == 0) sampleRate = 22050;
                else if (srIdx == 1) sampleRate = 24000;
                else if (srIdx == 2) sampleRate = 16000;
            } else if (version == 0) {
                if (srIdx == 0) sampleRate = 11025;
                else if (srIdx == 1) sampleRate = 12000;
                else if (srIdx == 2) sampleRate = 8000;
            }
            int samplesPerFrame = 1152;
            if (version != 3) {
                if (layer == 1) {
                    samplesPerFrame = 576;
                }
            }
            int flags = ((bytes[xingPos+4] & 0xFF) << 24) |
                        ((bytes[xingPos+5] & 0xFF) << 16) |
                        ((bytes[xingPos+6] & 0xFF) << 8)  |
                         (bytes[xingPos+7] & 0xFF);
            if ((flags & 1) != 0) {
                int frames = ((bytes[xingPos+8]  & 0xFF) << 24) |
                             ((bytes[xingPos+9]  & 0xFF) << 16) |
                             ((bytes[xingPos+10] & 0xFF) << 8)  |
                              (bytes[xingPos+11] & 0xFF);
                return (long) frames * samplesPerFrame * 1000L / sampleRate;
            }
        } catch (Exception e) {
        }
        return -1;
    }
}
