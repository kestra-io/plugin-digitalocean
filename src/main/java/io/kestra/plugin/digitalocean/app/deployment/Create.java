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
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Deploy a DigitalOcean App Platform app",
    description = """
        Starts a deployment of an App Platform app, which pulls the latest changes from its source and \
        rolls them out. The task returns as soon as the deployment is scheduled; poll `app.deployment.Get` \
        in a `LoopUntil` until the phase is ACTIVE (live) or ERROR, CANCELED, or SUPERSEDED (will not go live)."""
)
@Plugin(
    examples = {
        @Example(
            title = "Deploy an app, wait until it is live, and fail the flow if it is not",
            full = true,
            code = """
                id: digitalocean_deploy_app
                namespace: company.team

                tasks:
                  - id: deploy
                    type: io.kestra.plugin.digitalocean.app.deployment.Create
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    appId: 4f6c71e2-1e90-4762-9fee-6cc4a0a9f2cf

                  - id: wait_for_deployment
                    type: io.kestra.plugin.core.flow.LoopUntil
                    condition: "{{ ['ACTIVE', 'ERROR', 'CANCELED', 'SUPERSEDED'] contains outputs.get_deployment.phase }}"
                    checkFrequency:
                      interval: PT15S
                      maxDuration: PT30M
                    tasks:
                      - id: get_deployment
                        type: io.kestra.plugin.digitalocean.app.deployment.Get
                        apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                        appId: 4f6c71e2-1e90-4762-9fee-6cc4a0a9f2cf
                        deploymentId: "{{ outputs.deploy.id }}"

                  - id: fail_if_not_live
                    type: io.kestra.plugin.core.execution.Fail
                    condition: "{{ outputs.get_deployment.phase != 'ACTIVE' }}"
                    errorMessage: "Deployment ended in {{ outputs.get_deployment.phase }}: {{ outputs.get_deployment.errorMessage ?? 'no reason given' }}"
                """
        )
    }
)
public class Create extends AbstractDigitalOceanTask implements RunnableTask<DeploymentOutput> {

    @Schema(title = "App ID", description = "ID of the App Platform app to deploy.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> appId;

    @Schema(
        title = "Force a build",
        description = "Build the app from source even when DigitalOcean has a cached build it could reuse. Defaults to false."
    )
    @Builder.Default
    @PluginProperty(group = "advanced")
    private Property<Boolean> forceBuild = Property.ofValue(false);

    @Override
    public DeploymentOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rAppId = requireRendered(runContext, appId, String.class, "appId");
        var rForceBuild = runContext.render(forceBuild).as(Boolean.class).orElse(false);
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        logger.info("Starting a deployment of DigitalOcean App Platform app {}", rAppId);

        var url = join(rBaseUrl, "v2/apps/" + encodePathSegment(rAppId) + "/deployments");
        var requestBuilder = HttpRequest.builder()
            .uri(URI.create(url))
            .method("POST")
            .body(HttpRequest.JsonRequestBody.of(Map.of("force_build", rForceBuild)));

        var body = requestJson(runContext, options, rApiToken, requestBuilder);
        var output = DeploymentOutput.from(unwrap(body, "deployment"));

        logger.info("Deployment {} scheduled, phase {}", output.getId(), output.getPhase());
        return output;
    }
}
