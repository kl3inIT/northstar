package com.northstar.api.brief;

import com.northstar.core.brief.BriefFeed;
import com.northstar.core.brief.BriefFeedProvider;
import com.northstar.core.brief.BriefStoryDetail;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only live provider feed; app-owned Northstar Briefs remain durable notes. */
@RestController
@RequestMapping("/api/briefs")
class BriefController {

    private final BriefFeedProvider huggingNews;

    BriefController(BriefFeedProvider huggingNews) {
        this.huggingNews = huggingNews;
    }

    @GetMapping("/huggingnews")
    @Operation(operationId = "getHuggingNewsFeed")
    BriefFeed huggingNews() {
        return huggingNews.feed();
    }

    @GetMapping("/huggingnews/{topic}/{slug}")
    @Operation(operationId = "getHuggingNewsStory")
    BriefStoryDetail huggingNewsStory(@PathVariable String topic, @PathVariable String slug) {
        return huggingNews.story(topic, slug);
    }
}
