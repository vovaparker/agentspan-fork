package dev.agentspan.runtime.model;

/**
 * Response body for bulk-delete execution endpoint.
 */
public class BulkDeleteResponse {

    private int deleted;

    public BulkDeleteResponse() {
    }

    public BulkDeleteResponse(int deleted) {
        this.deleted = deleted;
    }

    public int getDeleted() {
        return deleted;
    }

    public void setDeleted(int deleted) {
        this.deleted = deleted;
    }
}
