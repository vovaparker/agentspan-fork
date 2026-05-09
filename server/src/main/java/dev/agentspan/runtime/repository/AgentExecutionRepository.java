package dev.agentspan.runtime.repository;

import dev.agentspan.runtime.model.AgentExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for {@link AgentExecution} entities.
 */
@Repository
public interface AgentExecutionRepository extends JpaRepository<AgentExecution, String> {

    /**
     * Delete all records with createdAt strictly before the given cutoff.
     *
     * @param cutoff the cutoff instant; records older than this are removed
     * @return number of records deleted
     */
    @Modifying
    @Query("DELETE FROM AgentExecution e WHERE e.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);

    /**
     * Count how many execution records exist for the given agent.
     *
     * @param agentId the agent identifier
     * @return total count
     */
    long countByAgentId(String agentId);

    /**
     * Delete all execution records for the given agent.
     *
     * @param agentId the agent identifier
     * @return number of records deleted
     */
    @Modifying
    @Query("DELETE FROM AgentExecution e WHERE e.agentId = :agentId")
    int deleteByAgentId(@Param("agentId") String agentId);

    /**
     * Find the {@code limit} oldest records for a given agent (ascending createdAt).
     *
     * @param agentId the agent identifier
     * @param limit   maximum number of records to return
     * @return list of oldest AgentExecution records
     */
    @Query("SELECT e FROM AgentExecution e WHERE e.agentId = :agentId ORDER BY e.createdAt ASC")
    List<AgentExecution> findOldestByAgentId(
            @Param("agentId") String agentId,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Convenience method: find the {@code limit} oldest records for a given agent.
     */
    default List<AgentExecution> findOldestByAgentId(String agentId, int limit) {
        return findOldestByAgentId(agentId,
                org.springframework.data.domain.PageRequest.of(0, limit));
    }
}
