package com.dark.videostreaming.transcoding.event.consumer;

import com.dark.videostreaming.transcoding.event.Event;
import com.dark.videostreaming.transcoding.event.model.PreviewUpdateEvent;
import com.dark.videostreaming.transcoding.event.model.VideoUploadedEvent;
import com.dark.videostreaming.transcoding.service.PreviewGeneratorService;
import com.dark.videostreaming.transcoding.service.ThumbnailGenerationService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Component
public class SimpleConsumer {

    private final ObjectMapper objectMapper;
    private final PreviewGeneratorService previewGeneratorService;
    private final ThumbnailGenerationService thumbnailGenerationService;

    @KafkaListener(topics = "video.events")
    public void listenVideoToEvents(Event<?> event) {
        Object payload = event.getPayload();

        if ("VideoUploaded".equals(event.getEventType())) {
            VideoUploadedEvent data = objectMapper.convertValue(payload, VideoUploadedEvent.class);
            previewGeneratorService.generatePreview(data);
        }
    }

    @KafkaListener(topics = "video.preview.events")
    public void listenToPreviewEvents(Event<?> event) {
        if ("PreviewUpdateEvent".equals(event.getEventType())) {
            PreviewUpdateEvent data = objectMapper.convertValue(event.getPayload(), PreviewUpdateEvent.class);
            if ("ready".equals(data.getStatus())) {
                thumbnailGenerationService.generateThumbnail(data);
            }
        }
    }
}
