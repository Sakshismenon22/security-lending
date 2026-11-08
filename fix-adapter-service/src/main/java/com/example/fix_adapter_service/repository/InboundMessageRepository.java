package com.example.fix_adapter_service.repository;

import com.example.fix_adapter_service.model.InboundMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface InboundMessageRepository extends JpaRepository<InboundMessage,Long> {
    Optional<InboundMessage> findByEventId(String eventId);
}
