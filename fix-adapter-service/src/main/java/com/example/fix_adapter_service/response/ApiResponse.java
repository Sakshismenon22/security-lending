package com.example.fix_adapter_service.response;

import java.time.Instant;

public record ApiResponse<T>(boolean success,T data,Instant timestamp) {
    public static <T> ApiResponse<T> ok(T value) {return new ApiResponse<>(true,value,Instant.now());}
}
