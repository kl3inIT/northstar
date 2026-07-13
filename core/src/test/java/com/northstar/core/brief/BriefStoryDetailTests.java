package com.northstar.core.brief;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class BriefStoryDetailTests {

    @Test
    void omitsPreviousStoryWhenTheProviderHasNoEarlierVersion() throws Exception {
        BriefStoryDetail detail = new BriefStoryDetail(null, "Summary", List.of(), null, List.of());

        String json = new ObjectMapper().writeValueAsString(detail);

        assertThat(json).doesNotContain("previousStory");
    }
}
