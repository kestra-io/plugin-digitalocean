package io.kestra.plugin.digitalocean.app.deployment;

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
    title = "Get a DigitalOcean App Platform deployment",
    description = "Reads a deployment's phase and step counts, plus the reason of the first failed step when there is one."
)
@Plugin(
    examples = {
        @Example(
            title = "Get a deployment and log its phase",
            full = true,
            code = """
                id: digitalocean_get_app_deployment
                namespace: company.team

                tasks:
                  - id: get_deployment
                    type: io.kestra.plugin.digitalocean.app.deployment.Get
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    appId: 4f6c71e2-1e90-4762-9fee-6cc4a0a9f2cf
                    deploymentId: 3aa4d20e-5527-4c00-b496-601fbd22520a
                  - id: log_phase
                    type: io.kestra.plugin.core.log.Log
                    message: "Deployment is {{ outputs.get_deployment.phase }} ({{ outputs.get_deployment.successSteps }}/{{ outputs.get_deployment.totalSteps }} steps done)"
                """
        )
    }
)
public class Get extends AbstractDigitalOceanTask implements RunnableTask<DeploymentOutput> {

    @Schema(title = "App ID", description = "ID of the App Platform app.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> appId;

    @Schema(title = "Deployment ID", description = "ID of the deployment to read.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> deploymentId;

    @Override
    public DeploymentOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rAppId = requireRendered(runContext, appId, String.class, "appId");
        var rDeploymentId = requireRendered(runContext, deploymentId, String.class, "deploymentId");
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        var url = join(rBaseUrl, "v2/apps/" + encodePathSegment(rAppId) + "/deployments/" + encodePathSegment(rDeploymentId));
        var requestBuilder = HttpRequest.builder().uri(URI.create(url)).method("GET");
        var body = requestJson(runContext, options, rApiToken, requestBuilder);
        var output = DeploymentOutput.from(unwrap(body, "deployment"));

        logger.info("DigitalOcean App Platform deployment {} is {}", rDeploymentId, output.getPhase());
        return output;
    }
}
