package io.kestra.plugin.digitalocean.firewall;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.VoidOutput;
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
    title = "Delete a DigitalOcean firewall",
    description = "Permanently destroys a cloud firewall. This cannot be undone; the droplets it applied to are not affected."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete a firewall",
            full = true,
            code = """
                id: digitalocean_delete_firewall
                namespace: company.team

                tasks:
                  - id: delete_firewall
                    type: io.kestra.plugin.digitalocean.firewall.Delete
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    firewallId: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
                """
        )
    }
)
public class Delete extends AbstractDigitalOceanTask implements RunnableTask<VoidOutput> {

    @Schema(title = "Firewall ID", description = "UUID of the firewall to delete.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> firewallId;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rFirewallId = runContext.render(firewallId).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("firewallId is required")
        );
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        logger.info("Deleting DigitalOcean firewall {}", rFirewallId);

        var url = join(rBaseUrl, "v2/firewalls/" + encodePathSegment(rFirewallId));
        var requestBuilder = HttpRequest.builder().uri(URI.create(url)).method("DELETE");
        request(runContext, options, rApiToken, requestBuilder, String.class);

        return new VoidOutput();
    }
}
