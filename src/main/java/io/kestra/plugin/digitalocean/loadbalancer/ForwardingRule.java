package io.kestra.plugin.digitalocean.loadbalancer;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single forwarding rule for {@link Create} and {@link Update}. Fields use idiomatic Java camelCase so a
 * flow renders them the same way as every other property; {@link #toMap()} converts to DigitalOcean's
 * snake_case JSON keys for the outgoing request only. A plain bean (not a Property field) so a
 * {@code Property<java.util.List<ForwardingRule>>} can render the whole list at once.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class ForwardingRule {

    @Schema(title = "Entry protocol", description = "Protocol accepted on the load balancer's public-facing side, e.g. http, https, tcp, or udp.")
    @NotNull
    private String entryProtocol;

    @Schema(title = "Entry port", description = "Port accepted on the load balancer's public-facing side.")
    @NotNull
    private Integer entryPort;

    @Schema(title = "Target protocol", description = "Protocol used to reach the backend droplets, e.g. http, https, tcp, or udp.")
    @NotNull
    private String targetProtocol;

    @Schema(title = "Target port", description = "Port used to reach the backend droplets.")
    @NotNull
    private Integer targetPort;

    @Schema(title = "Certificate ID", description = "UUID of the DigitalOcean-managed or custom certificate used for HTTPS/TLS termination on this rule.")
    private String certificateId;

    @Schema(title = "TLS passthrough", description = "Whether encrypted traffic is passed through to the backend without being decrypted by the load balancer.")
    private Boolean tlsPassthrough;

    Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("entry_protocol", entryProtocol);
        map.put("entry_port", entryPort);
        map.put("target_protocol", targetProtocol);
        map.put("target_port", targetPort);
        if (certificateId != null) {
            map.put("certificate_id", certificateId);
        }
        if (tlsPassthrough != null) {
            map.put("tls_passthrough", tlsPassthrough);
        }
        return map;
    }
}
