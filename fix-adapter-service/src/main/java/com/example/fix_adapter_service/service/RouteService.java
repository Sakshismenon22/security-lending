package com.example.fix_adapter_service.service;

import com.example.fix_adapter_service.config.*;
import org.springframework.stereotype.Service;
import quickfix.SessionID;
import java.util.*;

@Service
public class RouteService {
    private final AdapterProperties properties;
    private final Map<String,RouteProperties> counterparties=new LinkedHashMap<>();
    private final Map<String,RouteProperties> sessions=new LinkedHashMap<>();
    public RouteService(AdapterProperties properties) {
        this.properties=properties;
        for(var route:properties.getRoutes()) {
            String session=new SessionID("FIX.4.4",properties.getSenderCompId(),route.getTargetCompId()).toString();
            if(counterparties.putIfAbsent(route.getCounterpartyId(),route)!=null || sessions.putIfAbsent(session,route)!=null)
                throw new IllegalArgumentException("Duplicate counterparty ID or FIX session in adapter.routes");
            if(route.getTargetCompId().equals(properties.getSenderCompId()))throw new IllegalArgumentException("FIX sender and target must differ");
        }
    }
    public RouteProperties counterparty(String id,long organizationId) {
        var route=counterparties.get(id);validate(route,organizationId);return route;
    }
    public RouteProperties session(String key,long organizationId) {
        var route=sessions.get(key);validate(route,organizationId);return route;
    }
    private void validate(RouteProperties route,long organizationId) {
        if(route==null)throw new IllegalArgumentException("No configured counterparty route");
        if(!route.getOrganizationIds().contains(organizationId))throw new IllegalArgumentException("Organization is not permitted on this FIX session");
    }
    public String sessionKey(RouteProperties route) {return new SessionID("FIX.4.4",properties.getSenderCompId(),route.getTargetCompId()).toString();}
    public List<String> sessionKeys() {return List.copyOf(sessions.keySet());}
}
