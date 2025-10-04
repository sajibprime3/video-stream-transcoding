package com.dark.videostreaming.transcoding.service;

import java.io.InputStream;

public interface MinioStorageService {
    
    void save(InputStream file, String name, long size) throws Exception;
    
    void delete(String name) throws Exception;
    
    InputStream getInputStream(String name, long offset, long length) throws Exception;
    
}
