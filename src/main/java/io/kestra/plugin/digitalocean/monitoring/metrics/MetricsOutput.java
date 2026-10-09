package io.kestra.plugin.digitalocean.monitoring.metrics;

import io.kestra.core.models.tasks.Output;
import io.kestra.plugin.digitalocean.monitoring.models.MetricSeries;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asString;

@Builder
@Getter
public class MetricsOutput implements Output {

    @Schema(title = "Response status", description = "e.g. success")
    private final String status;

    @Schema(title = "Result type", description = "e.g. matrix")
    private final String resultType;

    @Schema(title = "Metric series results")
    private final List<MetricSeries> data;

    @SuppressWarnings("unchecked")
    public static MetricsOutput from(Map<String, Object> body) {
        if (body == null) {
            return null;
        }

        var status = asString(body.get("status"));
        String resultType = null;
        List<MetricSeries> seriesList = null;

        if (body.get("data") instanceof Map<?, ?> dataMap) {
            resultType = asString(dataMap.get("resultType"));
            if (dataMap.get("result") instanceof List<?> resultList) {
                seriesList = resultList.stream()
                    .filter(item -> item instanceof Map)
                    .map(item -> MetricSeries.from((Map<String, Object>) item))
                    .toList();
            }
        }

        return MetricsOutput.builder()
            .status(status)
            .resultType(resultType)
            .data(seriesList)
            .build();
    }
}
