package com.flagship.server.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Standard pagination response wrapper for all FlagShip list endpoints (C4).
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public static <E, T> PageResponse<T> from(Page<E> pageData, Function<E, T> mapper) {
        List<T> mapped = pageData.getContent().stream().map(mapper).toList();
        return new PageResponse<>(
                mapped,
                pageData.getNumber(),
                pageData.getSize(),
                pageData.getTotalElements(),
                pageData.getTotalPages(),
                pageData.isLast()
        );
    }

    public static <T> PageResponse<T> ofList(List<T> allItems, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, size);
        int totalElements = allItems.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);
        int fromIndex = Math.min(safePage * safeSize, totalElements);
        int toIndex = Math.min(fromIndex + safeSize, totalElements);
        List<T> slice = allItems.subList(fromIndex, toIndex);
        boolean last = totalPages == 0 || safePage >= totalPages - 1;
        return new PageResponse<>(slice, safePage, safeSize, totalElements, totalPages, last);
    }
}
