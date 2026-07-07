package com.northstar.core.discipline;

import com.northstar.core.shared.ColorName;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The discipline module's public API — the LDP spine other modules FK to.
 * Referencing modules validate against this API, while delete safety is
 * composed by delivery/tool layers that can inspect those references.
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

    @Transactional(readOnly = true)
    public DisciplineSummary find(UUID id) {
        return summary(disciplines.findById(id).orElseThrow(() -> new DisciplineNotFoundException(id)));
    }

    @Transactional
    public DisciplineSummary update(UUID id, String name, ColorName color) {
        Discipline discipline = disciplines.findById(id)
                .orElseThrow(() -> new DisciplineNotFoundException(id));
        discipline.edit(name.strip(), color);
        return summary(discipline);
    }

    @Transactional
    public void delete(UUID id) {
        if (!disciplines.existsById(id)) {
            throw new DisciplineNotFoundException(id);
        }
        disciplines.deleteById(id);
    }

    /** Existence check for modules that FK to a discipline. */
    @Transactional(readOnly = true)
    public boolean exists(UUID id) {
        return disciplines.existsById(id);
    }

    /**
     * Validates an OPTIONAL discipline reference for FK-ing modules: a null id is
     * allowed (no discipline), a non-existent id is rejected. One home for the
     * check task/project/calendar all need.
     */
    @Transactional(readOnly = true)
    public void requireExists(UUID id) {
        if (id != null && !disciplines.existsById(id)) {
            throw new IllegalArgumentException("No discipline with id " + id);
        }
    }

    private static DisciplineSummary summary(Discipline discipline) {
        return new DisciplineSummary(discipline.getId(), discipline.getName(), discipline.getColor());
    }
}
