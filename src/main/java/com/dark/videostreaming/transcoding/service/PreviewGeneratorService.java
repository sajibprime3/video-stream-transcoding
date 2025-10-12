package com.dark.videostreaming.transcoding.service;

import com.dark.videostreaming.transcoding.event.model.VideoUploadedEvent;

public interface PreviewGeneratorService {

    void generatePreview(VideoUploadedEvent event);

}
