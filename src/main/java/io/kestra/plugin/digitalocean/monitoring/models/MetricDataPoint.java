package io.kestra.plugin.digitalocean.monitoring.models;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;

@Builder
@Getter
@Jacksonized
public class MetricDataPoint {

    @Schema(title = "Timestamp of the data point")
    private final Instant timestamp;

    @Schema(title = "Metric value as a double")
    private final Double value;

    public static MetricDataPoint fromRawPair(List<?> pair) {
        if (pair == null || pair.size() < 2) {
            return null;
        }

        Instant ts = null;
        if (pair.get(0) instanceof Number num) {
            ts = Instant.ofEpochSecond(num.longValue());
        }

        Double val = null;
        if (pair.get(1) != null) {
            try {
                val = Double.parseDouble(pair.get(1).toString());
            } catch (NumberFormatException ignored) {
            }
        }

        return MetricDataPoint.builder()
            .timestamp(ts)
            .value(val)
            .build();
    }
}
