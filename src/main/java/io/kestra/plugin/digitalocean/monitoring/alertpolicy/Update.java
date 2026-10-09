package io.kestra.plugin.digitalocean.monitoring.alertpolicy;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTask;
import io.kestra.plugin.digitalocean.monitoring.models.AlertChannels;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Update a DigitalOcean monitoring alert policy",
    description = "Updates an existing alert policy by its UUID."
)
@Plugin(
    examples = {
        @Example(
            title = "Update threshold and description on an existing alert policy",
            full = true,
            code = """
                id: digitalocean_update_alert_policy
                namespace: company.team

                tasks:
                  - id: update_alert
                    type: io.kestra.plugin.digitalocean.monitoring.alertpolicy.Update
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    alertUuid: "7343e061-e0c1-4b71-a476-c56dfaa3d2ad"
                    policyType: "v1/insights/droplet/cpu"
                    policyDescription: "Updated high CPU usage alert"
                    compare: "greater_than"
                    value: 90.0
                    window: "10m"
                    alerts:
                      email:
                        - "devops@company.com"
                """
        )
    }
)
public class Update extends AbstractDigitalOceanTask implements RunnableTask<AlertPolicyOutput> {

    @Schema(title = "Alert policy UUID")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> alertUuid;

    @Schema(title = "Alert policy metric type", description = "e.g. v1/insights/droplet/cpu, v1/insights/droplet/memory, v1/insights/droplet/disk, v1/insights/droplet/bandwidth_inbound, v1/insights/droplet/bandwidth_outbound")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> policyType;

    @Schema(title = "Alert policy description")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> policyDescription;

    @Schema(title = "Comparison operator", description = "greater_than or less_than")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> compare;

    @Schema(title = "Threshold value")
    @NotNull
    @PluginProperty(group = "main")
    private Property<Double> value;

    @Schema(title = "Evaluation window", description = "5m, 10m, 30m, 1hr")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> window;

    @Schema(title = "Entities monitored by droplet ID")
    @PluginProperty(group = "main")
    private Property<List<String>> entities;

    @Schema(title = "Tags monitored by the alert policy")
    @PluginProperty(group = "main")
    private Property<List<String>> tags;

    @Schema(title = "Notification channels")
    @NotNull
    @PluginProperty(group = "main")
    private Property<AlertChannels> alerts;

    @Schema(title = "Whether the alert policy is enabled", description = "Defaults to true")
    @Builder.Default
    @PluginProperty(group = "main")
    private Property<Boolean> enabled = Property.ofValue(true);

    @Override
    public AlertPolicyOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rAlertUuid = requireRendered(runContext, alertUuid, String.class, "alertUuid");
        var rType = requireRendered(runContext, policyType, String.class, "policyType");
        var rDescription = requireRendered(runContext, policyDescription, String.class, "policyDescription");
        var rCompare = requireRendered(runContext, compare, String.class, "compare");
        var rValue = requireRendered(runContext, value, Double.class, "value");
        var rWindow = requireRendered(runContext, window, String.class, "window");
        var rEntities = runContext.render(entities).asList(String.class);
        var rTags = runContext.render(tags).asList(String.class);
        var rAlerts = requireRendered(runContext, alerts, AlertChannels.class, "alerts");
        var rEnabled = runContext.render(enabled).as(Boolean.class).orElse(true);

        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        logger.info("Updating DigitalOcean alert policy {}", rAlertUuid);

        var payload = new HashMap<String, Object>();
        payload.put("type", rType);
        payload.put("description", rDescription);
        payload.put("compare", rCompare);
        payload.put("value", rValue);
        payload.put("window", rWindow);
        if (!rEntities.isEmpty()) {
            payload.put("entities", rEntities);
        }
        if (!rTags.isEmpty()) {
            payload.put("tags", rTags);
        }
        payload.put("alerts", rAlerts.toMap());
        payload.put("enabled", rEnabled);

        var url = join(rBaseUrl, "v2/monitoring/alerts/" + encodePathSegment(rAlertUuid));
        var requestBuilder = HttpRequest.builder()
            .uri(URI.create(url))
            .method("PUT")
            .body(HttpRequest.JsonRequestBody.builder().content(payload).build());

        var body = requestJson(runContext, options, rApiToken, requestBuilder);

        return AlertPolicyOutput.from(unwrap(body, "policy"));
    }
}
