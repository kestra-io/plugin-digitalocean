package io.kestra.plugin.digitalocean.reservedip;

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
    title = "Get a DigitalOcean reserved IP action",
    description = "Reads the current status of an action run on a reserved IP, such as the one returned by `reservedip.Assign` or `reservedip.Unassign`: in-progress, completed, or errored."
)
@Plugin(
    examples = {
        @Example(
            title = "Check the status of a reserved IP action",
            full = true,
            code = """
                id: digitalocean_reserved_ip_action_status
                namespace: company.team

                tasks:
                  - id: get_action
                    type: io.kestra.plugin.digitalocean.reservedip.GetAction
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    reservedIp: 45.55.96.47
                    actionId: "72531856"
                  - id: log_status
                    type: io.kestra.plugin.core.log.Log
                    message: "The {{ outputs.get_action.type }} action is {{ outputs.get_action.status }}"
                """
        )
    }
)
public class GetAction extends AbstractDigitalOceanTask implements RunnableTask<AbstractDigitalOceanTask.ActionOutput> {

    @Schema(title = "Reserved IP", description = "The reserved IPv4 address the action ran on.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> reservedIp;

    @Schema(title = "Action ID", description = "Numeric identifier of the action, e.g. the actionId output of Assign or Unassign.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> actionId;

    @Override
    public ActionOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rReservedIp = requireRendered(runContext, reservedIp, String.class, "reservedIp");
        var rActionId = requireRendered(runContext, actionId, String.class, "actionId");
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        var url = join(rBaseUrl, "v2/reserved_ips/" + encodePathSegment(rReservedIp) + "/actions/" + encodePathSegment(rActionId));
        var requestBuilder = HttpRequest.builder().uri(URI.create(url)).method("GET");
        var output = toActionOutput(requestJson(runContext, options, rApiToken, requestBuilder));

        logger.info("Action {} on DigitalOcean reserved IP {} is {}", rActionId, rReservedIp, output.getStatus());
        return output;
    }
}
