package com.northstar.api.discipline;

import com.northstar.core.discipline.DisciplineService;
import com.northstar.core.discipline.DisciplineSummary;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST delivery for the discipline module — currently just what the calendar's
 * discipline picker needs: list and create.
 */
@RestController
@RequestMapping("/api/disciplines")
class DisciplineController {

    private final DisciplineService disciplines;

    DisciplineController(DisciplineService disciplines) {
        this.disciplines = disciplines;
    }

    @GetMapping
    List<DisciplineSummary> list() {
        return disciplines.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DisciplineSummary create(@Valid @RequestBody DisciplineRequest request) {
        return disciplines.create(request.name(), request.color());
    }
}
