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
import lombok.Builder;
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
    title = "Resize a DigitalOcean database cluster",
    description = "Changes a database cluster's size and/or node count. DigitalOcean processes the resize " +
        "asynchronously (HTTP 202, empty response body) and applies it with no downtime for most engines."
)
@Plugin(
    examples = {
        @Example(
            title = "Resize a database cluster to a bigger plan with a standby node",
            full = true,
            code = """
                id: digitalocean_resize_database
                namespace: company.team

                tasks:
                  - id: resize_database
                    type: io.kestra.plugin.digitalocean.database.Resize
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    databaseId: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
                    size: "db-s-2vcpu-4gb"
                    numNodes: 2
                """
        )
    }
)
public class Resize extends AbstractDigitalOceanTask implements RunnableTask<VoidOutput> {

    @Schema(title = "Database cluster ID", description = "UUID of the database cluster to resize.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> databaseId;

    @Schema(title = "New size", description = "New database cluster size slug, e.g. db-s-2vcpu-4gb.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> size;

    @Schema(title = "Number of nodes", description = "New number of nodes in the cluster, from 1 (no standby) to 3. Defaults to 1.")
    @Builder.Default
    @PluginProperty(group = "main")
    private Property<Integer> numNodes = Property.ofValue(1);

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rDatabaseId = runContext.render(databaseId).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("databaseId is required")
        );
        var rSize = runContext.render(size).as(String.class).orElseThrow(() -> new IllegalArgumentException("size is required"));
        var rNumNodes = requireInRange("numNodes", runContext.render(numNodes).as(Integer.class).orElse(1), 1, 3);
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        var payload = new LinkedHashMap<String, Object>();
        payload.put("size", rSize);
        payload.put("num_nodes", rNumNodes);

        logger.info("Resizing DigitalOcean database cluster {} to {}", rDatabaseId, rSize);

        var url = join(rBaseUrl, "v2/databases/" + encodePathSegment(rDatabaseId) + "/resize");
        var requestBuilder = HttpRequest.builder()
            .uri(URI.create(url))
            .method("PUT")
            .body(HttpRequest.JsonRequestBody.of(payload));

        request(runContext, options, rApiToken, requestBuilder, String.class);

        return new VoidOutput();
    }
}
