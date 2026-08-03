package io.kestra.plugin.digitalocean.database;

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
    title = "Get a DigitalOcean database cluster",
    description = "Reads a single managed database cluster's details from the DigitalOcean API."
)
@Plugin(
    examples = {
        @Example(
            title = "Get a database cluster and log its status",
            full = true,
            code = """
                id: digitalocean_get_database
                namespace: company.team

                tasks:
                  - id: get_database
                    type: io.kestra.plugin.digitalocean.database.Get
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    databaseId: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
                  - id: log_status
                    type: io.kestra.plugin.core.log.Log
                    message: "Database {{ outputs.get_database.name }} is {{ outputs.get_database.status }}"
                """
        )
    }
)
public class Get extends AbstractDigitalOceanTask implements RunnableTask<DatabaseOutput> {

    @Schema(title = "Database cluster ID", description = "UUID of the database cluster to read.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> databaseId;

    @Override
    public DatabaseOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rDatabaseId = requireRendered(runContext, databaseId, String.class, "databaseId");
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        logger.info("Fetching DigitalOcean database cluster {}", rDatabaseId);

        var url = join(rBaseUrl, "v2/databases/" + encodePathSegment(rDatabaseId));
        var requestBuilder = HttpRequest.builder().uri(URI.create(url)).method("GET");
        var body = requestJson(runContext, options, rApiToken, requestBuilder);

        return DatabaseOutput.from(unwrap(body, "database"));
    }
}
