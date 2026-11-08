package com.example.fix_adapter_service.repository;

import com.example.fix_adapter_service.model.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface OutboundMessageRepository extends JpaRepository<OutboundMessage,Long> {
    Optional<OutboundMessage> findByEventId(String eventId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from OutboundMessage m where m.id=:id")
    Optional<OutboundMessage> lockById(long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from OutboundMessage m where m.eventId=:eventId")
    Optional<OutboundMessage> lockByEventId(String eventId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from OutboundMessage m where m.sessionKey=:sessionKey and m.state<>com.example.fix_adapter_service.model.DeliveryState.ACKNOWLEDGED order by m.id")
    List<OutboundMessage> lockHead(String sessionKey,Pageable pageable);
    @Query("select distinct m.sessionKey from OutboundMessage m where m.state<>com.example.fix_adapter_service.model.DeliveryState.ACKNOWLEDGED")
    List<String> pendingSessions();
    long countByState(DeliveryState state);
}
