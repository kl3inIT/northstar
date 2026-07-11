package com.northstar.core.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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
    }

    AiRoute route() {
        return new AiRoute(gatewayId, modelId);
    }
}
