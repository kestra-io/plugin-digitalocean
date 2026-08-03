package io.kestra.plugin.digitalocean;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

/**
 * Shared behavior for every "list a resource" task: page size, fetch type, bounds checking, pagination,
 * and turning the fetched items into {@link AbstractDigitalOceanTask.PageOutput}. Concrete subclasses only
 * supply the endpoint path, the JSON array key, and a label for logging.
 */
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractDigitalOceanListTask extends AbstractDigitalOceanTask implements RunnableTask<AbstractDigitalOceanTask.PageOutput> {

    @Schema(title = "Page size", description = "Number of resources requested per page. Defaults to 200, the maximum allowed by the DigitalOcean API.")
    @Builder.Default
    @PluginProperty(group = "processing")
    protected Property<Integer> perPage = Property.ofValue(200);

    @Schema(
        title = "How to fetch the results",
        description = "FETCH returns all resources, FETCH_ONE returns the first one, STORE saves them to " +
            "internal storage as an ion file, NONE returns only the count. Defaults to FETCH."
    )
    @Builder.Default
    @PluginProperty(group = "processing")
    protected Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Override
    public final PageOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rPerPage = requireInRange("perPage", runContext.render(perPage).as(Integer.class).orElse(200), 1, 200);
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        logger.info("Listing DigitalOcean {}", resourceLabel());

        var result = fetchAllPages(runContext, options, rApiToken, rBaseUrl, path(runContext), rPerPage, arrayKey());

        logger.info("Found {} {}", result.total(), resourceLabel());

        var rows = transformRows(runContext, result.items());
        return toPageOutput(runContext, fetchType, rows, result.total());
    }

    /** Endpoint path relative to the API base, e.g. {@code "v2/droplets"}; may render/encode dynamic segments. */
    protected abstract String path(RunContext runContext) throws Exception;

    /** JSON array key the response wraps the items in, e.g. {@code "droplets"}. */
    protected abstract String arrayKey();

    /** Plural resource label used only for log lines, e.g. {@code "droplets"}. */
    protected abstract String resourceLabel();

    /**
     * Hook applied to every fetched row before it becomes output, e.g. {@code database.List} strips
     * credential-bearing fields here. Identity by default.
     */
    protected List<Map<String, Object>> transformRows(RunContext runContext, List<Map<String, Object>> rows) throws Exception {
        return rows;
    }
}
