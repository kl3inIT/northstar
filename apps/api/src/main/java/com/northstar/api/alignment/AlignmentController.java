package com.northstar.api.alignment;

import com.northstar.core.alignment.AlignmentService;
import com.northstar.core.note.NoteDetail;
import java.time.ZoneId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST delivery for machine-drafted Alignment reviews. GET answers "is there a
 * review for today / this week already?" (404 = not yet); POST (re)generates it —
 * interactive like capture, so the LLM call runs on this request thread. "Today"
 * and "this week" are zone-local via the same X-Timezone header the task and
 * calendar endpoints use.
 */
@RestController
@RequestMapping("/api/alignment")
class AlignmentController {

    private final AlignmentService alignment;

    AlignmentController(AlignmentService alignment) {
        this.alignment = alignment;
    }

    @GetMapping("/daily")
    ResponseEntity<NoteDetail> daily(@RequestHeader(name = "X-Timezone", required = false) String tz) {
        return ResponseEntity.of(alignment.findDaily(zone(tz)));
    }

    @PostMapping("/daily")
    NoteDetail generateDaily(@RequestHeader(name = "X-Timezone", required = false) String tz) {
        return alignment.generateDaily(zone(tz));
    }

    @GetMapping("/weekly")
    ResponseEntity<NoteDetail> weekly(@RequestHeader(name = "X-Timezone", required = false) String tz) {
        return ResponseEntity.of(alignment.findWeekly(zone(tz)));
    }

    @PostMapping("/weekly")
    NoteDetail generateWeekly(@RequestHeader(name = "X-Timezone", required = false) String tz) {
        return alignment.generateWeekly(zone(tz));
    }

    private static ZoneId zone(String tz) {
        try {
            return tz == null ? ZoneId.systemDefault() : ZoneId.of(tz);
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }
}
