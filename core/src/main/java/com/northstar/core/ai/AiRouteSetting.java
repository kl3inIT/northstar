package com.northstar.core.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_route_setting")
class AiRouteSetting {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "task", nullable = false, length = 32)
    private AiTask task;

    @Column(name = "gateway_id", nullable = false, length = 64)
    private String gatewayId;

    @Column(name = "model_id", nullable = false, length = 255)
    private String modelId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, String> options;

    protected AiRouteSetting() {
        // for JPA
    }

    AiRouteSetting(AiTask task, AiRoute route) {
        this.task = task;
        apply(route);
    }

    void apply(AiRoute route) {
        gatewayId = route.gatewayId();
        modelId = route.modelId();
        options = route.options();
    }

    AiRoute route() {
        return new AiRoute(gatewayId, modelId, options);
    }
}
