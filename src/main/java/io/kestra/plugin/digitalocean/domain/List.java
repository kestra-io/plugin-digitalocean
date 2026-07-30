package io.kestra.plugin.digitalocean.domain;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List DNS records of a DigitalOcean domain",
    description = "Lists DNS records for a domain, following DigitalOcean's page-based pagination automatically."
)
@Plugin(
    examples = {
        @Example(
            title = "List all records of a domain",
            full = true,
            code = """
                id: digitalocean_list_records
                namespace: company.team

                tasks:
                  - id: list_records
                    type: io.kestra.plugin.digitalocean.domain.List
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    domain: "example.com"
                  - id: log_total
                    type: io.kestra.plugin.core.log.Log
                    message: "Found {{ outputs.list_records.total }} record(s)"
                """
        )
    }
)
public class List extends AbstractDigitalOceanTask implements RunnableTask<AbstractDigitalOceanTask.PageOutput> {

    @Schema(title = "Domain", description = "Domain name whose records to list, e.g. example.com.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> domain;

    @Schema(title = "Page size", description = "Number of records requested per page. Defaults to 200, the maximum allowed by the DigitalOcean API.")
    @Builder.Default
    @Min(1)
    @Max(200)
    @PluginProperty(group = "processing")
    private Property<Integer> perPage = Property.ofValue(200);

    @Schema(
        title = "How to fetch the results",
        description = "FETCH returns all records, FETCH_ONE returns the first one, STORE saves them to " +
            "internal storage as an ion file, NONE returns only the count. Defaults to FETCH."
    )
    @Builder.Default
    @PluginProperty(group = "processing")
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Override
    public PageOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rDomain = runContext.render(domain).as(String.class).orElseThrow(() -> new IllegalArgumentException("domain is required"));
        var rPerPage = runContext.render(perPage).as(Integer.class).orElse(200);
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        logger.info("Listing DNS records for domain {}", rDomain);

        var result = fetchAllPages(runContext, options, rApiToken, rBaseUrl, "v2/domains/" + rDomain + "/records", rPerPage, "domain_records");

        logger.info("Found {} record(s)", result.total());

        return toPageOutput(runContext, fetchType, result.items(), result.total());
    }
}
