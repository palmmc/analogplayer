package com.palm1.analogaudio.lavaplayer;

import com.palm1.analogaudio.client.audio.api.IPlaylistResolver;
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
        AudioSourceManagers.registerRemoteSources(PLAYER_MANAGER);
    }

    @Override
    public CompletableFuture<List<ResolvedTrack>> resolve(String url) {
        CompletableFuture<List<ResolvedTrack>> future = new CompletableFuture<>();
        PLAYER_MANAGER.loadItem(url, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                List<ResolvedTrack> tracks = new ArrayList<>();
                tracks.add(new ResolvedTrack(track.getInfo().uri, track.getInfo().title, track.getDuration()));
                future.complete(tracks);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                List<ResolvedTrack> tracks = new ArrayList<>();
                for (AudioTrack track : playlist.getTracks()) {
                    tracks.add(new ResolvedTrack(track.getInfo().uri, track.getInfo().title, track.getDuration()));
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
