package com.dark.videostreaming.transcoding.event.model;

public record VideoUploadedEvent(
        long videoId,
        String fileName) {
}
