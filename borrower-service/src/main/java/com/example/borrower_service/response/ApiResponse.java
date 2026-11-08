package com.example.borrower_service.response;

import java.time.Instant;
import java.util.*;

public record ApiResponse<T>(boolean success,T data,String message,Instant timestamp) {
    public static ApiResponse<Object> ok(Object data) { return new ApiResponse<>(true,normalize(data),"Success",Instant.now()); }
    private static Object normalize(Object data) {
        if(data instanceof org.springframework.data.domain.Page<?> page)
            return new PageResponse(page.getContent(),page.getNumber(),page.getSize(),page.getTotalElements(),page.getTotalPages());
        if(data instanceof Map<?,?> map) {
            var result=new LinkedHashMap<String,Object>();
            map.forEach((key,value)->result.put(camel(String.valueOf(key)),normalize(value)));
            return result;
        }
        if(data instanceof List<?> list)return list.stream().map(ApiResponse::normalize).toList();
        return data;
    }
    private static String camel(String key) {
        StringBuilder result=new StringBuilder();boolean upper=false;
        for(char c:key.toCharArray()) {if(c=='_'){upper=true;continue;}result.append(upper?Character.toUpperCase(c):c);upper=false;}
        return result.toString();
    }
}
