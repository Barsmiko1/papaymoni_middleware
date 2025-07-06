package com.papaymoni.middleware.service;

import java.io.InputStream;

public interface S3Service {
    String uploadFile(String bucketName, String key, InputStream inputStream, String contentType);
    byte[] downloadFile(String bucketName, String key);
    String generatePresignedUrl(String bucketName, String key, int expirationMinutes);
}

