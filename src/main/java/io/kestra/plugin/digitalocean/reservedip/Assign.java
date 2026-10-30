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
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Assign a DigitalOcean reserved IP to a Droplet",
    description = """
        Points a reserved IP at a Droplet, e.g. to fail over to a standby Droplet. The action runs \
        asynchronously: poll `reservedip.GetAction` until its status is completed."""
)
@Plugin(
    examples = {
        @Example(
            title = "Move a reserved IP to a standby Droplet and wait until it is done",
            full = true,
            code = """
                id: digitalocean_fail_over_reserved_ip
                namespace: company.team

                tasks:
                  - id: assign_ip
                    type: io.kestra.plugin.digitalocean.reservedip.Assign
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    reservedIp: 45.55.96.47
                    dropletId: "3164494"

                  - id: wait_for_assignment
                    type: io.kestra.plugin.core.flow.LoopUntil
                    condition: "{{ outputs.get_action.status != 'in-progress' }}"
                    checkFrequency:
                      interval: PT5S
                      maxDuration: PT5M
                    tasks:
                      - id: get_action
                        type: io.kestra.plugin.digitalocean.reservedip.GetAction
                        apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                        reservedIp: 45.55.96.47
                        actionId: "{{ outputs.assign_ip.actionId }}"
                """
        )
    }
)
public class Assign extends AbstractDigitalOceanTask implements RunnableTask<AbstractDigitalOceanTask.ActionOutput> {

    @Schema(title = "Reserved IP", description = "The reserved IPv4 address, e.g. 45.55.96.47.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> reservedIp;

    @Schema(title = "Droplet ID", description = "Numeric identifier of the Droplet to assign the reserved IP to.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> dropletId;

    @Override
    public ActionOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rReservedIp = requireRendered(runContext, reservedIp, String.class, "reservedIp");
        var rDropletId = requireRendered(runContext, dropletId, String.class, "dropletId");
        var dropletNumber = parseDropletId(rDropletId);
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        logger.info("Assigning DigitalOcean reserved IP {} to droplet {}", rReservedIp, rDropletId);

        var url = join(rBaseUrl, "v2/reserved_ips/" + encodePathSegment(rReservedIp) + "/actions");
        var requestBuilder = HttpRequest.builder()
            .uri(URI.create(url))
            .method("POST")
            .body(HttpRequest.JsonRequestBody.of(Map.of("type", "assign", "droplet_id", dropletNumber)));

        return toActionOutput(requestJson(runContext, options, rApiToken, requestBuilder));
    }

    private static Long parseDropletId(String dropletId) {
        try {
            return Long.valueOf(dropletId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("dropletId must be a numeric DigitalOcean droplet id, got '" + dropletId + "'", e);
        }
    }
}
