package com.northstar.core.discipline;

import com.northstar.core.shared.BaseEntity;
import com.northstar.core.shared.ColorName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * A discipline — the LDP spine's middle layer (Life → Disciplines → Projects).
 * Other modules FK to it; on the calendar it plays the role Google Calendar's
 * "calendars" play: classification + color. The V1 columns not yet needed
 * (life_goal_id, ikigai, weekly_budget_minutes) stay unmapped until the full
 * discipline feature lands.
 */
@Entity
@Table(name = "discipline")
public class Discipline extends BaseEntity {

    @NotBlank
    @Column(nullable = false)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ColorName color = ColorName.BLUE;

    protected Discipline() {
        // for JPA
    }

    public Discipline(UUID id, String name, ColorName color) {
        super(id);
        this.name = name;
        this.color = color;
    }

    public String getName() {
        return name;
    }

    public ColorName getColor() {
        return color;
    }

    public void edit(String name, ColorName color) {
        this.name = name;
        this.color = color;
    }
}
