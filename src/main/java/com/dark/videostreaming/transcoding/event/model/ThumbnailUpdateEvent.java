package com.dark.videostreaming.transcoding.event.model;

import java.time.Instant;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ThumbnailUpdateEvent {
    private long videoId;
    private String name;
    private long size;
    private String status;
    private Instant createdAt;
}
