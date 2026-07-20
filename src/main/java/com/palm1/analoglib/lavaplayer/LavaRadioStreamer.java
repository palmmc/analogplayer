package com.palm1.analoglib.lavaplayer;

import com.palm1.analoglib.client.audio.api.IRadioStreamer;
import com.palm1.analoglib.api.IAudioFilter;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import dev.lavalink.youtube.YoutubeAudioSourceManager;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.Queue;

public class LavaRadioStreamer extends AudioEventAdapter implements IRadioStreamer {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(LavaRadioStreamer.class);
    private static final java.util.concurrent.ScheduledExecutorService SEEK_EXECUTOR = java.util.concurrent.Executors
            .newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "LavaRadioStreamer-SeekExecutor");
                t.setDaemon(true);
                return t;
            });
    private static final AudioPlayerManager PLAYER_MANAGER;
    static {
        PLAYER_MANAGER = new DefaultAudioPlayerManager();
        PLAYER_MANAGER.registerSourceManager(new YoutubeAudioSourceManager());
        PLAYER_MANAGER.registerSourceManager(new LocalAudioSourceManager());
        AudioSourceManagers.registerRemoteSources(PLAYER_MANAGER);
        PLAYER_MANAGER.getConfiguration()
                .setOutputFormat(com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats.COMMON_PCM_S16_BE);
    }

    private final AudioPlayer player;
    private int sourceId = -1;
    private final Queue<Integer> buffers = new LinkedList<>();
    private String currentUUID;
    private boolean playing = false;
    private boolean looping = false;
    private float volume = 1.0f;
    private double lastX, lastY, lastZ;
    private double range = 0;
    private boolean spatial = true;
    private Runnable trackEndCallback;
    private java.util.function.Consumer<String> errorCallback;
    private IAudioFilter audioFilter;

    public LavaRadioStreamer() {
        this.player = PLAYER_MANAGER.createPlayer();
        this.player.addListener(this);
    }

    @Override
    public void setAudioFilter(IAudioFilter filter) {
        this.audioFilter = filter;
    }

    @Override
    public void setOnTrackEnd(Runnable callback) {
        this.trackEndCallback = callback;
    }

    @Override
    public void setOnError(java.util.function.Consumer<String> callback) {
        this.errorCallback = callback;
    }

    @Override
    public void setSpatial(boolean spatial) {
        this.spatial = spatial;
    }

    @Override
    public void setSettings(float volume, boolean looping) {
        this.volume = volume;
        this.looping = looping;
    }

    @Override
    public void updatePosition(double x, double y, double z, double pX, double pY, double pZ, double vX, double vY,
            double vZ, double range, float volumeFactor) {
        updatePosition(x, y, z, pX, pY, pZ, vX, vY, vZ, range, volumeFactor, 0.1f);
    }

    @Override
    public void updatePosition(double x, double y, double z, double pX, double pY, double pZ, double vX, double vY,
            double vZ, double range, float volumeFactor, float spatialityThreshold) {
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;

        this.range = range;
        if (sourceId == -1)
            return;

        float fade = 1.0f;
        if (range > 0) {
            double dx = x - pX;
            double dy = y - pY;
            double dz = z - pZ;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            fade = 1.0f - (float) (dist / (float) range);
            if (fade < 0)
                fade = 0;
        }

        float modifier = spatial ? 1.2f : 0.3f;
        AL10.alSourcef(sourceId, AL10.AL_GAIN, this.volume * fade * volumeFactor * modifier);

        if (!spatial || range <= 0) {
            AL10.alSourcei(sourceId, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(sourceId, AL10.AL_POSITION, 0, 0, 0);
            AL10.alSource3f(sourceId, AL11.AL_VELOCITY, 0, 0, 0);
        } else {
            double threshold = spatialityThreshold;
            double interpX = pX + (x - pX) * threshold;
            double interpY = pY + (y - pY) * threshold;
            double interpZ = pZ + (z - pZ) * threshold;

            AL10.alSourcei(sourceId, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);

            try {
                Class<?> spCls = Class.forName("com.palm1.analogaudio.integration.SoundPhysicsIntegration");
                java.lang.reflect.Method m = spCls.getMethod("processSound", int.class, double.class, double.class,
                        double.class, String.class, String.class, String.class, boolean.class);
                double[] shiftedPos = (double[]) m.invoke(null, sourceId, x, y, z, "BLOCKS", "analoglib", "radio",
                        false);
                if (shiftedPos != null) {
                    double interpShiftedX = pX + (shiftedPos[0] - pX) * threshold;
                    double interpShiftedY = pY + (shiftedPos[1] - pY) * threshold;
                    double interpShiftedZ = pZ + (shiftedPos[2] - pZ) * threshold;
                    AL10.alSource3f(sourceId, AL10.AL_POSITION, (float) interpShiftedX, (float) interpShiftedY,
                            (float) interpShiftedZ);
                } else {
                    AL10.alSource3f(sourceId, AL10.AL_POSITION, (float) interpX, (float) interpY, (float) interpZ);
                }
            } catch (Throwable t) {
                AL10.alSource3f(sourceId, AL10.AL_POSITION, (float) interpX, (float) interpY, (float) interpZ);
            }

            AL10.alSource3f(sourceId, AL11.AL_VELOCITY, (float) vX, (float) vY, (float) vZ);
        }
        AL10.alSourcef(sourceId, AL10.AL_PITCH, 1.0f);
        streamAudio();
    }

    private ByteBuffer stereoBuffer;
    private ByteBuffer monoBuffer;

    private void streamAudio() {
        if (sourceId == -1) {
            start();
        }
        if (sourceId == -1 || !playing)
            return;

        int processed = AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_PROCESSED);
        while (processed-- > 0) {
            int buffer = AL10.alSourceUnqueueBuffers(sourceId);
            if (buffer != 0) {
                buffers.add(buffer);
            }
        }

        while (!buffers.isEmpty()) {
            AudioFrame frame = player.provide();
            if (frame == null)
                break;

            AudioTrack currentTrack = player.getPlayingTrack();
            if (currentTrack != null && pendingSeeks.containsKey(currentTrack)) {
                int count = frameCounts.getOrDefault(currentTrack, 0) + 1;
                frameCounts.put(currentTrack, count);
                if (count >= 5) {
                    Long posToSeek = pendingSeeks.remove(currentTrack);
                    frameCounts.remove(currentTrack);
                    if (posToSeek != null && posToSeek > 0) {
                        try {
                            currentTrack.setPosition(posToSeek);
                        } catch (Throwable t) {
                            LOGGER.error("Failed to seek track {}", currentTrack.getIdentifier(), t);
                        }
                    }
                }
            }

            int buffer = buffers.poll();
            byte[] data = frame.getData();

            if (!spatial) {
                short[] samples = new short[data.length / 2];
                for (int i = 0; i < data.length; i += 2) {
                    samples[i / 2] = (short) (((data[i] & 0xFF) << 8) | (data[i + 1] & 0xFF));
                }

                if (audioFilter != null) {
                    float[] floatSamples = new float[samples.length];
                    for (int i = 0; i < samples.length; i++) {
                        floatSamples[i] = samples[i] / 32768.0f;
                    }
                    try {
                        audioFilter.process(floatSamples, 2, 44100);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                    for (int i = 0; i < samples.length; i++) {
                        samples[i] = (short) Math.max(-32768, Math.min(32767, Math.round(floatSamples[i] * 32768.0f)));
                    }
                }

                if (stereoBuffer == null || stereoBuffer.capacity() < data.length) {
                    stereoBuffer = ByteBuffer.allocateDirect(data.length);
                    stereoBuffer.order(ByteOrder.nativeOrder());
                }
                stereoBuffer.clear();
                for (short sample : samples) {
                    stereoBuffer.putShort(sample);
                }
                stereoBuffer.flip();
                AL10.alBufferData(buffer, AL10.AL_FORMAT_STEREO16, stereoBuffer, 44100);
            } else {
                int monoLength = data.length / 2;
                short[] samples = new short[monoLength / 2];
                for (int i = 0; i < data.length; i += 4) {
                    short left = (short) (((data[i] & 0xFF) << 8) | (data[i + 1] & 0xFF));
                    short right = (short) (((data[i + 2] & 0xFF) << 8) | (data[i + 3] & 0xFF));
                    samples[i / 4] = (short) ((left + right) / 2);
                }

                if (audioFilter != null) {
                    float[] floatSamples = new float[samples.length];
                    for (int i = 0; i < samples.length; i++) {
                        floatSamples[i] = samples[i] / 32768.0f;
                    }
                    try {
                        audioFilter.process(floatSamples, 1, 44100);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                    for (int i = 0; i < samples.length; i++) {
                        samples[i] = (short) Math.max(-32768, Math.min(32767, Math.round(floatSamples[i] * 32768.0f)));
                    }
                }

                if (monoBuffer == null || monoBuffer.capacity() < monoLength) {
                    monoBuffer = ByteBuffer.allocateDirect(monoLength);
                    monoBuffer.order(ByteOrder.nativeOrder());
                }
                monoBuffer.clear();
                for (short sample : samples) {
                    monoBuffer.putShort(sample);
                }
                monoBuffer.flip();

                AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, monoBuffer, 44100);
            }
            AL10.alSourceQueueBuffers(sourceId, buffer);
        }

        int state = AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE);
        if (state != AL10.AL_PLAYING && AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_QUEUED) > 0) {
            AL10.alSourcePlay(sourceId);
        }
    }

    private final java.util.Map<AudioTrack, Long> pendingSeeks = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<AudioTrack, Integer> frameCounts = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (endReason.mayStartNext) {
            if (looping) {
                player.playTrack(track.makeClone());
            } else if (trackEndCallback != null) {
                trackEndCallback.run();
            }
        }
    }

    @Override
    public void start() {
        if (sourceId != -1)
            return;
        sourceId = AL10.alGenSources();
        int err = AL10.alGetError();
        if (err != AL10.AL_NO_ERROR || sourceId == -1) {
            System.err.println("Failed to generate OpenAL source for LavaRadioStreamer: " + err);
            sourceId = -1;
            return;
        }

        buffers.clear();
        for (int i = 0; i < 16; i++) {
            int buffer = AL10.alGenBuffers();
            if (buffer != 0)
                buffers.add(buffer);
        }
        playing = true;
        AL10.alSourcef(sourceId, AL10.AL_MAX_GAIN, 1.5f);
    }

    @Override
    public void playTrack(String url, long offsetMs) {
        playTrack(url, offsetMs, 0);
    }

    @Override
    public void playTrack(String url, long offsetMs, long trueDuration) {
        player.stopTrack();
        start();
        playing = true;
        final long requestTimeMs = System.currentTimeMillis();
        String finalUrl = url;
        if (url.startsWith("file:///")) {
            finalUrl = url.substring(8);
        } else if (url.startsWith("file:/")) {
            finalUrl = url.substring(6);
        }
        final String resolvedPath = finalUrl;
        PLAYER_MANAGER.loadItem(finalUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                long elapsedMs = System.currentTimeMillis() - requestTimeMs;
                long adjustedOffsetMs = offsetMs + elapsedMs;
                long targetSeekPos = -1;
                if (adjustedOffsetMs > 0) {
                    long internalDuration = track.getDuration();
                    long duration = trueDuration > 0 ? trueDuration : internalDuration;
                    if (duration == internalDuration && resolvedPath.toLowerCase().endsWith(".mp3")) {
                        long parsed = Mp3DurationParser.getMp3XingDuration(resolvedPath);
                        if (parsed > 0) {
                            duration = parsed;
                        }
                    }
                    if (duration > 0) {
                        if (!looping && adjustedOffsetMs > duration) {
                            playing = false;
                            if (trackEndCallback != null)
                                trackEndCallback.run();
                            return;
                        }
                        long seekPos = adjustedOffsetMs % duration;
                        if (duration != internalDuration && internalDuration > 0) {
                            seekPos = (seekPos * internalDuration) / duration;
                        }
                        targetSeekPos = seekPos;
                    } else {
                        targetSeekPos = adjustedOffsetMs;
                    }
                }
                if (targetSeekPos > 0) {
                    pendingSeeks.put(track, targetSeekPos);
                }
                player.playTrack(track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (!playlist.getTracks().isEmpty()) {
                    AudioTrack track = playlist.getTracks().get(0);
                    long elapsedMs = System.currentTimeMillis() - requestTimeMs;
                    long adjustedOffsetMs = offsetMs + elapsedMs;
                    long targetSeekPos = -1;
                    if (adjustedOffsetMs > 0) {
                        long internalDuration = track.getDuration();
                        long duration = trueDuration > 0 ? trueDuration : internalDuration;
                        if (duration == internalDuration && resolvedPath.toLowerCase().endsWith(".mp3")) {
                            long parsed = Mp3DurationParser.getMp3XingDuration(resolvedPath);
                            if (parsed > 0) {
                                duration = parsed;
                            }
                        }
                        if (duration > 0) {
                            if (!looping && adjustedOffsetMs > duration) {
                                playing = false;
                                if (trackEndCallback != null)
                                    trackEndCallback.run();
                                return;
                            }
                            long seekPos = adjustedOffsetMs % duration;
                            if (duration != internalDuration && internalDuration > 0) {
                                seekPos = (seekPos * internalDuration) / duration;
                            }
                            targetSeekPos = seekPos;
                        } else {
                            targetSeekPos = adjustedOffsetMs;
                        }
                    }
                    if (targetSeekPos > 0) {
                        pendingSeeks.put(track, targetSeekPos);
                    }
                    player.playTrack(track);
                }
            }

            @Override
            public void noMatches() {
                System.err.println("No matches for URL: " + url);
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                System.err.println("Load failed for URL: " + url + " - " + exception.getMessage());
                if (errorCallback != null) {
                    String message = "Failed to play audio.";
                    Throwable cause = exception.getCause();
                    while (cause != null) {
                        if (cause instanceof java.net.UnknownHostException) {
                            message = "Failed to play audio: You are offline.";
                            break;
                        }
                        cause = cause.getCause();
                    }
                    System.out.println("Triggering error callback with message: " + message);
                    errorCallback.accept(message);
                } else {
                    System.out.println("Error callback is null when audio failed to load.");
                }
            }
        });
    }

    @Override
    public void fetchDuration(String url, java.util.function.Consumer<Long> callback) {
        System.out.println("Fetching duration for: " + url);
        String finalUrl = url;
        if (url.startsWith("file:///")) {
            finalUrl = url.substring(8);
        } else if (url.startsWith("file:/")) {
            finalUrl = url.substring(6);
        }
        if (finalUrl.toLowerCase().endsWith(".mp3")) {
            long parsed = Mp3DurationParser.getMp3XingDuration(finalUrl);
            if (parsed > 0) {
                callback.accept(parsed);
                return;
            }
        }
        PLAYER_MANAGER.loadItem(finalUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                callback.accept(track.getDuration());
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (!playlist.getTracks().isEmpty()) {
                    callback.accept(playlist.getTracks().get(0).getDuration());
                } else {
                    callback.accept(0L);
                }
            }

            @Override
            public void noMatches() {
                callback.accept(0L);
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                callback.accept(0L);
            }
        });
    }

    @Override
    public boolean isPlaying() {
        return playing;
    }

    @Override
    public String getCurrentUUID() {
        return currentUUID;
    }

    @Override
    public void setCurrentUUID(String uuid) {
        this.currentUUID = uuid;
    }

    @Override
    public float getAmplitude() {
        return 0;
    }

    @Override
    public void stop() {
        playing = false;
        if (sourceId != -1) {
            AL10.alSourceStop(sourceId);
            int queued = AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_QUEUED);
            while (queued-- > 0) {
                AL10.alSourceUnqueueBuffers(sourceId);
            }
            AL10.alDeleteSources(sourceId);
            sourceId = -1;
        }
        while (!buffers.isEmpty()) {
            int buf = buffers.poll();
            if (buf != 0)
                AL10.alDeleteBuffers(buf);
        }
        player.stopTrack();
    }
}
