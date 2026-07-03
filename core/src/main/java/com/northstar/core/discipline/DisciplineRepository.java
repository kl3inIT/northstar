package com.northstar.core.discipline;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link Discipline}. Other modules go through {@link DisciplineService}. */
interface DisciplineRepository extends JpaRepository<Discipline, UUID> {

    List<Discipline> findAllByOrderByNameAsc();
}
