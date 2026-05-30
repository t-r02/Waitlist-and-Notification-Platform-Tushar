package com.waitlist.admin.repository.ingestion;

import com.waitlist.admin.entity.ingestion.ReferralAdminView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReferralAdminRepository extends JpaRepository<ReferralAdminView, Long> {

    @Query("SELECT COUNT(r) FROM ReferralAdminView r WHERE r.referrerEmail = :email")
    long countByReferrerEmail(@Param("email") String email);
}
