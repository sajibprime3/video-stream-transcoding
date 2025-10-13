package com.dark.videostreaming.transcoding.service.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import com.dark.videostreaming.transcoding.event.Event;
import com.dark.videostreaming.transcoding.event.model.PreviewUpdateEvent;
import com.dark.videostreaming.transcoding.event.model.ThumbnailUpdateEvent;
import com.dark.videostreaming.transcoding.service.PreviewStorageService;
import com.dark.videostreaming.transcoding.service.ThumbnailGenerationService;
import com.dark.videostreaming.transcoding.service.ThumbnailStorageService;

import org.apache.commons.io.FileUtils;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
@Service
public class ThumbnailGenerationServiceImpl implements ThumbnailGenerationService {

    private final PreviewStorageService previewStorageService;
    private final ThumbnailStorageService thumbnailStorageService;
    private final KafkaTemplate<String, Event<?>> kafkaTemplate;

    private final Path temp = Paths.get(System.getProperty("user.dir")).resolve("tmpThumb");

    @Async
    @Override
    public void generateThumbnail(PreviewUpdateEvent event) {
        generateAndStoreThumbnail(event.getVideoId(), event.getName(), event.getSize());
    }

    private void generateAndStoreThumbnail(long videoId, String filename, long filesize) {
        try {
            ThumbnailUpdateEvent updatedEvent = ThumbnailUpdateEvent.builder().videoId(videoId).status("processing")
                    .build();
            kafkaTemplate.send("video.thumbnail.events",
                    new Event<ThumbnailUpdateEvent>("ThumbnailUpdateEvent", "1.0", Instant.now(), updatedEvent));
            if (Files.notExists(temp, LinkOption.NOFOLLOW_LINKS))
                Files.createDirectory(temp);
            Path tempInput = temp.resolve(filename + ".mp4");
            if (Files.notExists(tempInput, LinkOption.NOFOLLOW_LINKS))
                Files.createFile(tempInput);
            try (InputStream is = previewStorageService.getInputStream(filename, 0,
                    filesize);
                    OutputStream os = Files.newOutputStream(tempInput, StandardOpenOption.TRUNCATE_EXISTING)) {
                is.transferTo(os);
            }
            double duration = getVideoDuration(tempInput.toFile().getAbsolutePath());
            Path tempDir = temp.resolve("gen");
            if (Files.notExists(tempDir, LinkOption.NOFOLLOW_LINKS))
                Files.createDirectory(tempDir);
            try {
                Path output = tempDir.resolve("thumbnail.png");
                generateThumbnail(tempInput.toString(), output.toString(), duration);

                long size = output.toFile().length();
                try (InputStream inputStream = Files.newInputStream(output)) {
                    Instant instant = Instant.now();
                    String thumbnailName = filename + "_thumbnail_" + instant;
                    thumbnailStorageService.save(inputStream, thumbnailName, size);
                    updatedEvent.setName(thumbnailName);
                    updatedEvent.setCreatedAt(instant);
                    updatedEvent.setSize(size);
                    updatedEvent.setStatus("ready");
                    kafkaTemplate.send("video.thumbnail.events",
                            new Event<ThumbnailUpdateEvent>("ThumbnailUpdateEvent", "1.0", instant, updatedEvent));
                }
            } finally {
                FileUtils.deleteDirectory(tempDir.toFile());
                Files.deleteIfExists(tempInput);
                FileUtils.deleteDirectory(temp.toFile());
            }
        } catch (IOException e) {
            log.warn("Failed to completely delete temp dir, but ignoring.", e);
        } catch (Exception e) {

            ThumbnailUpdateEvent failedThumbnailEvent = ThumbnailUpdateEvent.builder()
                    .videoId(videoId)
                    .status("failed")
                    .build();
            kafkaTemplate.send("video.thumbnail.events", new Event<ThumbnailUpdateEvent>("ThumbnailUpdateEvent", "1.0",
                    Instant.now(), failedThumbnailEvent));
            throw new RuntimeException("Failed to create thumbnail: ", e);
        }
    }

    private double getVideoDuration(String filePath) throws IOException, InterruptedException {

        ProcessBuilder processBuilder = new ProcessBuilder(
                "ffprobe",
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                filePath);
        Process process = processBuilder.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String durationStr = reader.readLine();
            process.waitFor();

            if (durationStr == null || durationStr.isBlank()) {

                BufferedReader bf = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                log.error("Couldn't figure out Duration of the Video: {}", bf.readLine());
                throw new RuntimeException("Unable to read Duration");
            }
            return Double.parseDouble(durationStr);
        }
    }

    private void generateThumbnail(String input, String output, double duration) throws Exception {
        String startStamp = String.format(Locale.US, "%.2f", duration / 3);
        int scanLength = 5;
        List<String> command = List.of(
                "ffmpeg",
                "-ss", startStamp,
                "-i", input,
                "-t", String.valueOf(scanLength),
                "-vf", "thumbnail",
                "-frames:v", "1",
                "-an",
                "-preset", "ultrafast",
                output);
        new ProcessBuilder(command).inheritIO().start().waitFor();
    }
}
