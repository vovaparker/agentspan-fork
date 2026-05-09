package dev.agentspan.runtime.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.agentspan.runtime.service.AgentDagService;
import dev.agentspan.runtime.service.AgentService;
import dev.agentspan.runtime.service.ExecutionHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentController.class)
class AgentControllerDeleteTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AgentService agentService;

    @MockBean
    private AgentDagService agentDagService;

    @MockBean
    private ExecutionHistoryService executionHistoryService;

    @Test
    void testDeleteExecution_returns204() throws Exception {
        doNothing().when(executionHistoryService).deleteExecution(eq("exec-1"));

        mockMvc.perform(delete("/api/agent/executions/exec-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testBulkDelete_returnsCount() throws Exception {
        when(executionHistoryService.bulkDeleteExecutions(anyList())).thenReturn(2);

        Map<String, Object> requestBody = Map.of("ids", Arrays.asList("a", "b"));
        String json = objectMapper.writeValueAsString(requestBody);

        mockMvc.perform(delete("/api/agent/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(2));
    }

    @Test
    void testBulkDelete_emptyList_returns200() throws Exception {
        when(executionHistoryService.bulkDeleteExecutions(anyList())).thenReturn(0);

        Map<String, Object> requestBody = Map.of("ids", Collections.emptyList());
        String json = objectMapper.writeValueAsString(requestBody);

        mockMvc.perform(delete("/api/agent/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(0));
    }
}
