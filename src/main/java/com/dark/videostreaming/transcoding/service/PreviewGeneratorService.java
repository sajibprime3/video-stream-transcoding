package com.dark.videostreaming.transcoding.service;

import com.dark.videostreaming.transcoding.event.PreviewCreationEvent;

public interface PreviewGeneratorService {
    
    void generatePreview(PreviewCreationEvent event);
    
}
