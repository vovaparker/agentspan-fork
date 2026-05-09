package dev.agentspan.runtime.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Represents a single agent execution event stored in the database.
 */
@Entity
@Table(name = "agent_execution",
        indexes = {
                @Index(name = "idx_agent_execution_agent_id", columnList = "agent_id"),
                @Index(name = "idx_agent_execution_created_at", columnList = "created_at")
        })
public class AgentExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "agent_id", nullable = false)
    private String agentId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "status")
    private String status;

    @Column(name = "output", columnDefinition = "TEXT")
    private String output;

    public AgentExecution() {
        this.createdAt = Instant.now();
    }

    public AgentExecution(String agentId, String status) {
        this.agentId = agentId;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }
}
