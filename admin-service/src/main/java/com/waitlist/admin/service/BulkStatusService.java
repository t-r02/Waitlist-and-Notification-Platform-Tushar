package com.waitlist.admin.service;

import com.waitlist.admin.dto.response.BulkStatusResponse;
import com.waitlist.admin.entity.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BulkStatusService {
    private final EntryManagementService entryService;
    
    public BulkStatusResponse bulkUpdate(Iterable<Long> ids, Status newStatus, String admin) {
        var response = new BulkStatusResponse();
        
        for (Long id : ids) {
            try {
                entryService.updateStatus(id, newStatus, admin);
                response.setSuccessCount(response.getSuccessCount() + 1);
            } catch (Exception e) {
                response.addFailure(id, e.getMessage());
            }
        }
        
        return response;
    }
}
