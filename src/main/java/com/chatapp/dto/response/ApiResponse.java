package com.chatapp.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.auto.value.AutoValue.Builder;

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
}
