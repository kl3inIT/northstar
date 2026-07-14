package com.northstar.api.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.northstar.core.artifact.TemporaryArtifact;
import com.northstar.core.artifact.TemporaryArtifactMetadata;
import com.northstar.core.study.SpeakingCoach;
import com.northstar.core.study.SpeakingService;
import com.northstar.core.study.SpeechAssessor;
import com.northstar.core.study.StudyService;
import com.northstar.core.study.VocabAudioPracticeService;
import com.northstar.core.study.VocabCoach;
import com.northstar.core.study.VocabDeckService;
import com.northstar.core.study.VocabService;
import com.northstar.core.study.WritingService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;

class StudyControllerArtifactTests {

    @Test
    void enrichmentArtifactUsesPrivateEphemeralResponseHeaders() {
        VocabEnrichmentJobService jobs = mock(VocabEnrichmentJobService.class);
        UUID jobId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        byte[] bytes = {1, 2, 3};
        Instant created = Instant.parse("2026-07-14T00:00:00Z");
        TemporaryArtifactMetadata metadata = new TemporaryArtifactMetadata(artifactId,
                "word.mp3", "audio/mpeg", bytes.length, "hash", created,
                created.plusSeconds(1800));
        when(jobs.artifact(jobId, artifactId)).thenReturn(new TemporaryArtifact(metadata, bytes));
        StudyController controller = new StudyController(mock(StudyService.class),
                mock(VocabService.class), mock(VocabCoach.class), mock(VocabDeckService.class), jobs,
                mock(VocabAudioPracticeService.class), mock(WritingService.class),
                mock(SpeakingService.class),
                StudyControllerArtifactTests.<SpeechAssessor>provider(),
                StudyControllerArtifactTests.<SpeakingCoach>provider());

        var response = controller.vocabEnrichmentArtifact(jobId, artifactId);

        assertThat(response.getBody()).containsExactly(bytes);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().getFirst("Content-Security-Policy"))
                .isEqualTo("sandbox; default-src 'none'");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("inline");
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("audio/mpeg");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(bytes.length);
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider() {
        return mock(ObjectProvider.class);
    }
}
