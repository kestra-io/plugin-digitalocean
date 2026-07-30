package io.kestra.plugin.digitalocean.droplet;

import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.models.triggers.TriggerService;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asLong;
import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asMap;
import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asString;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Fire an execution when a new DigitalOcean droplet appears",
    description = """
        Polls the account's droplet list at each interval and fires once per newly-appeared droplet id. \
        The first evaluation only establishes the baseline of existing droplet ids and does not fire. \
        Only one new droplet is reported per poll; if several droplets appear between polls, the rest are \
        reported on the following polls.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "React whenever a new droplet is created on the account",
            full = true,
            code = """
                id: digitalocean_on_new_droplet
                namespace: company.team

                triggers:
                  - id: on_new_droplet
                    type: io.kestra.plugin.digitalocean.droplet.Trigger
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    interval: PT5M

                tasks:
                  - id: handle_new_droplet
                    type: io.kestra.plugin.core.log.Log
                    message: "New droplet: {{ trigger.name }} ({{ trigger.id }}) in {{ trigger.region }}"
                """
        )
    }
)
public class Trigger extends AbstractTrigger implements PollingTriggerInterface, TriggerOutput<Trigger.Output> {

    @Schema(
        title = "DigitalOcean API token",
        description = "Personal access token used to authenticate against the DigitalOcean API v2, sent as " +
            "`Authorization: Bearer <token>`. Create one (prefixed `dop_v1_`) in the DigitalOcean control " +
            "panel under API > Tokens, and store it as a Kestra secret."
    )
    @NotNull
    @PluginProperty(group = "connection", secret = true)
    @ToString.Exclude
    private Property<String> apiToken;

    @Schema(
        title = "DigitalOcean API base URL",
        description = "Base endpoint for all DigitalOcean API calls. Defaults to `" + AbstractDigitalOceanTask.DEFAULT_BASE_URL + "`."
    )
    @NotNull
    @Builder.Default
    @PluginProperty(group = "connection")
    private Property<String> baseUrl = Property.ofValue(AbstractDigitalOceanTask.DEFAULT_BASE_URL);

    @Schema(
        title = "HTTP client options",
        description = "Optional HTTP configuration (timeouts, proxy, SSL) applied to every DigitalOcean API call."
    )
    @PluginProperty(group = "advanced")
    private HttpConfiguration options;

    @Schema(
        title = "How often to check for new droplets",
        description = "ISO-8601 duration. Defaults to PT5M."
    )
    @PluginProperty(group = "reliability")
    @Builder.Default
    private Duration interval = Duration.ofMinutes(5);

    @Override
    public Duration getInterval() {
        return interval;
    }

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        var runContext = conditionContext.getRunContext();
        var logger = runContext.logger();

        var rApiToken = AbstractDigitalOceanTask.renderApiToken(runContext, apiToken);
        var rBaseUrl = AbstractDigitalOceanTask.renderBaseUrl(runContext, baseUrl);

        logger.debug("Polling DigitalOcean droplets for new arrivals");

        var currentDroplets = AbstractDigitalOceanTask.fetchAllPages(runContext, options, rApiToken, rBaseUrl, "v2/droplets", 200, "droplets").items();

        var currentIds = new LinkedHashSet<String>();
        for (var droplet : currentDroplets) {
            currentIds.add(asString(droplet.get("id")));
        }

        var kv = runContext.namespaceKv(context.getNamespace());
        // Refreshed every poll while the trigger is active, so a 10x-interval TTL never expires a live
        // baseline but ages out an orphaned entry a few polls after the trigger stops.
        var ttl = interval.multipliedBy(10);
        var key = kvKey(context.getFlowId(), context.getTriggerId());

        var previous = kv.getValue(key);
        if (previous.isEmpty()) {
            logger.info("Establishing droplet baseline: {} existing droplet(s)", currentIds.size());
            persistSeenIds(kv, key, currentIds, ttl);
            return Optional.empty();
        }

        var previousIds = parseSeenIds(previous.get().value());
        var seen = new LinkedHashSet<>(previousIds);
        seen.retainAll(currentIds);

        Map<String, Object> newDroplet = null;
        for (var droplet : currentDroplets) {
            var id = asString(droplet.get("id"));
            if (!previousIds.contains(id)) {
                newDroplet = droplet;
                break;
            }
        }

        if (newDroplet == null) {
            persistSeenIds(kv, key, seen, ttl);
            return Optional.empty();
        }

        seen.add(asString(newDroplet.get("id")));
        persistSeenIds(kv, key, seen, ttl);

        var region = asMap(newDroplet.get("region"));
        var createdAt = asString(newDroplet.get("created_at"));

        logger.info("New DigitalOcean droplet detected: {} ({})", newDroplet.get("name"), newDroplet.get("id"));

        var output = Output.builder()
            .id(asLong(newDroplet.get("id")))
            .name(asString(newDroplet.get("name")))
            .region(region != null ? asString(region.get("slug")) : null)
            .status(asString(newDroplet.get("status")))
            .createdAt(createdAt != null ? Instant.parse(createdAt) : null)
            .build();

        return Optional.of(TriggerService.generateExecution(this, conditionContext, context, output));
    }

    /**
     * Length-prefixes each segment so a flowId/triggerId pair such as ("ab", "c") never collides with
     * ("a", "bc") onto the same KV key.
     */
    private static String kvKey(String flowId, String triggerId) {
        return "digitalocean_droplet_trigger_" + flowId.length() + "_" + flowId + "_" + triggerId.length() + "_" + triggerId;
    }

    private static Set<String> parseSeenIds(Object raw) {
        if (!(raw instanceof String str) || str.isBlank()) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(Arrays.asList(str.split(",")));
    }

    private static void persistSeenIds(KVStore kv, String key, Set<String> ids, Duration ttl) throws Exception {
        kv.put(key, new KVValueAndMetadata(new KVMetadata(null, ttl), String.join(",", ids)));
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Droplet ID")
        private final Long id;

        @Schema(title = "Droplet name")
        private final String name;

        @Schema(title = "Region slug")
        private final String region;

        @Schema(title = "Droplet status at detection time")
        private final String status;

        @Schema(title = "Creation timestamp")
        private final Instant createdAt;
    }
}
