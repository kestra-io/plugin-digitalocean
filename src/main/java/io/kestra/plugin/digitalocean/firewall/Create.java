package io.kestra.plugin.digitalocean.firewall;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create a DigitalOcean firewall",
    description = "Creates a new cloud firewall with inbound and outbound rules, optionally applied to a set of droplets."
)
@Plugin(
    examples = {
        @Example(
            title = "Create a firewall allowing inbound SSH and HTTP, and all outbound traffic",
            full = true,
            code = """
                id: digitalocean_create_firewall
                namespace: company.team

                tasks:
                  - id: create_firewall
                    type: io.kestra.plugin.digitalocean.firewall.Create
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    name: "web-firewall"
                    inboundRules:
                      - protocol: "tcp"
                        ports: "22"
                        sources:
                          addresses: ["0.0.0.0/0", "::/0"]
                      - protocol: "tcp"
                        ports: "80"
                        sources:
                          addresses: ["0.0.0.0/0", "::/0"]
                    outboundRules:
                      - protocol: "tcp"
                        ports: "1-65535"
                        destinations:
                          addresses: ["0.0.0.0/0", "::/0"]
                    dropletIds:
                      - 3164444
                """
        )
    }
)
public class Create extends AbstractDigitalOceanTask implements RunnableTask<FirewallOutput> {

    @Schema(title = "Firewall name", description = "Human-readable name for the firewall, must be unique on the account.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> name;

    @Schema(
        title = "Inbound rules",
        description = "Inbound rules, each with `protocol` (tcp, udp, or icmp), `ports`, and a `sources` object " +
            "(`addresses`, `droplet_ids`, `tags`, or `load_balancer_uids`)."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<List<Map<String, Object>>> inboundRules;

    @Schema(
        title = "Outbound rules",
        description = "Outbound rules, each with `protocol` (tcp, udp, or icmp), `ports`, and a `destinations` object " +
            "(`addresses`, `droplet_ids`, `tags`, or `load_balancer_uids`)."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<List<Map<String, Object>>> outboundRules;

    @Schema(title = "Droplet IDs", description = "Numeric IDs of the droplets to apply the firewall to.")
    @PluginProperty(group = "advanced")
    private Property<List<Long>> dropletIds;

    @Override
    public FirewallOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rName = runContext.render(name).as(String.class).orElseThrow(() -> new IllegalArgumentException("name is required"));
        @SuppressWarnings("unchecked")
        var rInboundRules = (List<Map<String, Object>>) runContext.render(inboundRules).asList(Map.class);
        @SuppressWarnings("unchecked")
        var rOutboundRules = (List<Map<String, Object>>) runContext.render(outboundRules).asList(Map.class);
        var rDropletIds = runContext.render(dropletIds).asList(Long.class);
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        var payload = new LinkedHashMap<String, Object>();
        payload.put("name", rName);
        payload.put("inbound_rules", rInboundRules);
        payload.put("outbound_rules", rOutboundRules);
        if (!rDropletIds.isEmpty()) {
            payload.put("droplet_ids", rDropletIds);
        }

        logger.info("Creating DigitalOcean firewall '{}'", rName);

        var url = join(rBaseUrl, "v2/firewalls");
        var requestBuilder = HttpRequest.builder()
            .uri(URI.create(url))
            .method("POST")
            .body(HttpRequest.JsonRequestBody.of(payload));

        var body = requestJson(runContext, options, rApiToken, requestBuilder);
        return FirewallOutput.from(unwrap(body, "firewall"));
    }
}
