package com.flagship.server.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public final class FlagshipPaging {

    private FlagshipPaging() {
    }

    public static Pageable of(Integer page, Integer size) {
        int safePage = (page == null || page < 0) ? 0 : page;
        int safeSize = (size == null || size < 1) ? 20 : Math.min(size, 200);
        return PageRequest.of(safePage, safeSize);
    }
}
