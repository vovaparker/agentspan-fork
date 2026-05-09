package dev.agentspan.runtime.service;

import dev.agentspan.runtime.model.AgentExecution;
import dev.agentspan.runtime.repository.AgentExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service responsible for managing execution history retention.
 * Provides scheduled pruning of old records and on-demand pruning APIs.
 */
@Component
public class ExecutionHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionHistoryService.class);

    @Autowired
    private AgentExecutionRepository agentExecutionRepository;

    /**
     * Maximum number of days to retain execution history records.
     * Defaults to 30 days. Can be overridden via application properties.
     */
    @Value("null")
    private int retentionDays;

    /**
     * Maximum number of execution records to retain per agent.
     * Defaults to 1000. Set to -1 to disable count-based pruning.
     */
    @Value("null")
    private int maxRecordsPerAgent;

    /**
     * Scheduled job that prunes execution history older than the configured retention period.
     * Runs daily at midnight by default.
     */
    @Scheduled(cron = "null")
    @Transactional
    public void scheduledPrune() {
        log.info("Starting scheduled execution history pruning (retentionDays={})", retentionDays);
        int deleted = pruneByAge(retentionDays);
        log.info("Scheduled pruning completed: {} records deleted", deleted);
    }

    /**
     * Prune execution history records older than the specified number of days.
     *
     * @param days number of days; records older than this are deleted
     * @return number of records deleted
     */
    @Transactional
    public int pruneByAge(int days) {
        if (days <= 0) {
            throw new IllegalArgumentException("days must be a positive integer, got: " + days);
        }
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        log.debug("Pruning execution history older than {} (cutoff={})", days, cutoff);
        int count = agentExecutionRepository.deleteByCreatedAtBefore(cutoff);
        log.info("Pruned {} execution history record(s) older than {} days", count, days);
        return count;
    }

    /**
     * Prune execution history for a specific agent, keeping only the most recent
     * {@code keepCount} records.
     *
     * @param agentId  the agent whose history should be pruned
     * @param keepCount number of most-recent records to retain
     * @return number of records deleted
     */
    @Transactional
    public int pruneByAgentKeepLatest(String agentId, int keepCount) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        if (keepCount < 0) {
            throw new IllegalArgumentException("keepCount must be >= 0, got: " + keepCount);
        }
        long total = agentExecutionRepository.countByAgentId(agentId);
        long toDelete = total - keepCount;
        if (toDelete <= 0) {
            log.debug("No pruning needed for agent {} (total={}, keepCount={})", agentId, total, keepCount);
            return 0;
        }
        List<AgentExecution> oldest =
                agentExecutionRepository.findOldestByAgentId(agentId, (int) toDelete);
        agentExecutionRepository.deleteAll(oldest);
        log.info("Pruned {} execution history record(s) for agent {}", oldest.size(), agentId);
        return oldest.size();
    }

    /**
     * Delete ALL execution history records for a specific agent.
     *
     * @param agentId the agent whose history should be cleared
     * @return number of records deleted
     */
    @Transactional
    public int clearHistoryForAgent(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        int count = agentExecutionRepository.deleteByAgentId(agentId);
        log.info("Cleared {} execution history record(s) for agent {}", count, agentId);
        return count;
    }

    // --- accessors for testing ---

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public int getMaxRecordsPerAgent() {
        return maxRecordsPerAgent;
    }

    public void setMaxRecordsPerAgent(int maxRecordsPerAgent) {
        this.maxRecordsPerAgent = maxRecordsPerAgent;
    }
}
