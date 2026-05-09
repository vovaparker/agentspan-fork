package dev.agentspan.runtime.service;

import dev.agentspan.runtime.model.AgentExecution;
import dev.agentspan.runtime.repository.AgentExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExecutionHistoryServiceTest {

    @Mock
    private AgentExecutionRepository agentExecutionRepository;

    @InjectMocks
    private ExecutionHistoryService service;

    @BeforeEach
    void setUp() {
        service.setRetentionDays(30);
        service.setMaxRecordsPerAgent(1000);
    }

    // --- pruneByAge ---

    @Test
    void pruneByAge_deletesOldRecords() {
        when(agentExecutionRepository.deleteByCreatedAtBefore(any(Instant.class))).thenReturn(5);

        int result = service.pruneByAge(30);

        assertThat(result).isEqualTo(5);
        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(agentExecutionRepository).deleteByCreatedAtBefore(captor.capture());
        // cutoff should be approximately 30 days ago
        Instant cutoff = captor.getValue();
        assertThat(cutoff).isBefore(Instant.now());
        assertThat(cutoff).isAfter(Instant.now().minusSeconds(30L * 86400 + 60));
    }

    @Test
    void pruneByAge_throwsForNonPositiveDays() {
        assertThatThrownBy(() -> service.pruneByAge(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.pruneByAge(-5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pruneByAge_returnsZeroWhenNothingDeleted() {
        when(agentExecutionRepository.deleteByCreatedAtBefore(any())).thenReturn(0);
        assertThat(service.pruneByAge(7)).isEqualTo(0);
    }

    // --- pruneByAgentKeepLatest ---

    @Test
    void pruneByAgentKeepLatest_deletesExcessRecords() {
        AgentExecution old1 = new AgentExecution("agent1", "SUCCESS");
        AgentExecution old2 = new AgentExecution("agent1", "SUCCESS");
        when(agentExecutionRepository.countByAgentId("agent1")).thenReturn(5L);
        when(agentExecutionRepository.findOldestByAgentId("agent1", 3))
                .thenReturn(Arrays.asList(old1, old2, old1)); // return 3 items

        int deleted = service.pruneByAgentKeepLatest("agent1", 2);

        assertThat(deleted).isEqualTo(3);
        verify(agentExecutionRepository).deleteAll(anyList());
    }

    @Test
    void pruneByAgentKeepLatest_noOpWhenEnoughRecordsToKeep() {
        when(agentExecutionRepository.countByAgentId("agent1")).thenReturn(3L);

        int deleted = service.pruneByAgentKeepLatest("agent1", 5);

        assertThat(deleted).isEqualTo(0);
        verify(agentExecutionRepository, never()).deleteAll(anyList());
    }

    @Test
    void pruneByAgentKeepLatest_throwsForBlankAgentId() {
        assertThatThrownBy(() -> service.pruneByAgentKeepLatest("", 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.pruneByAgentKeepLatest(null, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pruneByAgentKeepLatest_throwsForNegativeKeepCount() {
        assertThatThrownBy(() -> service.pruneByAgentKeepLatest("agent1", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pruneByAgentKeepLatest_keepZeroDeletesAll() {
        AgentExecution e = new AgentExecution("agent1", "SUCCESS");
        when(agentExecutionRepository.countByAgentId("agent1")).thenReturn(2L);
        when(agentExecutionRepository.findOldestByAgentId("agent1", 2))
                .thenReturn(Arrays.asList(e, e));

        int deleted = service.pruneByAgentKeepLatest("agent1", 0);

        assertThat(deleted).isEqualTo(2);
    }

    // --- clearHistoryForAgent ---

    @Test
    void clearHistoryForAgent_deletesAll() {
        when(agentExecutionRepository.deleteByAgentId("agentX")).thenReturn(42);

        int deleted = service.clearHistoryForAgent("agentX");

        assertThat(deleted).isEqualTo(42);
        verify(agentExecutionRepository).deleteByAgentId("agentX");
    }

    @Test
    void clearHistoryForAgent_throwsForBlankAgentId() {
        assertThatThrownBy(() -> service.clearHistoryForAgent(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.clearHistoryForAgent(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- scheduledPrune ---

    @Test
    void scheduledPrune_callsPruneByAge() {
        service.setRetentionDays(14);
        when(agentExecutionRepository.deleteByCreatedAtBefore(any())).thenReturn(3);

        service.scheduledPrune();

        verify(agentExecutionRepository).deleteByCreatedAtBefore(any(Instant.class));
    }
}
