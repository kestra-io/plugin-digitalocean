package io.kestra.plugin.digitalocean.volume;

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
    title = "List DigitalOcean volumes",
    description = "Lists block storage volumes on the account, following DigitalOcean's page-based pagination automatically."
)
@Plugin(
    examples = {
        @Example(
            title = "List all volumes and log how many exist",
            full = true,
            code = """
                id: digitalocean_list_volumes
                namespace: company.team

                tasks:
                  - id: list_volumes
                    type: io.kestra.plugin.digitalocean.volume.List
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                  - id: log_total
                    type: io.kestra.plugin.core.log.Log
                    message: "Found {{ outputs.list_volumes.total }} volume(s)"
                """
        )
    }
)
public class List extends AbstractDigitalOceanTask implements RunnableTask<AbstractDigitalOceanTask.PageOutput> {

    @Schema(title = "Page size", description = "Number of volumes requested per page. Defaults to 200, the maximum allowed by the DigitalOcean API.")
    @Builder.Default
    @Min(1)
    @Max(200)
    @PluginProperty(group = "processing")
    private Property<Integer> perPage = Property.ofValue(200);

    @Schema(
        title = "How to fetch the results",
        description = "FETCH returns all volumes, FETCH_ONE returns the first one, STORE saves them to " +
            "internal storage as an ion file, NONE returns only the count. Defaults to FETCH."
    )
    @Builder.Default
    @PluginProperty(group = "processing")
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Override
    public PageOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rPerPage = runContext.render(perPage).as(Integer.class).orElse(200);
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        logger.info("Listing DigitalOcean volumes");

        var result = fetchAllPages(runContext, options, rApiToken, rBaseUrl, "v2/volumes", rPerPage, "volumes");

        logger.info("Found {} volume(s)", result.total());

        return toPageOutput(runContext, fetchType, result.items(), result.total());
    }
}
