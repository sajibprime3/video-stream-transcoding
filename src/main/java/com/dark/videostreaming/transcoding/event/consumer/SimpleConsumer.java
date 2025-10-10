package com.dark.videostreaming.transcoding.event.consumer;

import com.dark.videostreaming.transcoding.event.Event;
import com.dark.videostreaming.transcoding.event.model.VideoUploadedEvent;
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

    @KafkaListener(topics = "video.events")
    public void listen(Event<?> event) {
        log.info("Received event: {}", event.getEventType());
        Object payload = event.getPayload();

        if ("VideoUploaded".equals(event.getEventType())) {
            VideoUploadedEvent data = objectMapper.convertValue(payload,
                    VideoUploadedEvent.class);
            log.info("id:{}, filename:{}", data.videoId(), data.fileName());
        }
    }
}
