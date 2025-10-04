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
import java.util.List;
import java.util.Locale;

import jakarta.transaction.Transactional;

import com.dark.videostreaming.transcoding.entity.File;
import com.dark.videostreaming.transcoding.entity.FileMetadata;
import com.dark.videostreaming.transcoding.entity.Preview;
import com.dark.videostreaming.transcoding.event.PreviewCreationEvent;
import com.dark.videostreaming.transcoding.event.ThumbnailCreationEvent;
import com.dark.videostreaming.transcoding.repository.FileRepository;
import com.dark.videostreaming.transcoding.repository.PreviewRepository;
import com.dark.videostreaming.transcoding.service.PreviewGeneratorService;
import com.dark.videostreaming.transcoding.service.PreviewStorageService;
import com.dark.videostreaming.transcoding.service.VideoStorageService;

import org.apache.commons.io.FileUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
@Service
public class PreviewGeneratorServiceImpl implements PreviewGeneratorService {
    private final FileRepository fileRepository;
    private final PreviewRepository previewRepository;
    private final VideoStorageService videoStorageService;
    private final PreviewStorageService previewStorageService;
    private final ApplicationEventPublisher eventPublisher;

    private final Path temp = Paths.get(System.getProperty("user.dir")).resolve("tmp");

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Override
    public void generatePreview(PreviewCreationEvent event) {
        File video = fileRepository.findById(event.getFileId()).orElseThrow();
        generateAndStorePreview(video.getPreview(), video.getMetadata());
        eventPublisher.publishEvent(new ThumbnailCreationEvent(event.getFileId()));
    }

    @Transactional
    private void generateAndStorePreview(Preview preview, FileMetadata metadata) {
        try {
            preview.setStatus(Preview.PreviewStatus.PROCESSING);
            previewRepository.save(preview);
            if (Files.notExists(temp, LinkOption.NOFOLLOW_LINKS))
                Files.createDirectory(temp);
            Path tempInput = temp.resolve(metadata.getUuid() + ".mp4");
            if (Files.notExists(tempInput, LinkOption.NOFOLLOW_LINKS))
                Files.createFile(tempInput);
            try (InputStream is = videoStorageService.getInputStream(metadata.getUuid().toString(), 0,
                    metadata.getSize());
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
                    previewStorageService.save(inputStream, preview.getName(), size);
                    preview.setSize(size);
                    preview.setStatus(Preview.PreviewStatus.READY);
                    previewRepository.save(preview);
                }
            } finally {
                FileUtils.deleteDirectory(tempDir.toFile());
                Files.deleteIfExists(tempInput);
                FileUtils.deleteDirectory(temp.toFile());
            }
        } catch (IOException e) {
            log.warn("Failed to completely delete temp dir, but ignoring.", e);
        } catch (Exception e) {
            preview.setStatus(Preview.PreviewStatus.FAILED);
            previewRepository.save(preview);
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
