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
    title = "Unassign a DigitalOcean reserved IP",
    description = "Detaches a reserved IP from its Droplet. The IP stays reserved on the account. The action runs asynchronously: poll `reservedip.GetAction` until its status is completed."
)
@Plugin(
    examples = {
        @Example(
            title = "Unassign a reserved IP",
            full = true,
            code = """
                id: digitalocean_unassign_reserved_ip
                namespace: company.team

                tasks:
                  - id: unassign_ip
                    type: io.kestra.plugin.digitalocean.reservedip.Unassign
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    reservedIp: 45.55.96.47
                """
        )
    }
)
public class Unassign extends AbstractDigitalOceanTask implements RunnableTask<AbstractDigitalOceanTask.ActionOutput> {

    @Schema(title = "Reserved IP", description = "The reserved IPv4 address, e.g. 45.55.96.47.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> reservedIp;

    @Override
    public ActionOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rReservedIp = requireRendered(runContext, reservedIp, String.class, "reservedIp");
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        logger.info("Unassigning DigitalOcean reserved IP {}", rReservedIp);

        var url = join(rBaseUrl, "v2/reserved_ips/" + encodePathSegment(rReservedIp) + "/actions");
        var requestBuilder = HttpRequest.builder()
            .uri(URI.create(url))
            .method("POST")
            .body(HttpRequest.JsonRequestBody.of(Map.of("type", "unassign")));

        return toActionOutput(requestJson(runContext, options, rApiToken, requestBuilder));
    }
}
