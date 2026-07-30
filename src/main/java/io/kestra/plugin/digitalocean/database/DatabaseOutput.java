package io.kestra.plugin.digitalocean.database;

import io.kestra.core.models.tasks.Output;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asInteger;
import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asMap;
import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asString;

/**
 * Shared output shape for {@link Get} and {@link Create}. Never exposes the connection's user/password:
 * those are credentials and must not leak into task outputs, execution logs, or the Kestra UI.
 */
@Builder
@Getter
public class DatabaseOutput implements Output {

    @Schema(title = "Database cluster ID", description = "UUID assigned by DigitalOcean.")
    private final String id;

    @Schema(title = "Database cluster name")
    private final String name;

    @Schema(title = "Database engine", description = "One of pg, mysql, redis, mongodb, kafka, or opensearch.")
    private final String engine;

    @Schema(title = "Region slug", description = "Datacenter region the cluster runs in, e.g. nyc1.")
    private final String region;

    @Schema(title = "Cluster status", description = "One of creating, online, resizing, or migrating.")
    private final String status;

    @Schema(title = "Connection host", description = "Hostname to connect to the cluster's primary connection.")
    private final String host;

    @Schema(title = "Connection port")
    private final Integer port;

    public static DatabaseOutput from(Map<String, Object> database) {
        var connection = asMap(database.get("connection"));

        return DatabaseOutput.builder()
            .id(asString(database.get("id")))
            .name(asString(database.get("name")))
            .engine(asString(database.get("engine")))
            .region(asString(database.get("region")))
            .status(asString(database.get("status")))
            .host(connection != null ? asString(connection.get("host")) : null)
            .port(connection != null ? asInteger(connection.get("port")) : null)
            .build();
    }
}
