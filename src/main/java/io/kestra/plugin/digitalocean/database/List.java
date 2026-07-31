package io.kestra.plugin.digitalocean.database;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTask;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List DigitalOcean database clusters",
    description = "Lists managed database clusters on the account, following DigitalOcean's page-based " +
        "pagination automatically. Each row has its connection, private_connection, and users fields " +
        "stripped before being returned: DigitalOcean's list endpoint embeds credentials (a connection " +
        "user/password, a uri with the password inlined, and a users array of passwords) in every item, " +
        "and this task never surfaces them in outputs or internal storage."
)
@Plugin(
    examples = {
        @Example(
            title = "List all database clusters and log how many exist",
            full = true,
            code = """
                id: digitalocean_list_databases
                namespace: company.team

                tasks:
                  - id: list_databases
                    type: io.kestra.plugin.digitalocean.database.List
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                  - id: log_total
                    type: io.kestra.plugin.core.log.Log
                    message: "Found {{ outputs.list_databases.total }} database cluster(s)"
                """
        )
    }
)
public class List extends AbstractDigitalOceanTask implements RunnableTask<AbstractDigitalOceanTask.PageOutput> {

    @Schema(title = "Page size", description = "Number of database clusters requested per page. Defaults to 200, the maximum allowed by the DigitalOcean API.")
    @Builder.Default
    @PluginProperty(group = "processing")
    private Property<Integer> perPage = Property.ofValue(200);

    @Schema(
        title = "How to fetch the results",
        description = "FETCH returns all database clusters, FETCH_ONE returns the first one, STORE saves them to " +
            "internal storage as an ion file, NONE returns only the count. Defaults to FETCH."
    )
    @Builder.Default
    @PluginProperty(group = "processing")
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Override
    public PageOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rPerPage = requireInRange("perPage", runContext.render(perPage).as(Integer.class).orElse(200), 1, 200);
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        logger.info("Listing DigitalOcean database clusters");

        var result = fetchAllPages(runContext, options, rApiToken, rBaseUrl, "v2/databases", rPerPage, "databases");

        logger.info("Found {} database cluster(s)", result.total());

        var rows = result.items().stream().map(List::sanitize).toList();
        return toPageOutput(runContext, fetchType, rows, result.total());
    }

    /**
     * Strips the credential-bearing fields DigitalOcean embeds in every database list item: connection
     * and private_connection each carry a user/password and a uri with the password inlined, and users
     * carries a password per database user. Dropping only "password" would still leak it through uri.
     */
    private static Map<String, Object> sanitize(Map<String, Object> database) {
        var sanitized = new LinkedHashMap<>(database);
        sanitized.remove("connection");
        sanitized.remove("private_connection");
        sanitized.remove("users");
        return sanitized;
    }
}
