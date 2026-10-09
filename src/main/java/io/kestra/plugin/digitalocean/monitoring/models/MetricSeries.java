package io.kestra.plugin.digitalocean.monitoring.models;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Builder
@Getter
@Jacksonized
public class MetricSeries {

    @Schema(title = "Metric labels / metadata", description = "Key-value tags associated with the metric series, e.g. host_id, mode, direction")
    private final Map<String, String> metric;

    @Schema(title = "Time series data points")
    private final List<MetricDataPoint> values;

    @SuppressWarnings("unchecked")
    public static MetricSeries from(Map<String, Object> map) {
        if (map == null) {
            return null;
        }

        Map<String, String> labels = null;
        if (map.get("metric") instanceof Map<?, ?> m) {
            labels = (Map<String, String>) m;
        }

        List<MetricDataPoint> points = null;
        if (map.get("values") instanceof List<?> list) {
            points = list.stream()
                .filter(item -> item instanceof List)
                .map(item -> MetricDataPoint.fromRawPair((List<?>) item))
                .filter(Objects::nonNull)
                .toList();
        }

        return MetricSeries.builder()
            .metric(labels)
            .values(points)
            .build();
    }
}
