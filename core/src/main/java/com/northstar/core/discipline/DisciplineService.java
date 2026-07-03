package com.northstar.core.discipline;

import com.northstar.core.shared.ColorName;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The discipline module's public API — the LDP spine other modules FK to.
 * Deliberately minimal for now: list for pickers, create for setup, and an
 * existence check so referencing modules can validate a discipline id.
 */
@Service
public class DisciplineService {

    private final DisciplineRepository disciplines;

    DisciplineService(DisciplineRepository disciplines) {
        this.disciplines = disciplines;
    }

    @Transactional(readOnly = true)
    public List<DisciplineSummary> list() {
        return disciplines.findAllByOrderByNameAsc().stream().map(DisciplineService::summary).toList();
    }

    @Transactional
    public DisciplineSummary create(String name, ColorName color) {
        Discipline discipline = new Discipline(UUID.randomUUID(), name.strip(), color);
        disciplines.save(discipline);
        return summary(discipline);
    }

    /** Existence check for modules that FK to a discipline. */
    @Transactional(readOnly = true)
    public boolean exists(UUID id) {
        return disciplines.existsById(id);
    }

    private static DisciplineSummary summary(Discipline discipline) {
        return new DisciplineSummary(discipline.getId(), discipline.getName(), discipline.getColor());
    }
}
