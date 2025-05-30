package com.chatapp.controller;

import com.chatapp.service.TextGenerationService;
import com.google.common.collect.ImmutableMap;
import com.google.genai.ResponseStream;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller cho việc tạo văn bản sử dụng Google Gemini AI.
 * Cung cấp các endpoint khác nhau cho các loại tạo văn bản khác nhau.
 */
@RestController
@RequestMapping("/api/text-generation")
@CrossOrigin(origins = "*")
public class TextGenerationController {

    private static final Logger logger = LoggerFactory.getLogger(TextGenerationController.class);

    private final TextGenerationService textGenerationService;

    public TextGenerationController(TextGenerationService textGenerationService) {
        this.textGenerationService = textGenerationService;
    }

    /**
     * Tạo văn bản đơn giản dựa trên prompt.
     *
     * @param request Request chứa prompt
     * @return Phản hồi văn bản được tạo
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateText(@RequestBody Map<String, String> request) {
        try {
            String prompt = request.get("prompt");
            if (prompt == null || prompt.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Prompt không được để trống"));
            }

            // Sử dụng cấu hình đơn giản
            GenerateContentConfig config = textGenerationService.createSimpleConfig(1000, 0.7);
            String generatedText = textGenerationService.generateText(prompt, config);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "text", generatedText,
                    "model", "gemini-2.0-flash"));

        } catch (Exception e) {
            logger.error("Lỗi trong endpoint generateText: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Lỗi khi tạo văn bản: " + e.getMessage()));
        }
    }

    /**
     * Tạo văn bản với hình ảnh (đầu vào đa phương tiện).
     *
     * @param prompt Prompt văn bản
     * @param images Hình ảnh tùy chọn
     * @return Phản hồi văn bản được tạo
     */
    @PostMapping(value = "/generate-with-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> generateTextWithImages(
            @RequestParam("prompt") String prompt,
            @RequestParam(value = "images", required = false) List<MultipartFile> images) {
        try {
            if (prompt == null || prompt.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Prompt không được để trống"));
            }

            GenerateContentConfig config = textGenerationService.createSimpleConfig(1500, 0.8);
            String generatedText = textGenerationService.generateTextWithImages(prompt, images, config);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "text", generatedText,
                    "hasImages", images != null && !images.isEmpty(),
                    "imageCount", images != null ? images.size() : 0));

        } catch (Exception e) {
            logger.error("Lỗi trong endpoint generateTextWithImages: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Lỗi khi tạo văn bản với hình ảnh: " + e.getMessage()));
        }
    }

    /**
     * Tạo văn bản bất đồng bộ.
     *
     * @param request Request chứa prompt
     * @return CompletableFuture với văn bản được tạo
     */
    @PostMapping("/generate-async")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> generateTextAsync(
            @RequestBody Map<String, String> request) {

        String prompt = request.get("prompt");
        if (prompt == null || prompt.trim().isEmpty()) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest()
                            .body(Map.<String, Object>of("error", "Prompt không được để trống")));
        }

        GenerateContentConfig config = textGenerationService.createSimpleConfig(1200, 0.9);

        return textGenerationService.generateTextAsync(prompt, config)
                .thenApply(generatedText -> ResponseEntity.ok(Map.<String, Object>of(
                        "success", true,
                        "text", generatedText,
                        "async", true)))
                .exceptionally(throwable -> {
                    logger.error("Lỗi trong endpoint generateTextAsync: {}", throwable.getMessage(), throwable);
                    return ResponseEntity.internalServerError()
                            .body(Map.<String, Object>of("error",
                                    "Lỗi khi tạo văn bản bất đồng bộ: " + throwable.getMessage()));
                });
    }

    /**
     * Tạo văn bản với cấu hình nâng cao.
     *
     * @param request Request chứa prompt và các tùy chọn cấu hình
     * @return Phản hồi văn bản được tạo
     */
    @PostMapping("/generate-advanced")
    public ResponseEntity<Map<String, Object>> generateTextAdvanced(@RequestBody Map<String, Object> request) {
        try {
            String prompt = (String) request.get("prompt");
            if (prompt == null || prompt.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Prompt không được để trống"));
            }

            String systemInstruction = (String) request.get("systemInstruction");
            Integer maxTokens = request.get("maxTokens") != null ? ((Number) request.get("maxTokens")).intValue()
                    : 1000;
            Double temperature = request.get("temperature") != null
                    ? ((Number) request.get("temperature")).doubleValue()
                    : 0.7;

            String generatedText = textGenerationService.generateTextWithAdvancedConfig(
                    prompt,
                    systemInstruction,
                    maxTokens,
                    temperature,
                    textGenerationService.createDefaultSafetySettings());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "text", generatedText,
                    "config", Map.of(
                            "maxTokens", maxTokens,
                            "temperature", temperature,
                            "hasSystemInstruction", systemInstruction != null)));

        } catch (Exception e) {
            logger.error("Lỗi trong endpoint generateTextAdvanced: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Lỗi khi tạo văn bản nâng cao: " + e.getMessage()));
        }
    }

    /**
     * Tạo phản hồi JSON có cấu trúc.
     *
     * @param request Request chứa prompt và cấu trúc mong đợi
     * @return Phản hồi JSON được tạo
     */
    @PostMapping("/generate-json")
    public ResponseEntity<Map<String, Object>> generateStructuredResponse(@RequestBody Map<String, String> request) {
        try {
            String prompt = request.get("prompt");
            if (prompt == null || prompt.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Prompt không được để trống"));
            }

            // Schema ví dụ cho một đối tượng người
            Schema schema = Schema.builder()
                    .type("object")
                    .properties(ImmutableMap.of(
                            "name", Schema.builder()
                                    .type(Type.Known.STRING)
                                    .description("Tên của người")
                                    .build(),
                            "age", Schema.builder()
                                    .type(Type.Known.INTEGER)
                                    .description("Tuổi của người")
                                    .build(),
                            "description", Schema.builder()
                                    .type(Type.Known.STRING)
                                    .description("Mô tả về người")
                                    .build()))
                    .required(List.of("name", "description"))
                    .build();

            String jsonResponse = textGenerationService.generateStructuredResponse(prompt, schema);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "jsonResponse", jsonResponse,
                    "structured", true));

        } catch (Exception e) {
            logger.error("Lỗi trong endpoint generateStructuredResponse: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Lỗi khi tạo phản hồi có cấu trúc: " + e.getMessage()));
        }
    }

    /**
     * Tạo phản hồi văn bản streaming.
     * Lưu ý: Đây là implementation đơn giản. Để streaming thực sự tới frontend,
     * bạn cần sử dụng Server-Sent Events (SSE) hoặc WebSocket.
     *
     * @param request Request chứa prompt
     * @return Phản hồi văn bản streaming hoàn chỉnh
     */
    @PostMapping("/generate-stream")
    public ResponseEntity<Map<String, Object>> generateTextStream(@RequestBody Map<String, String> request) {
        try {
            String prompt = request.get("prompt");
            if (prompt == null || prompt.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Prompt không được để trống"));
            }

            GenerateContentConfig config = textGenerationService.createSimpleConfig(1000, 0.8);

            StringBuilder fullResponse = new StringBuilder();
            try (ResponseStream<GenerateContentResponse> responseStream = textGenerationService
                    .generateTextStream(prompt, config)) {

                for (GenerateContentResponse response : responseStream) {
                    fullResponse.append(response.text());
                }
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "text", fullResponse.toString(),
                    "streamed", true));

        } catch (Exception e) {
            logger.error("Lỗi trong endpoint generateTextStream: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Lỗi khi tạo luồng văn bản: " + e.getMessage()));
        }
    }

    /**
     * Lấy thông tin về các model và khả năng của service.
     *
     * @return Thông tin service
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getServiceInfo() {
        return ResponseEntity.ok(Map.of(
                "service", "TextGenerationService",
                "model", "gemini-2.0-flash",
                "capabilities", List.of(
                        "Tạo văn bản đơn giản",
                        "Tạo văn bản đa phương tiện (text + images)",
                        "Tạo văn bản bất đồng bộ",
                        "Tạo văn bản streaming",
                        "Tạo phản hồi có cấu trúc JSON",
                        "Cấu hình nâng cao với system instructions"),
                "endpoints", List.of(
                        "POST /api/text-generation/generate",
                        "POST /api/text-generation/generate-with-images",
                        "POST /api/text-generation/generate-async",
                        "POST /api/text-generation/generate-advanced",
                        "POST /api/text-generation/generate-json",
                        "POST /api/text-generation/generate-stream",
                        "GET /api/text-generation/info")));
    }
}