package com.northstar.core.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TemporaryArtifactValueTests {

    @Test
    void writeAndArtifactDefensivelyCopyBytes() {
        byte[] source = {1, 2, 3};
        TemporaryArtifactWrite write = new TemporaryArtifactWrite("preview.png", "IMAGE/PNG", source);
        source[0] = 9;

        byte[] fromWrite = write.data();
        assertThat(fromWrite).containsExactly(1, 2, 3);
        fromWrite[1] = 9;
        assertThat(write.data()).containsExactly(1, 2, 3);

        Instant created = Instant.parse("2026-07-14T00:00:00Z");
        TemporaryArtifactMetadata metadata = new TemporaryArtifactMetadata(UUID.randomUUID(),
                "preview.png", "image/png", 3, "hash", created, created.plusSeconds(60));
        TemporaryArtifact artifact = new TemporaryArtifact(metadata, write.data());
        byte[] fromArtifact = artifact.data();
        fromArtifact[2] = 9;
        assertThat(artifact.data()).containsExactly(1, 2, 3);
    }

    @Test
    void scopeAndWriteRejectUnsafeBoundaryValues() {
        assertThatThrownBy(() -> new TemporaryArtifactScope("owner", "session", "../image"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TemporaryArtifactWrite("../preview.png", "image/png", new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TemporaryArtifactWrite("preview.png", "image/png\r\nX: y", new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
