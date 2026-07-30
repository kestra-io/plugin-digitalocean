package io.kestra.plugin.digitalocean.domain;

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

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create a DigitalOcean domain zone",
    description = "Registers a new domain zone on the account. The domain's nameservers must already point " +
        "to DigitalOcean for DNS resolution to work; this task only creates the zone, it does not register " +
        "or transfer the domain name itself."
)
@Plugin(
    examples = {
        @Example(
            title = "Create a zone, then add an A record to it",
            full = true,
            code = """
                id: digitalocean_create_domain
                namespace: company.team

                tasks:
                  - id: create_domain
                    type: io.kestra.plugin.digitalocean.domain.Create
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    name: "example.com"
                    ipAddress: "104.131.186.241"
                  - id: create_record
                    type: io.kestra.plugin.digitalocean.domain.record.Create
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    domain: "example.com"
                    recordType: "A"
                    name: "www"
                    data: "104.131.186.241"
                """
        )
    }
)
public class Create extends AbstractDigitalOceanTask implements RunnableTask<DomainOutput> {

    @Schema(title = "Domain name", description = "Fully qualified zone name to create, e.g. example.com.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> name;

    @Schema(
        title = "IP address",
        description = "Optional IPv4 address. When set, DigitalOcean automatically creates an apex A record pointing the zone to it."
    )
    @PluginProperty(group = "main")
    private Property<String> ipAddress;

    @Override
    public DomainOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rName = runContext.render(name).as(String.class).orElseThrow(() -> new IllegalArgumentException("name is required"));
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        var payload = new LinkedHashMap<String, Object>();
        payload.put("name", rName);
        runContext.render(ipAddress).as(String.class).ifPresent(v -> payload.put("ip_address", v));

        logger.info("Creating DigitalOcean domain zone '{}'", rName);

        var url = join(rBaseUrl, "v2/domains");
        var requestBuilder = HttpRequest.builder()
            .uri(URI.create(url))
            .method("POST")
            .body(HttpRequest.JsonRequestBody.of(payload));

        var body = requestJson(runContext, options, rApiToken, requestBuilder);
        return DomainOutput.from(unwrap(body, "domain"));
    }
}
