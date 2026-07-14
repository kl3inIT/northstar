package com.northstar.core.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AttachmentTypePolicyTests {

    @Test
    void acceptsUtf8KnowledgeAndSourceFiles() {
        assertThat(AttachmentTypePolicy.inspect("notes.md", "# Notes".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(new AttachmentTypePolicy.AcceptedType(
                        "text/markdown", AttachmentTypePolicy.Kind.DOCUMENT));
        assertThat(AttachmentTypePolicy.inspect("Example.java", "class Example {}".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(new AttachmentTypePolicy.AcceptedType(
                        "text/plain", AttachmentTypePolicy.Kind.DOCUMENT));
        assertThat(AttachmentTypePolicy.inspect("data.json", "{\"ok\":true}".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(new AttachmentTypePolicy.AcceptedType(
                        "application/json", AttachmentTypePolicy.Kind.DOCUMENT));
    }

    @Test
    void acceptsRasterOnlyWhenMagicBytesAreReal() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        assertThat(AttachmentTypePolicy.inspect("capture.png", png).mimeType()).isEqualTo("image/png");
        assertThatThrownBy(() -> AttachmentTypePolicy.inspect(
                "capture.png", "<svg/>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("do not match");
    }

    @Test
    void rejectsArchivesExecutablesAudioVideoAndBinaryMasqueradingAsText() {
        byte[] zip = {'P', 'K', 3, 4};
        assertThatThrownBy(() -> AttachmentTypePolicy.inspect("archive.zip", zip))
                .hasMessage("Unsupported attachment type");
        assertThatThrownBy(() -> AttachmentTypePolicy.inspect("program.exe", new byte[] {'M', 'Z'}))
                .hasMessage("Unsupported attachment type");
        assertThatThrownBy(() -> AttachmentTypePolicy.inspect("voice.mp3", new byte[] {'I', 'D', '3'}))
                .hasMessage("Unsupported attachment type");
        assertThatThrownBy(() -> AttachmentTypePolicy.inspect("movie.mp4", new byte[] {0, 0, 0, 1}))
                .hasMessage("Unsupported attachment type");
        assertThatThrownBy(() -> AttachmentTypePolicy.inspect("payload.txt", new byte[] {'a', 0, 'b'}))
                .hasMessageContaining("binary");
    }

    @Test
    void rejectsMacroEnabledOfficeExtensionsBeforeParsing() {
        assertThatThrownBy(() -> AttachmentTypePolicy.inspect("unsafe.docm", new byte[] {'P', 'K', 3, 4}))
                .hasMessage("Unsupported attachment type");
    }
}
