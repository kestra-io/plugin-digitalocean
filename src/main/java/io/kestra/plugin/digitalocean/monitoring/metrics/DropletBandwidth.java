package io.kestra.plugin.digitalocean.monitoring.metrics;

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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Query DigitalOcean droplet bandwidth metrics",
    description = "Queries bandwidth transfer rate metrics for a Droplet network interface over a time window."
)
@Plugin(
    examples = {
        @Example(
            title = "Fetch public outbound bandwidth metrics for a Droplet",
            full = true,
            code = """
                id: digitalocean_get_droplet_bandwidth_metrics
                namespace: company.team

                tasks:
                  - id: bandwidth_metrics
                    type: io.kestra.plugin.digitalocean.monitoring.metrics.DropletBandwidth
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    hostId: "12345678"
                    networkInterface: "public"
                    direction: "outbound"
                    start: "1728000000"
                    end: "1728003600"
                """
        )
    }
)
public class DropletBandwidth extends AbstractDigitalOceanTask implements RunnableTask<MetricsOutput> {

    @Schema(title = "Droplet host ID")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> hostId;

    @Schema(title = "Network interface", description = "public or private. Defaults to public.")
    @Builder.Default
    @PluginProperty(group = "main")
    private Property<String> networkInterface = Property.ofValue("public");

    @Schema(title = "Traffic direction", description = "inbound or outbound. Defaults to outbound.")
    @Builder.Default
    @PluginProperty(group = "main")
    private Property<String> direction = Property.ofValue("outbound");

    @Schema(title = "Start timestamp in UNIX epoch seconds")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> start;

    @Schema(title = "End timestamp in UNIX epoch seconds")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> end;

    @Override
    public MetricsOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rHostId = requireRendered(runContext, hostId, String.class, "hostId");
        var rInterface = runContext.render(networkInterface).as(String.class).orElse("public");
        var rDirection = runContext.render(direction).as(String.class).orElse("outbound");
        var rStart = requireRendered(runContext, start, String.class, "start");
        var rEnd = requireRendered(runContext, end, String.class, "end");
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        logger.info("Fetching {} {} bandwidth metrics for Droplet {}", rInterface, rDirection, rHostId);

        var query = "host_id=" + URLEncoder.encode(rHostId, StandardCharsets.UTF_8)
            + "&interface=" + URLEncoder.encode(rInterface, StandardCharsets.UTF_8)
            + "&direction=" + URLEncoder.encode(rDirection, StandardCharsets.UTF_8)
            + "&start=" + URLEncoder.encode(rStart, StandardCharsets.UTF_8)
            + "&end=" + URLEncoder.encode(rEnd, StandardCharsets.UTF_8);

        var url = join(rBaseUrl, "v2/monitoring/metrics/droplet/bandwidth?" + query);
        var requestBuilder = HttpRequest.builder().uri(URI.create(url)).method("GET");
        var body = requestJson(runContext, options, rApiToken, requestBuilder);

        return MetricsOutput.from(body);
    }
}
