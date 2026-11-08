package com.example.fix_adapter_service.config;

import com.example.fix_adapter_service.model.AdapterSide;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter
@Validated
@ConfigurationProperties("adapter")
public class AdapterProperties {
    @NotNull private AdapterSide side=AdapterSide.BORROWER;
    @NotBlank @Size(max=100) private String institutionId="BORROWER_DEMO";
    @NotBlank @Pattern(regexp="[A-Za-z0-9_.-]{1,64}") private String senderCompId="BORROWER_DEMO";
    private boolean fixEnabled;
    private boolean kafkaEnabled;
    private boolean workersEnabled=true;
    @NotBlank private String listenAddress="127.0.0.1";
    @Min(1) @Max(65535) private int listenPort=9876;
    @NotBlank private String storeDirectory="./runtime/fix/borrower";
    @Min(1) private int reconnectSeconds=5;
    @Min(5) private int heartbeatSeconds=30;
    @Min(1) private int retrySeconds=10;
    @Min(1) private int receiptTimeoutSeconds=30;
    @Min(1) private int maxDeliveryAttempts=20;
    @Min(1) private int maxPublicationAttempts=20;
    @Min(1024) @Max(1048576) private int maxPayloadBytes=262144;
    @NotBlank private String outboundTopic="borrower.commands.v1";
    @NotBlank private String inboundTopic="lender.commands.v1";
    @NotBlank private String consumerGroup="borrower-fix-adapter";
    @NotEmpty private List<@Valid RouteProperties> routes=new ArrayList<>();
}
