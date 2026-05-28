package com.waitlist.admin.service;

import com.waitlist.admin.entity.Status;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Set;

@Component
public class StateMachineGuard {
    private static final Map<Status, Set<Status>> TRANSITIONS = Map.of(
        Status.PENDING, Set.of(Status.APPROVED, Status.REJECTED),
        Status.APPROVED, Set.of(Status.INVITED, Status.REJECTED),
        Status.REJECTED, Set.of(Status.PENDING),
        Status.INVITED, Set.of()
    );
    
    public boolean isValidTransition(Status from, Status to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }
}
