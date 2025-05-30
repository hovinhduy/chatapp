package com.chatapp.service;

import com.google.common.collect.ImmutableList;
import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.*;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Service chịu trách nhiệm tạo văn bản sử dụng mô hình Gemini AI của Google.
 * Service này tương tác với Gemini API để tạo nội dung văn bản dựa trên các
 * prompt,
 * với hỗ trợ đầu vào đa phương tiện (văn bản + hình ảnh), streaming và xử lý
 * bất đồng bộ.
 */
@Service
public class TextGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(TextGenerationService.class);

    private final String textGenerationModel = "gemini-2.0-flash";
    private final Client genaiClient;

    /**
     * Khởi tạo TextGenerationService với các dependency cần thiết.
     *
     * @param genaiClient Google GenAI client để giao tiếp với API
     */
    public TextGenerationService(Client genaiClient) {
        this.genaiClient = genaiClient;
    }

    /**
     * Tạo nội dung văn bản dựa trên prompt với cấu hình tùy chọn.
     *
     * @param prompt Prompt văn bản để tạo
     * @param config Cấu hình tùy chọn cho việc tạo (có thể null)
     * @return Phản hồi văn bản được tạo
     */
    public String generateText(String prompt, @Nullable GenerateContentConfig config) {
        try {
            GenerateContentResponse response = genaiClient.models.generateContent(
                    textGenerationModel,
                    prompt,
                    config);

            String generatedText = response.text();
            logger.info("Tạo văn bản thành công với {} ký tự", generatedText.length());
            return generatedText;

        } catch (Exception e) {
            logger.error("Lỗi khi tạo văn bản: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể tạo văn bản: " + e.getMessage(), e);
        }
    }

    /**
     * Tạo nội dung văn bản với đầu vào đa phương tiện (văn bản + hình ảnh).
     *
     * @param prompt Prompt văn bản để tạo
     * @param images Hình ảnh tùy chọn để bao gồm trong prompt
     * @param config Cấu hình tùy chọn cho việc tạo
     * @return Phản hồi văn bản được tạo
     */
    public String generateTextWithImages(String prompt, @Nullable List<MultipartFile> images,
            @Nullable GenerateContentConfig config) {
        try {
            List<Part> parts = new ArrayList<>();
            parts.add(Part.fromText(prompt));

            // Thêm hình ảnh nếu được cung cấp
            if (images != null && !images.isEmpty()) {
                List<Part> imageParts = images.stream()
                        .map(image -> {
                            try {
                                return Part.fromBytes(image.getBytes(), image.getContentType());
                            } catch (IOException e) {
                                logger.warn("Không thể xử lý hình ảnh: {}", e.getMessage());
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .toList();
                parts.addAll(imageParts);
            }

            Content content = Content.builder().parts(parts).build();

            GenerateContentResponse response = genaiClient.models.generateContent(
                    textGenerationModel,
                    content,
                    config);

            String generatedText = response.text();
            logger.info("Tạo văn bản đa phương tiện thành công với {} ký tự", generatedText.length());
            return generatedText;

        } catch (Exception e) {
            logger.error("Lỗi khi tạo văn bản đa phương tiện: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể tạo văn bản đa phương tiện: " + e.getMessage(), e);
        }
    }

    /**
     * Tạo nội dung văn bản bất đồng bộ.
     *
     * @param prompt Prompt văn bản để tạo
     * @param config Cấu hình tùy chọn cho việc tạo
     * @return CompletableFuture chứa văn bản được tạo
     */
    public CompletableFuture<String> generateTextAsync(String prompt, @Nullable GenerateContentConfig config) {
        return genaiClient.async.models.generateContent(textGenerationModel, prompt, config)
                .thenApply(response -> {
                    String generatedText = response.text();
                    logger.info("Tạo văn bản bất đồng bộ thành công với {} ký tự", generatedText.length());
                    return generatedText;
                })
                .exceptionally(throwable -> {
                    logger.error("Lỗi trong việc tạo văn bản bất đồng bộ: {}", throwable.getMessage(), throwable);
                    throw new RuntimeException("Không thể tạo văn bản bất đồng bộ: " + throwable.getMessage(),
                            throwable);
                });
    }

    /**
     * Tạo nội dung văn bản với phản hồi streaming.
     *
     * @param prompt Prompt văn bản để tạo
     * @param config Cấu hình tùy chọn cho việc tạo
     * @return ResponseStream để streaming các đoạn văn bản
     */
    public ResponseStream<GenerateContentResponse> generateTextStream(String prompt,
            @Nullable GenerateContentConfig config) {
        try {
            return genaiClient.models.generateContentStream(textGenerationModel, prompt, config);
        } catch (Exception e) {
            logger.error("Lỗi khi tạo luồng văn bản: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể tạo luồng văn bản: " + e.getMessage(), e);
        }
    }

    /**
     * Tạo văn bản với cấu hình nâng cao bao gồm hướng dẫn hệ thống,
     * cài đặt an toàn và schema phản hồi.
     *
     * @param prompt            Prompt văn bản để tạo
     * @param systemInstruction Hướng dẫn hệ thống tùy chọn để định hướng mô hình
     * @param maxTokens         Số token đầu ra tối đa
     * @param temperature       Nhiệt độ cho độ ngẫu nhiên (0.0-2.0)
     * @param safetySettings    Cài đặt an toàn tùy chọn
     * @return Phản hồi văn bản được tạo
     */
    public String generateTextWithAdvancedConfig(String prompt,
            @Nullable String systemInstruction,
            @Nullable Integer maxTokens,
            @Nullable Double temperature,
            @Nullable List<SafetySetting> safetySettings) {
        try {
            GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder()
                    .candidateCount(1);

            if (maxTokens != null) {
                configBuilder.maxOutputTokens(maxTokens);
            }

            if (temperature != null) {
                configBuilder.temperature(temperature.floatValue());
            }

            if (systemInstruction != null) {
                Content sysInstruction = Content.fromParts(Part.fromText(systemInstruction));
                configBuilder.systemInstruction(sysInstruction);
            }

            if (safetySettings != null && !safetySettings.isEmpty()) {
                configBuilder.safetySettings(safetySettings);
            }

            GenerateContentConfig config = configBuilder.build();

            return generateText(prompt, config);

        } catch (Exception e) {
            logger.error("Lỗi khi tạo văn bản với cấu hình nâng cao: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể tạo văn bản với cấu hình nâng cao: " + e.getMessage(), e);
        }
    }

    /**
     * Tạo phản hồi JSON có cấu trúc dựa trên schema được cung cấp.
     *
     * @param prompt         Prompt văn bản để tạo
     * @param responseSchema Schema cho phản hồi JSON mong đợi
     * @return Phản hồi JSON được tạo dưới dạng chuỗi
     */
    public String generateStructuredResponse(String prompt, Schema responseSchema) {
        try {
            GenerateContentConfig config = GenerateContentConfig.builder()
                    .responseMimeType("application/json")
                    .responseSchema(responseSchema)
                    .candidateCount(1)
                    .build();

            return generateText(prompt, config);

        } catch (Exception e) {
            logger.error("Lỗi khi tạo phản hồi có cấu trúc: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể tạo phản hồi có cấu trúc: " + e.getMessage(), e);
        }
    }

    /**
     * Tạo cài đặt an toàn mặc định cho việc tạo nội dung.
     *
     * @return Danh sách các cài đặt an toàn mặc định
     */
    public List<SafetySetting> createDefaultSafetySettings() {
        return ImmutableList.of(
                SafetySetting.builder()
                        .category(HarmCategory.Known.HARM_CATEGORY_HATE_SPEECH)
                        .threshold(HarmBlockThreshold.Known.BLOCK_MEDIUM_AND_ABOVE)
                        .build(),
                SafetySetting.builder()
                        .category(HarmCategory.Known.HARM_CATEGORY_DANGEROUS_CONTENT)
                        .threshold(HarmBlockThreshold.Known.BLOCK_MEDIUM_AND_ABOVE)
                        .build(),
                SafetySetting.builder()
                        .category(HarmCategory.Known.HARM_CATEGORY_HARASSMENT)
                        .threshold(HarmBlockThreshold.Known.BLOCK_MEDIUM_AND_ABOVE)
                        .build(),
                SafetySetting.builder()
                        .category(HarmCategory.Known.HARM_CATEGORY_SEXUALLY_EXPLICIT)
                        .threshold(HarmBlockThreshold.Known.BLOCK_MEDIUM_AND_ABOVE)
                        .build());
    }

    /**
     * Phương thức hỗ trợ để tạo cấu hình đơn giản với các cài đặt thông thường.
     *
     * @param maxTokens   Token đầu ra tối đa
     * @param temperature Nhiệt độ cho độ ngẫu nhiên
     * @return Instance của GenerateContentConfig
     */
    public GenerateContentConfig createSimpleConfig(int maxTokens, double temperature) {
        return GenerateContentConfig.builder()
                .maxOutputTokens(maxTokens)
                .temperature((float) temperature)
                .candidateCount(1)
                .safetySettings(createDefaultSafetySettings())
                .build();
    }
}