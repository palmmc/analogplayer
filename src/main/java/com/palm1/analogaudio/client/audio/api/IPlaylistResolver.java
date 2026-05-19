package com.palm1.analogaudio.client.audio.api;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface IPlaylistResolver {
    public record ResolvedTrack(String url, String name, long duration) {
    }

    CompletableFuture<List<ResolvedTrack>> resolve(String url);
}
