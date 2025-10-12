package com.dark.videostreaming.transcoding.event.consumer;

import com.dark.videostreaming.transcoding.event.Event;
import com.dark.videostreaming.transcoding.event.model.VideoUploadedEvent;
import com.dark.videostreaming.transcoding.service.PreviewGeneratorService;
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

    @KafkaListener(topics = "video.events")
    public void listen(Event<?> event) {
        Object payload = event.getPayload();

        if ("VideoUploaded".equals(event.getEventType())) {
            VideoUploadedEvent data = objectMapper.convertValue(payload, VideoUploadedEvent.class);
            previewGeneratorService.generatePreview(data);
        }
    }
}
