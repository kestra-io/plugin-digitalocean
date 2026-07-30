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
    title = "Get a DigitalOcean DNS record",
    description = "Reads a single DNS record's details from the DigitalOcean API."
)
@Plugin(
    examples = {
        @Example(
            title = "Get a DNS record and log its data",
            full = true,
            code = """
                id: digitalocean_get_record
                namespace: company.team

                tasks:
                  - id: get_record
                    type: io.kestra.plugin.digitalocean.domain.Get
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    domain: "example.com"
                    recordId: "12345"
                  - id: log_data
                    type: io.kestra.plugin.core.log.Log
                    message: "Record {{ outputs.get_record.name }} points to {{ outputs.get_record.data }}"
                """
        )
    }
)
public class Get extends AbstractDigitalOceanTask implements RunnableTask<DomainRecordOutput> {

    @Schema(title = "Domain", description = "Domain name the record belongs to, e.g. example.com.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> domain;

    @Schema(title = "Record ID", description = "Numeric identifier of the DNS record to read.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> recordId;

    @Override
    public DomainRecordOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rDomain = runContext.render(domain).as(String.class).orElseThrow(() -> new IllegalArgumentException("domain is required"));
        var rRecordId = runContext.render(recordId).as(String.class).orElseThrow(() -> new IllegalArgumentException("recordId is required"));
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        logger.info("Fetching DNS record {} for domain {}", rRecordId, rDomain);

        var url = join(rBaseUrl, "v2/domains/" + rDomain + "/records/" + rRecordId);
        var requestBuilder = HttpRequest.builder().uri(URI.create(url)).method("GET");
        var body = requestJson(runContext, options, rApiToken, requestBuilder);

        return DomainRecordOutput.from(unwrap(body, "domain_record"));
    }
}
