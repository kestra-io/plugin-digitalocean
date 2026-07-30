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
    title = "Create a DigitalOcean database cluster",
    description = "Creates a new managed database cluster. Provisioning typically takes several minutes; this " +
        "task returns as soon as the API accepts the request, without waiting for the cluster to become online."
)
@Plugin(
    examples = {
        @Example(
            title = "Create a single-node Postgres cluster in nyc1",
            full = true,
            code = """
                id: digitalocean_create_database
                namespace: company.team

                tasks:
                  - id: create_database
                    type: io.kestra.plugin.digitalocean.database.Create
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    name: "prod-postgres"
                    engine: "pg"
                    engineVersion: "16"
                    region: "nyc1"
                    size: "db-s-1vcpu-1gb"
                    numNodes: 1
                """
        )
    }
)
public class Create extends AbstractDigitalOceanTask implements RunnableTask<DatabaseOutput> {

    @Schema(title = "Database cluster name", description = "Human-readable name for the cluster, must be unique on the account.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> name;

    @Schema(title = "Engine", description = "Database engine, one of pg, mysql, redis, mongodb, kafka, or opensearch.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> engine;

    @Schema(title = "Engine version", description = "Version of the selected engine, e.g. 16 for pg.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> engineVersion;

    @Schema(title = "Region", description = "Datacenter region slug to create the cluster in, e.g. nyc1, ams3, sgp1.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> region;

    @Schema(title = "Size", description = "Database cluster size slug, e.g. db-s-1vcpu-1gb.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> size;

    @Schema(title = "Number of nodes", description = "Number of nodes in the cluster, from 1 (no standby) to 3. Defaults to 1.")
    @Builder.Default
    @PluginProperty(group = "main")
    private Property<Integer> numNodes = Property.ofValue(1);

    @Override
    public DatabaseOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rName = runContext.render(name).as(String.class).orElseThrow(() -> new IllegalArgumentException("name is required"));
        var rEngine = runContext.render(engine).as(String.class).orElseThrow(() -> new IllegalArgumentException("engine is required"));
        var rVersion = runContext.render(engineVersion).as(String.class).orElseThrow(() -> new IllegalArgumentException("engineVersion is required"));
        var rRegion = runContext.render(region).as(String.class).orElseThrow(() -> new IllegalArgumentException("region is required"));
        var rSize = runContext.render(size).as(String.class).orElseThrow(() -> new IllegalArgumentException("size is required"));
        var rNumNodes = requireInRange("numNodes", runContext.render(numNodes).as(Integer.class).orElse(1), 1, 3);
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        var payload = new LinkedHashMap<String, Object>();
        payload.put("name", rName);
        payload.put("engine", rEngine);
        payload.put("version", rVersion);
        payload.put("region", rRegion);
        payload.put("size", rSize);
        payload.put("num_nodes", rNumNodes);

        logger.info("Creating DigitalOcean database cluster '{}' ({} {})", rName, rEngine, rVersion);

        var url = join(rBaseUrl, "v2/databases");
        var requestBuilder = HttpRequest.builder()
            .uri(URI.create(url))
            .method("POST")
            .body(HttpRequest.JsonRequestBody.of(payload));

        var body = requestJson(runContext, options, rApiToken, requestBuilder);
        return DatabaseOutput.from(unwrap(body, "database"));
    }
}
