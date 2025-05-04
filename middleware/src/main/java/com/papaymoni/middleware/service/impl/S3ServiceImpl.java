package com.papaymoni.middleware.service.impl;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.util.IOUtils;
import com.papaymoni.middleware.service.S3Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;

@Slf4j
@Service
public class S3ServiceImpl implements S3Service {

    @Value("${aws.access.key.id}")
    private String accessKeyId;

    @Value("${aws.secret.access.key}")
    private String secretAccessKey;

    @Value("${aws.s3.region}")
    private String region;

    private AmazonS3 s3Client;

    @PostConstruct
    public void init() {
        BasicAWSCredentials awsCreds = new BasicAWSCredentials(accessKeyId, secretAccessKey);
        this.s3Client = AmazonS3ClientBuilder.standard()
                .withRegion(region)
                .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
                .build();
    }

    @Override
    public String uploadFile(String bucketName, String key, InputStream inputStream, String contentType) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);

            s3Client.putObject(bucketName, key, inputStream, metadata);

            // Return the S3 URL
            return s3Client.getUrl(bucketName, key).toString();
        } catch (Exception e) {
            log.error("Error uploading file to S3: {}", e.getMessage());
            throw new RuntimeException("Failed to upload file to S3", e);
        }
    }

    @Override
    public byte[] downloadFile(String bucketName, String key) {
        try {
            S3Object s3Object = s3Client.getObject(bucketName, key);
            return IOUtils.toByteArray(s3Object.getObjectContent());
        } catch (Exception e) {
            log.error("Error downloading file from S3: {}", e.getMessage());
            throw new RuntimeException("Failed to download file from S3", e);
        }
    }

    @Override
    public String generatePresignedUrl(String bucketName, String key, int expirationMinutes) {
        Date expiration = new Date();
        expiration.setTime(expiration.getTime() + expirationMinutes * 60 * 1000);

        GeneratePresignedUrlRequest generatePresignedUrlRequest =
                new GeneratePresignedUrlRequest(bucketName, key)
                        .withMethod(com.amazonaws.HttpMethod.GET)
                        .withExpiration(expiration);

        URL url = s3Client.generatePresignedUrl(generatePresignedUrlRequest);
        return url.toString();
    }
}