package com.dark.videostreaming.transcoding.service.impl;

import com.dark.videostreaming.transcoding.config.MinioConfig;
import com.dark.videostreaming.transcoding.service.VideoStorageService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@RequiredArgsConstructor
@Service
public class VideoStorageServiceImpl implements VideoStorageService {
    
    private final MinioClient client;
    
    @Value("${minio.object-part-size}")
    private Long objectPartSize;
    
    @Override
    public void save(InputStream file, String name, long size) throws Exception {
        client.putObject(
                PutObjectArgs.builder()
                        .bucket(MinioConfig.VIDEO_BUCKET_NAME)
                        .object(name)
                        .stream(file, size, objectPartSize)
                        .build()
        );
    }

    @Override
    public void delete(String name) throws Exception {
        client.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(MinioConfig.VIDEO_BUCKET_NAME)
                        .object(name)
                        .build()
        );
    }

    @Override
    public InputStream getInputStream(String name, long offset, long length) throws Exception {
        return client.getObject(
                GetObjectArgs.builder()
                        .bucket(MinioConfig.VIDEO_BUCKET_NAME)
                        .object(name)
                        .offset(offset)
                        .length(length)
                        .build()
        );
    }
    
    
}
