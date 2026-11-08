package com.example.fix_adapter_service.service;

import com.example.fix_adapter_service.config.AdapterProperties;
import com.example.fix_adapter_service.dto.SessionStatus;
import com.example.fix_adapter_service.model.AdapterSide;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;
import quickfix.*;
import java.nio.file.*;
import java.util.*;

@Service
public class FixEngineService implements FixTransport,SmartLifecycle {
    private final AdapterProperties properties;private final RouteService routes;private final FixApplicationService application;
    private Connector connector;private volatile boolean running;
    public FixEngineService(AdapterProperties properties,RouteService routes,FixApplicationService application,AdapterIdentityService identity) {this.properties=properties;this.routes=routes;this.application=application;}
    @Override public void start() {
        if(!properties.isFixEnabled())return;
        try {
            Files.createDirectories(Path.of(properties.getStoreDirectory()));
            var settings=settings();var store=new FileStoreFactory(settings);var logs=new SLF4JLogFactory(settings);var messages=new DefaultMessageFactory();
            connector=properties.getSide()==AdapterSide.BORROWER?new SocketInitiator(application,store,settings,logs,messages):new SocketAcceptor(application,store,settings,logs,messages);
            connector.start();running=true;
        } catch(Exception e) {if(connector!=null)connector.stop(true);throw new IllegalStateException("Cannot start configured FIX "+properties.getSide()+" adapter",e);}
    }
    public SessionSettings settings() {
        var settings=new SessionSettings();
        settings.setString("ConnectionType",properties.getSide()==AdapterSide.BORROWER?"initiator":"acceptor");
        settings.setString("StartTime","00:00:00");settings.setString("EndTime","00:00:00");settings.setString("TimeZone","UTC");
        settings.setString("NonStopSession","Y");settings.setString("UseDataDictionary","Y");settings.setString("DataDictionary","FIX44-SecuritiesLending.xml");
        settings.setString("ValidateUserDefinedFields","Y");settings.setString("AllowUnknownMsgFields","N");settings.setString("ValidateIncomingMessage","Y");
        settings.setString("ValidateSequenceNumbers","Y");settings.setString("PersistMessages","Y");
        settings.setString("ResetOnLogon","N");settings.setString("ResetOnLogout","N");settings.setString("ResetOnDisconnect","N");
        settings.setString("FileStorePath",properties.getStoreDirectory());settings.setString("FileStoreSync","Y");
        settings.setLong("HeartBtInt",properties.getHeartbeatSeconds());settings.setLong("ReconnectInterval",properties.getReconnectSeconds());
        settings.setString("SocketAcceptAddress",properties.getListenAddress());settings.setLong("SocketAcceptPort",properties.getListenPort());
        settings.setString("SLF4JLogHeartbeats","N");settings.setString("SLF4JLogIncomingMessages","N");settings.setString("SLF4JLogOutgoingMessages","N");
        for(var route:properties.getRoutes()) {
            var id=new SessionID("FIX.4.4",properties.getSenderCompId(),route.getTargetCompId());
            settings.setString(id,"BeginString","FIX.4.4");settings.setString(id,"SenderCompID",properties.getSenderCompId());settings.setString(id,"TargetCompID",route.getTargetCompId());
            if(properties.getSide()==AdapterSide.BORROWER) {settings.setString(id,"SocketConnectHost",route.getHost());settings.setLong(id,"SocketConnectPort",route.getPort());}
        }
        return settings;
    }
    @Override public boolean loggedOn(String key) {var session=Session.lookupSession(new SessionID(key));return running && session!=null && session.isLoggedOn();}
    @Override public boolean send(String key,Message message) throws SessionNotFound {return loggedOn(key) && Session.sendToTarget(message,new SessionID(key));}
    public List<SessionStatus> sessions() {
        var result=new ArrayList<SessionStatus>();
        for(var route:properties.getRoutes()) {
            String key=routes.sessionKey(route);var session=Session.lookupSession(new SessionID(key));
            result.add(new SessionStatus(key,route.getCounterpartyId(),properties.isFixEnabled(),session!=null && session.isLoggedOn(),session==null?null:session.getExpectedSenderNum(),session==null?null:session.getExpectedTargetNum()));
        }
        return result;
    }
    @Override public void stop() {if(connector!=null)connector.stop();running=false;}
    @Override public boolean isRunning(){return running;}
    @Override public boolean isAutoStartup(){return true;}
    @Override public int getPhase(){return 0;}
}
