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

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Get a DigitalOcean domain zone",
    description = "Reads a single domain zone's details from the DigitalOcean API."
)
@Plugin(
    examples = {
        @Example(
            title = "Get a domain zone and log its TTL",
            full = true,
            code = """
                id: digitalocean_get_domain
                namespace: company.team

                tasks:
                  - id: get_domain
                    type: io.kestra.plugin.digitalocean.domain.Get
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    name: "example.com"
                  - id: log_ttl
                    type: io.kestra.plugin.core.log.Log
                    message: "Domain {{ outputs.get_domain.name }} has a default TTL of {{ outputs.get_domain.ttl }}s"
                """
        )
    }
)
public class Get extends AbstractDigitalOceanTask implements RunnableTask<DomainOutput> {

    @Schema(title = "Domain name", description = "Fully qualified zone name to read, e.g. example.com.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> name;

    @Override
    public DomainOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rName = runContext.render(name).as(String.class).orElseThrow(() -> new IllegalArgumentException("name is required"));
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        logger.info("Fetching DigitalOcean domain zone {}", rName);

        var url = join(rBaseUrl, "v2/domains/" + encodePathSegment(rName));
        var requestBuilder = HttpRequest.builder().uri(URI.create(url)).method("GET");
        var body = requestJson(runContext, options, rApiToken, requestBuilder);

        return DomainOutput.from(unwrap(body, "domain"));
    }
}
