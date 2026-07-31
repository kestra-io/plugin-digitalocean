package io.kestra.plugin.digitalocean.droplet;

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
import java.util.LinkedHashMap;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Run a power or snapshot action on a DigitalOcean droplet",
    description = """
        Triggers an asynchronous droplet action: POWER_ON, POWER_OFF, REBOOT, or SNAPSHOT. The \
        DigitalOcean API processes actions asynchronously; this task only reports the action's initial \
        status, it does not wait for completion. Use io.kestra.plugin.digitalocean.droplet.Resize to \
        change a droplet's size.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Power off a droplet, then take a named snapshot",
            full = true,
            code = """
                id: digitalocean_droplet_action
                namespace: company.team

                tasks:
                  - id: power_off
                    type: io.kestra.plugin.digitalocean.droplet.Action
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    dropletId: "3164444"
                    action: POWER_OFF
                  - id: snapshot
                    type: io.kestra.plugin.digitalocean.droplet.Action
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    dropletId: "3164444"
                    action: SNAPSHOT
                    name: "pre-migration-snapshot"
                """
        )
    }
)
public class Action extends AbstractDigitalOceanTask implements RunnableTask<AbstractDigitalOceanTask.ActionOutput> {

    @Schema(title = "Droplet ID", description = "Numeric identifier of the droplet to act on.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> dropletId;

    @Schema(title = "Action", description = "Action to run: POWER_ON, POWER_OFF, REBOOT, or SNAPSHOT.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<DropletAction> action;

    @Schema(title = "Snapshot name", description = "Name for the snapshot. Only used when action is SNAPSHOT; DigitalOcean generates a name when omitted.")
    @PluginProperty(group = "advanced")
    private Property<String> name;

    @Override
    public ActionOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rDropletId = requireRendered(runContext, dropletId, String.class, "dropletId");
        var rAction = requireRendered(runContext, action, DropletAction.class, "action");
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        var payload = new LinkedHashMap<String, Object>();
        payload.put("type", rAction.name().toLowerCase());

        if (rAction == DropletAction.SNAPSHOT) {
            runContext.render(name).as(String.class).ifPresent(v -> payload.put("name", v));
        }

        logger.info("Running DigitalOcean droplet action {} on droplet {}", rAction, rDropletId);

        var url = join(rBaseUrl, "v2/droplets/" + encodePathSegment(rDropletId) + "/actions");
        var requestBuilder = HttpRequest.builder()
            .uri(URI.create(url))
            .method("POST")
            .body(HttpRequest.JsonRequestBody.of(payload));

        var body = requestJson(runContext, options, rApiToken, requestBuilder);
        return toActionOutput(body);
    }
}
