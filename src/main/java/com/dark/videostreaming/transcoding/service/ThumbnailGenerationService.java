package com.dark.videostreaming.transcoding.service;

import com.dark.videostreaming.transcoding.event.ThumbnailCreationEvent;

public interface ThumbnailGenerationService {
    void generateThumbnail(ThumbnailCreationEvent event);
}
