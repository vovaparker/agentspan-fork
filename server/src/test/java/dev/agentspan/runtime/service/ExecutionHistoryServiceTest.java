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
import java.util.Optional;

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

    // --- deleteExecution ---

    @Test
    void testDeleteExecution_removesRecord() {
        AgentExecution exec = new AgentExecution("agent1", "SUCCESS");
        exec.setId("exec-1");
        when(agentExecutionRepository.findById("exec-1")).thenReturn(Optional.of(exec));
        when(agentExecutionRepository.findByParentExecutionId("exec-1")).thenReturn(Collections.emptyList());

        service.deleteExecution("exec-1");

        verify(agentExecutionRepository).deleteById("exec-1");
    }

    @Test
    void testDeleteExecution_cascadesChildren() {
        AgentExecution parent = new AgentExecution("agent1", "SUCCESS");
        parent.setId("parent-1");

        AgentExecution child1 = new AgentExecution("agent1", "SUCCESS");
        child1.setId("child-1");
        child1.setParentExecutionId("parent-1");

        AgentExecution child2 = new AgentExecution("agent1", "SUCCESS");
        child2.setId("child-2");
        child2.setParentExecutionId("parent-1");

        when(agentExecutionRepository.findById("parent-1")).thenReturn(Optional.of(parent));
        when(agentExecutionRepository.findById("child-1")).thenReturn(Optional.of(child1));
        when(agentExecutionRepository.findById("child-2")).thenReturn(Optional.of(child2));
        when(agentExecutionRepository.findByParentExecutionId("parent-1")).thenReturn(Arrays.asList(child1, child2));
        when(agentExecutionRepository.findByParentExecutionId("child-1")).thenReturn(Collections.emptyList());
        when(agentExecutionRepository.findByParentExecutionId("child-2")).thenReturn(Collections.emptyList());

        service.deleteExecution("parent-1");

        verify(agentExecutionRepository).deleteById("parent-1");
        verify(agentExecutionRepository).deleteById("child-1");
        verify(agentExecutionRepository).deleteById("child-2");
    }

    @Test
    void testDeleteExecution_childDeletesCascadesToParent() {
        AgentExecution parent = new AgentExecution("agent1", "SUCCESS");
        parent.setId("parent-1");

        AgentExecution child = new AgentExecution("agent1", "SUCCESS");
        child.setId("child-1");
        child.setParentExecutionId("parent-1");

        when(agentExecutionRepository.findById("child-1")).thenReturn(Optional.of(child));
        when(agentExecutionRepository.findById("parent-1")).thenReturn(Optional.of(parent));
        when(agentExecutionRepository.findByParentExecutionId("child-1")).thenReturn(Collections.emptyList());
        when(agentExecutionRepository.findByParentExecutionId("parent-1")).thenReturn(Arrays.asList(child));

        service.deleteExecution("child-1");

        verify(agentExecutionRepository).deleteById("child-1");
        verify(agentExecutionRepository).deleteById("parent-1");
    }

    @Test
    void testDeleteExecution_nonExistentId_noException() {
        when(agentExecutionRepository.findById("missing")).thenReturn(Optional.empty());

        service.deleteExecution("missing");

        verify(agentExecutionRepository, never()).deleteById(any());
    }

    @Test
    void testBulkDeleteExecutions_deletesAll() {
        AgentExecution e1 = new AgentExecution("agent1", "SUCCESS");
        e1.setId("e1");
        AgentExecution e2 = new AgentExecution("agent1", "SUCCESS");
        e2.setId("e2");
        AgentExecution e3 = new AgentExecution("agent1", "SUCCESS");
        e3.setId("e3");

        when(agentExecutionRepository.findById("e1")).thenReturn(Optional.of(e1));
        when(agentExecutionRepository.findById("e2")).thenReturn(Optional.of(e2));
        when(agentExecutionRepository.findById("e3")).thenReturn(Optional.of(e3));
        when(agentExecutionRepository.findByParentExecutionId("e1")).thenReturn(Collections.emptyList());
        when(agentExecutionRepository.findByParentExecutionId("e2")).thenReturn(Collections.emptyList());
        when(agentExecutionRepository.findByParentExecutionId("e3")).thenReturn(Collections.emptyList());

        int result = service.bulkDeleteExecutions(Arrays.asList("e1", "e2", "e3"));

        assertThat(result).isEqualTo(3);
        verify(agentExecutionRepository).deleteById("e1");
        verify(agentExecutionRepository).deleteById("e2");
        verify(agentExecutionRepository).deleteById("e3");
    }
}

