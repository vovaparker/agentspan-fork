package dev.agentspan.runtime.model;

import java.util.List;

/**
 * Request body for bulk-delete execution endpoint.
 */
public class BulkDeleteRequest {

    private List<String> ids;

    public BulkDeleteRequest() {
    }

    public List<String> getIds() {
        return ids;
    }

    public void setIds(List<String> ids) {
        this.ids = ids;
    }
}
