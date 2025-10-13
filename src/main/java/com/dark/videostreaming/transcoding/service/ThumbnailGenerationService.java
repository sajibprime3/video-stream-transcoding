package com.dark.videostreaming.transcoding.service;

import com.dark.videostreaming.transcoding.event.model.PreviewUpdateEvent;

public interface ThumbnailGenerationService {
    void generateThumbnail(PreviewUpdateEvent event);
}
