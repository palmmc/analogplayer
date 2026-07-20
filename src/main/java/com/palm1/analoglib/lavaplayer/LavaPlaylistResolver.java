package com.palm1.analoglib.lavaplayer;

import com.palm1.analoglib.client.audio.api.IPlaylistResolver;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.lavalink.youtube.YoutubeAudioSourceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class LavaPlaylistResolver implements IPlaylistResolver {
    private static final AudioPlayerManager PLAYER_MANAGER;

    static {
        PLAYER_MANAGER = new DefaultAudioPlayerManager();
        PLAYER_MANAGER.registerSourceManager(new YoutubeAudioSourceManager());
        PLAYER_MANAGER
                .registerSourceManager(new com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager());
        AudioSourceManagers.registerRemoteSources(PLAYER_MANAGER);
    }

    @Override
    public CompletableFuture<List<ResolvedTrack>> resolve(String url) {
        CompletableFuture<List<ResolvedTrack>> future = new CompletableFuture<>();
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
                List<ResolvedTrack> tracks = new ArrayList<>();
                long duration = track.getDuration();
                if (resolvedPath.toLowerCase().endsWith(".mp3")) {
                    long parsed = Mp3DurationParser.getMp3XingDuration(resolvedPath);
                    if (parsed > 0) {
                        duration = parsed;
                    }
                }
                tracks.add(new ResolvedTrack(track.getInfo().uri, track.getInfo().title, duration));
                future.complete(tracks);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                List<ResolvedTrack> tracks = new ArrayList<>();
                for (AudioTrack track : playlist.getTracks()) {
                    long duration = track.getDuration();
                    if (resolvedPath.toLowerCase().endsWith(".mp3")) {
                        long parsed = Mp3DurationParser.getMp3XingDuration(resolvedPath);
                        if (parsed > 0) {
                            duration = parsed;
                        }
                    }
                    tracks.add(new ResolvedTrack(track.getInfo().uri, track.getInfo().title, duration));
                }
                future.complete(tracks);
            }

            @Override
            public void noMatches() {
                future.completeExceptionally(new RuntimeException("No matches found for URL: " + url));
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                future.completeExceptionally(new RuntimeException("Load failed: " + exception.getMessage()));
            }
        });
        return future;
    }
}
