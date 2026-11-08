package com.example.fix_adapter_service.service;

import com.example.fix_adapter_service.config.AdapterProperties;
import com.example.fix_adapter_service.model.AdapterIdentity;
import com.example.fix_adapter_service.repository.AdapterIdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;

@Service
@RequiredArgsConstructor
public class AdapterIdentityService implements InitializingBean {
    private final AdapterIdentityRepository identities;
    private final AdapterProperties properties;
    private final TransactionTemplate transactions;
    private final Clock clock;
    @Override public void afterPropertiesSet() {
        transactions.executeWithoutResult(status->{
            var existing=identities.lockIdentity();
            if(existing.isPresent()) {
                var identity=existing.get();
                if(identity.getSide()!=properties.getSide() || !identity.getSenderCompId().equals(properties.getSenderCompId()) || !identity.getInstitutionId().equals(properties.getInstitutionId()))
                    throw new IllegalStateException("Adapter database belongs to another side/institution/FIX identity; use separate borrower and lender databases");
            } else {
                var identity=new AdapterIdentity();identity.setId(1);identity.setSide(properties.getSide());
                identity.setInstitutionId(properties.getInstitutionId());identity.setSenderCompId(properties.getSenderCompId());identity.setCreatedAt(clock.instant());
                identities.saveAndFlush(identity);
            }
        });
    }
}
