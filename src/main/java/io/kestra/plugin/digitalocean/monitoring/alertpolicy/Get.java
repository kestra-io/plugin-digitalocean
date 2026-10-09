package io.kestra.plugin.digitalocean.monitoring.alertpolicy;

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

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Get a DigitalOcean monitoring alert policy",
    description = "Reads a single monitoring alert policy by its UUID."
)
@Plugin(
    examples = {
        @Example(
            title = "Get an alert policy and log its threshold and description",
            full = true,
            code = """
                id: digitalocean_get_alert_policy
                namespace: company.team

                tasks:
                  - id: get_policy
                    type: io.kestra.plugin.digitalocean.monitoring.alertpolicy.Get
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    alertUuid: "7343e061-e0c1-4b71-a476-c56dfaa3d2ad"
                  - id: log_policy
                    type: io.kestra.plugin.core.log.Log
                    message: "Policy {{ outputs.get_policy.description }} triggers when {{ outputs.get_policy.type }} is {{ outputs.get_policy.compare }} {{ outputs.get_policy.value }}"
                """
        )
    }
)
public class Get extends AbstractDigitalOceanTask implements RunnableTask<AlertPolicyOutput> {

    @Schema(title = "Alert policy UUID")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> alertUuid;

    @Override
    public AlertPolicyOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rAlertUuid = requireRendered(runContext, alertUuid, String.class, "alertUuid");
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        logger.info("Fetching DigitalOcean alert policy {}", rAlertUuid);

        var url = join(rBaseUrl, "v2/monitoring/alerts/" + encodePathSegment(rAlertUuid));
        var requestBuilder = HttpRequest.builder().uri(URI.create(url)).method("GET");
        var body = requestJson(runContext, options, rApiToken, requestBuilder);

        return AlertPolicyOutput.from(unwrap(body, "policy"));
    }
}
