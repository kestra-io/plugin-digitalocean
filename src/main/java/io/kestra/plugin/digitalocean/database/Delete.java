package io.kestra.plugin.digitalocean.database;

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
    title = "Delete a DigitalOcean database cluster",
    description = "Permanently destroys a managed database cluster and all its data. This cannot be undone."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete a database cluster",
            full = true,
            code = """
                id: digitalocean_delete_database
                namespace: company.team

                tasks:
                  - id: delete_database
                    type: io.kestra.plugin.digitalocean.database.Delete
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    databaseId: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
                """
        )
    }
)
public class Delete extends AbstractDigitalOceanTask implements RunnableTask<VoidOutput> {

    @Schema(title = "Database cluster ID", description = "UUID of the database cluster to delete.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> databaseId;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rDatabaseId = runContext.render(databaseId).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("databaseId is required")
        );
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        logger.info("Deleting DigitalOcean database cluster {}", rDatabaseId);

        var url = join(rBaseUrl, "v2/databases/" + rDatabaseId);
        var requestBuilder = HttpRequest.builder().uri(URI.create(url)).method("DELETE");
        request(runContext, options, rApiToken, requestBuilder, String.class);

        return new VoidOutput();
    }
}
