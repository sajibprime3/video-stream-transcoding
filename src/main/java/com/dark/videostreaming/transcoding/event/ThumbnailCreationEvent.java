package com.dark.videostreaming.transcoding.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ThumbnailCreationEvent {

    private Long fileId;
}
