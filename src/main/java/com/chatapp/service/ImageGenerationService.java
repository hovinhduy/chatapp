package com.chatapp.service;

import com.google.common.collect.ImmutableList;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service responsible for generating images using Google's Gemini AI model.
 * This service interacts with the Gemini API to generate images based on text
 * prompts
 * and optional reference images.
 */
@Service
public class ImageGenerationService {

    private final String imageGenerationModel = "gemini-2.0-flash-exp-image-generation";

    private final Client genaiClient;
    private final FileStorageService fileStorageService;

    /**
     * Constructs an ImageGenerationService with the required dependencies.
     *
     * @param genaiClient        Google GenAI client for API communication
     * @param fileStorageService Service for uploading files to S3
     */
    public ImageGenerationService(Client genaiClient, FileStorageService fileStorageService) {
        this.genaiClient = genaiClient;
        this.fileStorageService = fileStorageService;
    }

    /**
     * Generates images based on a text prompt and optional reference images.
     *
     * @param prompt Text description of the image to generate
     * @param images Optional reference images to guide the generation (can be null)
     * @return List of image URLs from S3
     */
    public List<String> generateImages(String prompt, @Nullable List<MultipartFile> images) {
        List<Part> parts = new ArrayList<>();
        parts.add(Part.fromText(prompt)); // Add prompt

        if (images != null) {
            List<Part> imageParts = images.stream()
                    .map(image -> {
                        try {
                            return Part.fromBytes(image.getBytes(), image.getContentType());
                        } catch (IOException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();
            parts.addAll(imageParts); // Add images
        }

        Content content = Content.builder().parts(parts).build();
        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseModalities(List.of("Text", "Image"))
                .build();

        try {
            GenerateContentResponse response = this.genaiClient.models.generateContent(imageGenerationModel, content,
                    config);
            List<Image> generatedImages = getImages(response);

            List<String> imageUrls = new ArrayList<>();
            for (Image image : generatedImages) {
                try {
                    // Tạo CustomMultipartFile từ byte array để upload lên S3
                    CustomMultipartFile multipartFile = new CustomMultipartFile(
                            image.imageBytes(),
                            image.imageName(),
                            image.mimeType());

                    String imageUrl = fileStorageService.uploadFile(multipartFile);
                    imageUrls.add(imageUrl);
                } catch (IOException e) {
                    System.out.println("Lỗi khi upload ảnh lên S3: " + e.getMessage());
                }
            }

            return imageUrls;
        } catch (Exception e) {
            System.out.println("Error generating images: " + e.getMessage());
        }

        return Collections.emptyList();
    }

    /**
     * Extracts image data from the Gemini API response.
     *
     * @param response The response from the Gemini API
     * @return List of Image objects containing the generated images
     */
    private List<Image> getImages(GenerateContentResponse response) {
        ImmutableList<Part> responseParts = response.parts();
        if (responseParts == null || responseParts.isEmpty()) {
            return Collections.emptyList();
        }
        return responseParts
                .stream()
                .map(Part::inlineData)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(inlineData -> inlineData.data().isPresent())
                .map(inlineData -> {
                    MimeType mimeType = MimeType.valueOf(inlineData.mimeType().get()); // imageMimeType
                    return new Image(
                            "generated_%s.%s".formatted(UUID.randomUUID().toString(), mimeType.getSubtype()),
                            inlineData.data().get(), // imageBytes
                            mimeType.toString());
                })
                .toList();
    }

    /**
     * Record class representing a generated image with its name, binary data, and
     * MIME type.
     *
     * @param imageName  The name of the image file
     * @param imageBytes The binary data of the image
     * @param mimeType   The MIME type of the image
     */
    record Image(String imageName, byte[] imageBytes, String mimeType) {
    }

    /**
     * Custom implementation of MultipartFile for generated images
     */
    private static class CustomMultipartFile implements MultipartFile {
        private final byte[] content;
        private final String name;
        private final String contentType;

        public CustomMultipartFile(byte[] content, String name, String contentType) {
            this.content = content;
            this.name = name;
            this.contentType = contentType;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return name;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() throws IOException {
            return content;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            throw new UnsupportedOperationException("transferTo not supported");
        }
    }
}