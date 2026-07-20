package com.palm1.analoglib.client.audio.api;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface IPlaylistResolver {
    record ResolvedTrack(String url, String title, long duration) {}

    CompletableFuture<List<ResolvedTrack>> resolve(String url);
}
