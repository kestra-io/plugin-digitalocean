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

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Get a DigitalOcean firewall",
    description = "Reads a single cloud firewall's details from the DigitalOcean API."
)
@Plugin(
    examples = {
        @Example(
            title = "Get a firewall and log its status",
            full = true,
            code = """
                id: digitalocean_get_firewall
                namespace: company.team

                tasks:
                  - id: get_firewall
                    type: io.kestra.plugin.digitalocean.firewall.Get
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    firewallId: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
                  - id: log_status
                    type: io.kestra.plugin.core.log.Log
                    message: "Firewall {{ outputs.get_firewall.name }} is {{ outputs.get_firewall.status }}"
                """
        )
    }
)
public class Get extends AbstractDigitalOceanTask implements RunnableTask<FirewallOutput> {

    @Schema(title = "Firewall ID", description = "UUID of the firewall to read.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> firewallId;

    @Override
    public FirewallOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rFirewallId = runContext.render(firewallId).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("firewallId is required")
        );
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        logger.info("Fetching DigitalOcean firewall {}", rFirewallId);

        var url = join(rBaseUrl, "v2/firewalls/" + rFirewallId);
        var requestBuilder = HttpRequest.builder().uri(URI.create(url)).method("GET");
        var body = requestJson(runContext, options, rApiToken, requestBuilder);

        return FirewallOutput.from(unwrap(body, "firewall"));
    }
}
