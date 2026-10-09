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
    title = "Query DigitalOcean droplet CPU metrics",
    description = "Queries historical CPU metrics (idle, user, system, iowait, etc.) for a specific Droplet over a time window."
)
@Plugin(
    examples = {
        @Example(
            title = "Fetch CPU metrics for a Droplet over the last hour",
            full = true,
            code = """
                id: digitalocean_get_droplet_cpu_metrics
                namespace: company.team

                tasks:
                  - id: cpu_metrics
                    type: io.kestra.plugin.digitalocean.monitoring.metrics.DropletCpu
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    hostId: "12345678"
                    start: "1728000000"
                    end: "1728003600"
                """
        )
    }
)
public class DropletCpu extends AbstractDigitalOceanTask implements RunnableTask<MetricsOutput> {

    @Schema(title = "Droplet host ID")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> hostId;

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
        var rStart = requireRendered(runContext, start, String.class, "start");
        var rEnd = requireRendered(runContext, end, String.class, "end");
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        logger.info("Fetching CPU metrics for Droplet {}", rHostId);

        var query = "host_id=" + URLEncoder.encode(rHostId, StandardCharsets.UTF_8)
            + "&start=" + URLEncoder.encode(rStart, StandardCharsets.UTF_8)
            + "&end=" + URLEncoder.encode(rEnd, StandardCharsets.UTF_8);

        var url = join(rBaseUrl, "v2/monitoring/metrics/droplet/cpu?" + query);
        var requestBuilder = HttpRequest.builder().uri(URI.create(url)).method("GET");
        var body = requestJson(runContext, options, rApiToken, requestBuilder);

        return MetricsOutput.from(body);
    }
}
