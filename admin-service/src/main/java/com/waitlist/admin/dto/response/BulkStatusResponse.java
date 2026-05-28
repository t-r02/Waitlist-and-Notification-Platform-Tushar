package com.waitlist.admin.dto.response;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class BulkStatusResponse {
    private int successCount;
    private List<String> failures = new ArrayList<>();

    public void addFailure(Long id, String reason) {
        failures.add("ID " + id + ": " + reason);
    }
}
