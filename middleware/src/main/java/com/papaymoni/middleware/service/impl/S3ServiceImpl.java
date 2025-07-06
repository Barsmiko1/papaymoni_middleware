package com.papaymoni.middleware.service.impl;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.util.IOUtils;
import com.papaymoni.middleware.service.S3Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
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

    @Value("${aws.s3.bucket.receipts:test-papaymoni-receipts}")
    private String receiptsBucket;

    private AmazonS3 s3Client;

    @PostConstruct
    public void init() {
        try {
            BasicAWSCredentials awsCreds = new BasicAWSCredentials(accessKeyId, secretAccessKey);
            this.s3Client = AmazonS3ClientBuilder.standard()
                    .withRegion(region)
                    .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
                    .build();

            // Verify bucket exists or create it
            if (!s3Client.doesBucketExistV2(receiptsBucket)) {
                log.info("Creating S3 bucket: {}", receiptsBucket);
                s3Client.createBucket(receiptsBucket);
            }

            log.info("S3 service initialized successfully with bucket: {}", receiptsBucket);
        } catch (Exception e) {
            log.error("Failed to initialize S3 service: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize S3 service", e);
        }
    }

    @Override
    public String uploadFile(String bucketName, String key, InputStream inputStream, String contentType) {
        try {
            byte[] bytes = IOUtils.toByteArray(inputStream);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            metadata.setContentLength(bytes.length);

            PutObjectRequest request = new PutObjectRequest(
                    bucketName,
                    key,
                    new ByteArrayInputStream(bytes),
                    metadata
            );

            s3Client.putObject(request);

            // Return the S3 URL
            String url = s3Client.getUrl(bucketName, key).toString();
            log.info("File uploaded successfully to S3: {}", url);
            return url;
        } catch (Exception e) {
            log.error("Error uploading file to S3: bucketName={}, key={}, error={}",
                    bucketName, key, e.getMessage(), e);
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

