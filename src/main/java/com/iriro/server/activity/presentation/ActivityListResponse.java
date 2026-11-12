package com.iriro.server.activity.presentation;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
public class ActivityListResponse {

    private List<ActivityResponse> content;

    private int page;

    private int size;

    private long totalElements;

    private int totalPages;

    private boolean hasNext;

    public static ActivityListResponse from(
            Page<ActivityResponse> result
    ) {

        return ActivityListResponse.builder()
                .content(result.getContent())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .hasNext(result.hasNext())
                .build();
    }
}