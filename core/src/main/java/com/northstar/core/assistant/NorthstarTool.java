package com.northstar.core.assistant;

/**
 * Marker for a bean that exposes Northstar tools to an LLM. Each method carries
 * BOTH annotations over the same body and description constants:
 * {@code @Tool} for the in-app assistant (ChatClient calls it in-process) and
 * {@code @McpTool} for the mcp app (published to external agents with MCP
 * behavior hints). One definition, two transports — the delivering app gathers
 * every {@code NorthstarTool} bean, so no central tool list is edited per tool.
 */
public interface NorthstarTool {
}
