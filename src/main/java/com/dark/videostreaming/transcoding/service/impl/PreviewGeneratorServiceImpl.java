package com.dark.videostreaming.transcoding.service.impl;

import java.io.BufferedReader;
import java.io.BufferedWriter;
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
import com.dark.videostreaming.transcoding.event.model.VideoUploadedEvent;
import com.dark.videostreaming.transcoding.service.PreviewGeneratorService;
import com.dark.videostreaming.transcoding.service.PreviewStorageService;
import com.dark.videostreaming.transcoding.service.VideoStorageService;

import org.apache.commons.io.FileUtils;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
@Service
public class PreviewGeneratorServiceImpl implements PreviewGeneratorService {
    private final VideoStorageService videoStorageService;
    private final PreviewStorageService previewStorageService;
    private final KafkaTemplate<String, Event<?>> kafkaTemplate;

    private final Path temp = Paths.get(System.getProperty("user.dir")).resolve("tmp");

    @Async
    @Override
    public void generatePreview(VideoUploadedEvent event) {
        generateAndStorePreview(event.videoId(), event.fileName(), event.fileSize());
    }

    private void generateAndStorePreview(long videoId, String filename, long filesize) {
        try {
            PreviewUpdateEvent updateEvent = PreviewUpdateEvent.builder().videoId(videoId).status("processing").build();
            kafkaTemplate.send("video.preview.events",
                    new Event<PreviewUpdateEvent>("PreviewUpdateEvent", "1.0", Instant.now(), updateEvent));
            if (Files.notExists(temp, LinkOption.NOFOLLOW_LINKS))
                Files.createDirectory(temp);
            Path tempInput = temp.resolve(filename + ".mp4");
            if (Files.notExists(tempInput, LinkOption.NOFOLLOW_LINKS))
                Files.createFile(tempInput);
            try (InputStream is = videoStorageService.getInputStream(filename, 0,
                    filesize);
                    OutputStream os = Files.newOutputStream(tempInput, StandardOpenOption.TRUNCATE_EXISTING)) {
                is.transferTo(os);
            }
            double duration = getVideoDuration(tempInput.toFile().getAbsolutePath());
            Path tempDir = temp.resolve("gen");
            if (Files.notExists(tempDir, LinkOption.NOFOLLOW_LINKS))
                Files.createDirectory(tempDir);
            try {
                generateVideoClips(tempInput.toString(), tempDir.toString(), duration);
                createConcatList(tempDir);

                Path outputPreview = tempDir.resolve("generated_preview.mp4");

                concatClips(tempDir.toString(), outputPreview.toString());
                long size = outputPreview.toFile().length();
                try (InputStream inputStream = Files.newInputStream(outputPreview)) {
                    Instant instant = Instant.now();
                    String previewFilename = filename + "_preview_" + instant;
                    updateEvent.setName(previewFilename);
                    updateEvent.setSize(size);
                    updateEvent.setCreatedAt(instant);
                    updateEvent.setStatus("ready");
                    previewStorageService.save(inputStream, previewFilename, size);
                    kafkaTemplate.send("video.preview.events",
                            new Event<PreviewUpdateEvent>("PreviewUpdateEvent", "1.0", instant, updateEvent));
                }
            } finally {
                FileUtils.deleteDirectory(tempDir.toFile());
                Files.deleteIfExists(tempInput);
                FileUtils.deleteDirectory(temp.toFile());
            }
        } catch (IOException e) {
            log.warn("Failed to completely delete temp dir, but ignoring.", e);
        } catch (Exception e) {
            PreviewUpdateEvent failedPreviewEvent = PreviewUpdateEvent.builder()
                    .videoId(videoId)
                    .status("failed")
                    .build();
            kafkaTemplate.send("video.preview.events", new Event<PreviewUpdateEvent>("PreviewUpdateEvent", "1.0",
                    Instant.now(), failedPreviewEvent));
            throw new RuntimeException("Failed to create Preview: ", e);
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

    private void generateVideoClips(String input, String outputDir, double duration) throws Exception {
        int clipLength = 5;
        List<Double> startTimes = List.of(duration * 0.25, duration * 0.5, duration * 0.75);
        for (int i = 0; i < startTimes.size(); i++) {
            String output = outputDir + "/clip" + i + ".mp4";
            List<String> command = List.of(
                    "ffmpeg",
                    "-ss", String.format(Locale.US, "%.2f", startTimes.get(i)),
                    "-i", input,
                    "-t", String.valueOf(clipLength),
                    "-c:v", "libx264",
                    "-an",
                    "-preset", "ultrafast",
                    output);
            new ProcessBuilder(command).inheritIO().start().waitFor();
        }
    }

    private void createConcatList(Path dir) throws IOException {
        Path listPath = dir.resolve("filelist.txt");
        try (BufferedWriter writer = Files.newBufferedWriter(listPath)) {
            for (int i = 0; i < 3; i++) {
                writer.write("file 'clip" + i + ".mp4'\n");
            }
        }
    }

    private void concatClips(String dir, String outputFile) throws Exception {
        List<String> command = List.of(
                "ffmpeg",
                "-f", "concat",
                "-safe", "0",
                "-i", dir + "/filelist.txt",
                "-c", "copy",
                outputFile);
        new ProcessBuilder(command).inheritIO().start().waitFor();
    }

}
