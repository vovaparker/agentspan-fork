package dev.agentspan.runtime.controller;

import dev.agentspan.runtime.service.ExecutionHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExecutionHistoryController.class)
class ExecutionHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExecutionHistoryService executionHistoryService;

    @Test
    void pruneByAge_defaultDays() throws Exception {
        when(executionHistoryService.pruneByAge(30)).thenReturn(10);

        mockMvc.perform(delete("/api/execution-history/prune"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(10))
                .andExpect(jsonPath("$.days").value(30));
    }

    @Test
    void pruneByAge_customDays() throws Exception {
        when(executionHistoryService.pruneByAge(7)).thenReturn(3);

        mockMvc.perform(delete("/api/execution-history/prune").param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(3))
                .andExpect(jsonPath("$.days").value(7));
    }

    @Test
    void clearAgentHistory_returnsDeletedCount() throws Exception {
        when(executionHistoryService.clearHistoryForAgent("myAgent")).thenReturn(5);

        mockMvc.perform(delete("/api/execution-history/agent/myAgent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(5))
                .andExpect(jsonPath("$.agentId").value("myAgent"));
    }

    @Test
    void pruneAgentHistory_defaultKeep() throws Exception {
        when(executionHistoryService.pruneByAgentKeepLatest("myAgent", 100)).thenReturn(2);

        mockMvc.perform(delete("/api/execution-history/agent/myAgent/prune"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(2))
                .andExpect(jsonPath("$.agentId").value("myAgent"))
                .andExpect(jsonPath("$.kept").value(100));
    }

    @Test
    void pruneAgentHistory_customKeep() throws Exception {
        when(executionHistoryService.pruneByAgentKeepLatest("myAgent", 50)).thenReturn(7);

        mockMvc.perform(delete("/api/execution-history/agent/myAgent/prune").param("keep", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(7))
                .andExpect(jsonPath("$.kept").value(50));
    }
}
