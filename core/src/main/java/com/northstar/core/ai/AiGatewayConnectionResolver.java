package com.northstar.core.ai;

/** Resolves deployment and encrypted runtime gateway credentials for server integrations. */
public interface AiGatewayConnectionResolver {

    AiGatewayConnection require(String gatewayId);
}
