package io.kestra.plugin.digitalocean.domain;

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
    title = "Delete a DigitalOcean DNS record",
    description = "Permanently removes a DNS record from a domain. This cannot be undone."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete a DNS record",
            full = true,
            code = """
                id: digitalocean_delete_record
                namespace: company.team

                tasks:
                  - id: delete_record
                    type: io.kestra.plugin.digitalocean.domain.Delete
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    domain: "example.com"
                    recordId: "12345"
                """
        )
    }
)
public class Delete extends AbstractDigitalOceanTask implements RunnableTask<VoidOutput> {

    @Schema(title = "Domain", description = "Domain name the record belongs to, e.g. example.com.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> domain;

    @Schema(title = "Record ID", description = "Numeric identifier of the DNS record to delete.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> recordId;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rDomain = runContext.render(domain).as(String.class).orElseThrow(() -> new IllegalArgumentException("domain is required"));
        var rRecordId = runContext.render(recordId).as(String.class).orElseThrow(() -> new IllegalArgumentException("recordId is required"));
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        logger.info("Deleting DNS record {} for domain {}", rRecordId, rDomain);

        var url = join(rBaseUrl, "v2/domains/" + encodePathSegment(rDomain) + "/records/" + encodePathSegment(rRecordId));
        var requestBuilder = HttpRequest.builder().uri(URI.create(url)).method("DELETE");
        request(runContext, options, rApiToken, requestBuilder, String.class);

        return null;
    }
}
