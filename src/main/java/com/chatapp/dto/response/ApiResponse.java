package com.chatapp.dto.response;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success = false;
    private String message;
    private T payload;
    private List<String> errors;
    private String error;
    private Long id;
    private Map<String, Object> additionalData;

    public static class ApiResponseBuilder<T> {
        private Map<String, Object> additionalData;

        public ApiResponseBuilder<T> data(String key, Object value) {
            if (additionalData == null) {
                additionalData = new HashMap<>();
            }
            additionalData.put(key, value);
            return this;
        }
    }
}
