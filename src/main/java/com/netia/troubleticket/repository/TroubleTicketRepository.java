package com.netia.troubleticket.repository;

import com.netia.troubleticket.domain.TroubleTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TroubleTicketRepository extends JpaRepository<TroubleTicket, Long> {

    @Query("SELECT DISTINCT t FROM TroubleTicket t LEFT JOIN FETCH t.notes n WHERE t.id = :id AND t.tenantId = :tenantId")
    Optional<TroubleTicket> findByIdAndTenantId(@Param("id") String id, @Param("tenantId") String tenantId);

    @Query("SELECT DISTINCT t FROM TroubleTicket t LEFT JOIN FETCH t.notes n WHERE t.externalId = :externalId AND t.tenantId = :tenantId")
    Optional<TroubleTicket> findByExternalIdAndTenantId(@Param("externalId") String externalId, @Param("tenantId") String tenantId);

    @Query("SELECT DISTINCT t FROM TroubleTicket t LEFT JOIN FETCH t.notes n WHERE t.tenantId = :tenantId ORDER BY t.createdAt DESC")
    List<TroubleTicket> findAllByTenantId(@Param("tenantId") String tenantId);
}


