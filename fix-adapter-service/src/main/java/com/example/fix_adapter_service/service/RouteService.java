package com.example.fix_adapter_service.service;

import com.example.fix_adapter_service.config.*;
import org.springframework.stereotype.Service;
import quickfix.SessionID;
import java.util.*;

@Service
public class RouteService {
    private final AdapterProperties properties;
    private final LendoraRouteDirectory directory;
    private final Map<String,RouteProperties> counterparties=new LinkedHashMap<>();
    private final Map<String,RouteProperties> sessions=new LinkedHashMap<>();
    public RouteService(AdapterProperties properties) { this(properties, new LendoraRouteDirectory(properties)); }
    @org.springframework.beans.factory.annotation.Autowired
    public RouteService(AdapterProperties properties, LendoraRouteDirectory directory) {
        this.properties=properties; this.directory=directory;
        for(var route:properties.getRoutes()) {
            String session=new SessionID("FIX.4.4",properties.getSenderCompId(),route.getTargetCompId()).toString();
            if(counterparties.putIfAbsent(route.getCounterpartyId(),route)!=null || sessions.putIfAbsent(session,route)!=null)
                throw new IllegalArgumentException("Duplicate counterparty ID or FIX session in adapter.routes");
            if(route.getTargetCompId().equals(properties.getSenderCompId()))throw new IllegalArgumentException("FIX sender and target must differ");
        }
    }
    public RouteProperties counterparty(String id,long organizationId) {
        var route=counterparties.get(id);
        if (route == null && properties.isLendoraRoutingEnabled()) {
            String type = properties.getSide() == com.example.fix_adapter_service.model.AdapterSide.BORROWER ? "LENDER" : "BORROWER";
            var identity = directory.external(id, type).orElseThrow(() -> new IllegalArgumentException("No registered counterparty identity for " + id));
            route = counterparties.get(identity.institutionId());
        }
        validate(route,organizationId);return route;
    }
    public RouteProperties session(String key,long organizationId) {
        var route=sessions.get(key);validate(route,organizationId);return route;
    }
    private void validate(RouteProperties route,long organizationId) {
        if(route==null)throw new IllegalArgumentException("No configured counterparty route");
        if(!route.getOrganizationIds().contains(organizationId)) {
            String borrowerInstitution = properties.getSide() == com.example.fix_adapter_service.model.AdapterSide.BORROWER ? properties.getInstitutionId() : route.getCounterpartyId();
            if (!properties.isLendoraRoutingEnabled() || !directory.borrower(organizationId, borrowerInstitution))
                throw new IllegalArgumentException("Organization is not permitted on this FIX session");
        }
    }
    public boolean acceptsCommandTarget(String externalId) {
        return properties.getInstitutionId().equals(externalId) || properties.isLendoraRoutingEnabled()
            && directory.external(externalId, "LENDER").map(e -> e.institutionId().equals(properties.getInstitutionId())).orElse(false);
    }
    public String sessionKey(RouteProperties route) {return new SessionID("FIX.4.4",properties.getSenderCompId(),route.getTargetCompId()).toString();}
    public List<String> sessionKeys() {return List.copyOf(sessions.keySet());}
}
