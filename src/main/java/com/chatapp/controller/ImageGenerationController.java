package com.chatapp.controller;

import com.chatapp.service.ImageGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST Controller để xử lý các yêu cầu tạo hình ảnh.
 * Cung cấp endpoints để tạo hình ảnh bằng AI dựa trên văn bản mô tả và hình ảnh
 * tham chiếu tùy chọn.
 */
@RestController
@RequestMapping("/api/images")
@CrossOrigin(origins = "*")
@Tag(name = "Tạo Hình Ảnh", description = "API để tạo hình ảnh")
public class ImageGenerationController {

    private final ImageGenerationService imageGenerationService;

    /**
     * Constructor cho ImageGenerationController.
     *
     * @param imageGenerationService Service xử lý logic tạo hình ảnh
     */
    public ImageGenerationController(ImageGenerationService imageGenerationService) {
        this.imageGenerationService = imageGenerationService;
    }

    /**
     * Tạo hình ảnh dựa trên văn bản mô tả và hình ảnh tham chiếu tùy chọn.
     *
     * @param prompt          Văn bản mô tả hình ảnh cần tạo
     * @param referenceImages Hình ảnh tham chiếu tùy chọn để hướng dẫn việc tạo ảnh
     * @return ResponseEntity chứa danh sách URL của hình ảnh đã tạo
     */
    @Operation(summary = "Tạo hình ảnh bằng AI", description = "Tạo hình ảnh từ văn bản mô tả và hình ảnh tham chiếu tùy chọn sử dụng Google Gemini AI", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tạo hình ảnh thành công", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ImageGenerationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dữ liệu đầu vào không hợp lệ - Prompt không được để trống"),
            @ApiResponse(responseCode = "401", description = "Không được phép - Token không hợp lệ hoặc đã hết hạn"),
            @ApiResponse(responseCode = "500", description = "Lỗi server nội bộ - Không thể tạo hình ảnh")
    })
    @PostMapping("/generate")
    public ResponseEntity<ImageGenerationResponse> generateImages(
            @Parameter(description = "Văn bản mô tả hình ảnh cần tạo (bắt buộc)", required = true, example = "Vẽ một con mèo dễ thương đang ngủ trên ghế sofa") @RequestParam("prompt") String prompt,

            @Parameter(description = "Danh sách hình ảnh tham chiếu để hướng dẫn việc tạo ảnh (tùy chọn)", content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE)) @RequestParam(value = "referenceImages", required = false) List<MultipartFile> referenceImages,

            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

        try {
            // Kiểm tra tính hợp lệ của prompt
            if (prompt == null || prompt.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new ImageGenerationResponse(false, "Prompt không được để trống", null));
            }

            // Tạo hình ảnh
            List<String> imageUrls = imageGenerationService.generateImages(prompt, referenceImages);

            if (imageUrls.isEmpty()) {
                return ResponseEntity.internalServerError()
                        .body(new ImageGenerationResponse(false, "Không thể tạo hình ảnh", null));
            }

            return ResponseEntity.ok(
                    new ImageGenerationResponse(true, "Tạo hình ảnh thành công", imageUrls));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new ImageGenerationResponse(false, "Lỗi server: " + e.getMessage(), null));
        }
    }

    /**
     * Endpoint kiểm tra sức khỏe để xác minh service tạo hình ảnh đang hoạt động.
     *
     * @return ResponseEntity với trạng thái service
     */
    @Operation(summary = "Kiểm tra sức khỏe service", description = "Kiểm tra xem service tạo hình ảnh có đang hoạt động bình thường không")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Service đang hoạt động bình thường", content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Image Generation Service is running")))
    })
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Service tạo hình ảnh đang hoạt động");
    }

    /**
     * DTO phản hồi cho các yêu cầu tạo hình ảnh.
     *
     * @param success   Cho biết thao tác có thành công hay không
     * @param message   Thông báo phản hồi
     * @param imageUrls Danh sách URL của hình ảnh đã tạo từ S3
     */
    @Schema(description = "Phản hồi cho yêu cầu tạo hình ảnh")
    public record ImageGenerationResponse(
            @Schema(description = "Trạng thái thành công của thao tác", example = "true") boolean success,

            @Schema(description = "Thông báo mô tả kết quả", example = "Tạo hình ảnh thành công") String message,

            @Schema(description = "Danh sách URL của hình ảnh đã tạo", example = "[\"https://bucket.s3.amazonaws.com/image1.png\", \"https://bucket.s3.amazonaws.com/image2.jpg\"]") List<String> imageUrls) {
    }
}