package com.northstar.api.alignment;

import com.northstar.core.alignment.AlignmentService;
import com.northstar.core.calendar.CalendarEventService;
import com.northstar.core.finance.FinanceService;
import com.northstar.core.note.NoteService;
import com.northstar.core.task.TaskService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the alignment module for this app, reusing the single ChatClient bean
 * CaptureConfig defines; core's AlignmentService is deliberately not a component
 * so apps without an LLM (mcp, worker) never try to build it.
 */
@Configuration(proxyBeanMethods = false)
class AlignmentConfig {

    @Bean
    AlignmentService alignmentService(ChatClient chatClient, TaskService tasks,
            CalendarEventService events, NoteService notes, FinanceService finance) {
        return new AlignmentService(chatClient, tasks, events, notes, finance);
    }
}
