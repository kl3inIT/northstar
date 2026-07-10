package com.northstar.core.finance;

import com.northstar.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/** A user-confirmed description-to-category mapping used as Capture few-shot context. */
@Entity
@Table(name = "finance_category_correction")
public class CategoryCorrection extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 16)
    private TransactionType type;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String description;

    @NotBlank
    @Column(name = "description_key", nullable = false, length = 255)
    private String descriptionKey;

    @NotBlank
    @Column(nullable = false, length = 64)
    private String category;

    protected CategoryCorrection() {
        // for JPA
    }

    CategoryCorrection(UUID id, TransactionType type, String description,
            String descriptionKey, String category) {
        super(id);
        this.type = type;
        edit(description, category);
        this.descriptionKey = descriptionKey;
    }

    public TransactionType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public String getDescriptionKey() {
        return descriptionKey;
    }

    public String getCategory() {
        return category;
    }

    void edit(String description, String category) {
        this.description = description;
        this.category = category;
    }
}
