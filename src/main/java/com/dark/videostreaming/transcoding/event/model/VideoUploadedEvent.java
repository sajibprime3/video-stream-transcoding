package com.dark.videostreaming.transcoding.event.model;

import java.util.UUID;

public record VideoUploadedEvent(
        UUID videoId,
        String fileName,
        long fileSize) {
}
