package com.shahbytes.feedme.dtos;

import java.util.List;

public record TimelinePageResponse(String timelineOwnerId, TimelineMode mode, int totalItems,
                                   List<FeedItemResponse> items, String nextCursor) {
}
