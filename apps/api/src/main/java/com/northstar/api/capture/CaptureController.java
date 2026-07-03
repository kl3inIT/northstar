package com.northstar.api.capture;

import com.northstar.core.capture.CaptureService;
import com.northstar.core.capture.NoteDraft;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST delivery for AI capture. Interactive by design: the user pastes text and
 * waits for the draft, so the LLM call runs on this (virtual) request thread —
 * the "no LLM on api threads" rule is for background jobs, not for a response
 * the user is actively waiting on.
 */
@RestController
@RequestMapping("/api/capture")
class CaptureController {

    private final CaptureService capture;

    CaptureController(CaptureService capture) {
        this.capture = capture;
    }

    @PostMapping("/draft")
    NoteDraft draft(@RequestBody CaptureRequest request) {
        if (request.text() == null || request.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text is required");
        }
        return capture.draft(request.text().strip());
    }
}
