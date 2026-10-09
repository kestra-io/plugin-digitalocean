package io.kestra.plugin.digitalocean.monitoring.alertpolicy;

import io.kestra.core.models.tasks.Output;
import io.kestra.plugin.digitalocean.monitoring.models.AlertChannels;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asString;

@Builder
@Getter
public class AlertPolicyOutput implements Output {

    @Schema(title = "Alert policy UUID")
    private final String uuid;

    @Schema(title = "Alert policy type", description = "e.g. v1/insights/droplet/cpu, v1/insights/droplet/memory")
    private final String type;

    @Schema(title = "Alert policy description")
    private final String description;

    @Schema(title = "Comparison operator", description = "greater_than or less_than")
    private final String compare;

    @Schema(title = "Threshold value")
    private final Double value;

    @Schema(title = "Evaluation window", description = "e.g. 5m, 10m, 30m, 1hr")
    private final String window;

    @Schema(title = "Entities monitored by droplet ID")
    private final List<String> entities;

    @Schema(title = "Tags monitored by the alert policy")
    private final List<String> tags;

    @Schema(title = "Notification channels")
    private final AlertChannels alerts;

    @Schema(title = "Whether the alert policy is enabled")
    private final Boolean enabled;

    @SuppressWarnings("unchecked")
    public static AlertPolicyOutput from(Map<String, Object> policy) {
        if (policy == null) {
            return null;
        }

        var entitiesList = policy.get("entities") instanceof List<?> list
            ? list.stream().map(String::valueOf).toList()
            : null;

        var tagsList = policy.get("tags") instanceof List<?> list
            ? list.stream().map(String::valueOf).toList()
            : null;

        var alertsObj = policy.get("alerts") instanceof Map<?, ?> map
            ? AlertChannels.from((Map<String, Object>) map)
            : null;

        Double val = null;
        if (policy.get("value") instanceof Number num) {
            val = num.doubleValue();
        }

        Boolean isEnabled = null;
        if (policy.get("enabled") instanceof Boolean b) {
            isEnabled = b;
        } else if (policy.get("enabled") != null) {
            isEnabled = Boolean.parseBoolean(policy.get("enabled").toString());
        }

        return AlertPolicyOutput.builder()
            .uuid(asString(policy.get("uuid")))
            .type(asString(policy.get("type")))
            .description(asString(policy.get("description")))
            .compare(asString(policy.get("compare")))
            .value(val)
            .window(asString(policy.get("window")))
            .entities(entitiesList)
            .tags(tagsList)
            .alerts(alertsObj)
            .enabled(isEnabled)
            .build();
    }
}
