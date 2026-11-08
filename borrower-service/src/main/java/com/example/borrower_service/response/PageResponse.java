package com.example.borrower_service.response;

import java.util.List;

/** Stable pagination shape independent of Spring Data's internal PageImpl serialization. */
public record PageResponse(List<?> content,int page,int size,long totalElements,int totalPages) {}
