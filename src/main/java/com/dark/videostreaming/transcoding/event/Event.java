package com.dark.videostreaming.transcoding.event;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class Event<T> {
    private String eventType;
    private String version;
    private Instant timestamp;
    private T payload;
}
