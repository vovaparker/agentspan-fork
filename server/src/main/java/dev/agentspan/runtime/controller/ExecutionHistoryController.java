package dev.agentspan.runtime.controller;

import dev.agentspan.runtime.service.ExecutionHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller exposing execution history management endpoints.
 */
@RestController
@RequestMapping("/api/execution-history")
public class ExecutionHistoryController {

    private static final Logger log = LoggerFactory.getLogger(ExecutionHistoryController.class);

    @Autowired
    private ExecutionHistoryService executionHistoryService;

    /**
     * DELETE /api/execution-history/prune?days={days}
     * <p>
     * Prune execution history records older than {@code days} days.
     *
     * @param days number of days to retain (records older than this are removed)
     * @return JSON body with "deleted" count
     */
    @DeleteMapping("/prune")
    public ResponseEntity<Map<String, Object>> pruneByAge(
            @RequestParam(defaultValue = "30") int days) {
        log.info("REST: pruneByAge days={}", days);
        int deleted = executionHistoryService.pruneByAge(days);
        return ResponseEntity.ok(Map.of("deleted", deleted, "days", days));
    }

    /**
     * DELETE /api/execution-history/agent/{agentId}
     * <p>
     * Delete all execution history for the given agent.
     *
     * @param agentId the agent identifier
     * @return JSON body with "deleted" count
     */
    @DeleteMapping("/agent/{agentId}")
    public ResponseEntity<Map<String, Object>> clearAgentHistory(
            @PathVariable String agentId) {
        log.info("REST: clearAgentHistory agentId={}", agentId);
        int deleted = executionHistoryService.clearHistoryForAgent(agentId);
        return ResponseEntity.ok(Map.of("deleted", deleted, "agentId", agentId));
    }

    /**
     * DELETE /api/execution-history/agent/{agentId}/prune?keep={keep}
     * <p>
     * Prune execution history for a specific agent, retaining the most-recent {@code keep} records.
     *
     * @param agentId the agent identifier
     * @param keep    number of records to keep (most recent)
     * @return JSON body with "deleted" count
     */
    @DeleteMapping("/agent/{agentId}/prune")
    public ResponseEntity<Map<String, Object>> pruneAgentHistory(
            @PathVariable String agentId,
            @RequestParam(defaultValue = "100") int keep) {
        log.info("REST: pruneAgentHistory agentId={} keep={}", agentId, keep);
        int deleted = executionHistoryService.pruneByAgentKeepLatest(agentId, keep);
        return ResponseEntity.ok(Map.of("deleted", deleted, "agentId", agentId, "kept", keep));
    }
}
