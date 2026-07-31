package io.kestra.plugin.digitalocean.droplet;

import io.kestra.core.exceptions.ResourceExpiredException;
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
import io.kestra.core.storages.kv.KVValue;
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
import org.slf4j.Logger;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

        This trigger is at-least-once: a droplet id missing from a single poll (a transient gap from \
        DigitalOcean's eventual consistency, or a short page) is tolerated for up to 3 consecutive polls \
        before it is dropped from the watermark, so it does not re-fire once the listing catches up. The \
        watermark is stored under a Kestra namespace KV key prefixed `digitalocean_droplet_trigger_`; do \
        not delete it manually, as doing so re-establishes the baseline and skips whatever is already on \
        the account at that point.
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

        var previous = getWatermarkValue(kv, key, logger);
        if (previous.isEmpty()) {
            logger.info("Establishing droplet baseline: {} existing droplet(s)", currentIds.size());
            persistWatermark(kv, key, freshWatermark(currentIds), ttl);
            return Optional.empty();
        }

        var watermark = parseWatermark(previous.get().value());

        Map<String, Object> newDroplet = null;
        for (var droplet : currentDroplets) {
            var id = asString(droplet.get("id"));
            if (!watermark.containsKey(id)) {
                newDroplet = droplet;
                break;
            }
        }

        var firedId = newDroplet != null ? asString(newDroplet.get("id")) : null;
        persistWatermark(kv, key, advanceWatermark(watermark, currentIds, firedId), ttl);

        if (newDroplet == null) {
            return Optional.empty();
        }

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

    /** An id is forgotten only once it has been absent from the listing for this many consecutive polls. */
    private static final int MAX_CONSECUTIVE_MISSES = 3;

    /**
     * A KV entry whose TTL has lapsed surfaces as {@link ResourceExpiredException} rather than an empty
     * {@link Optional}. Treat it the same as "no baseline yet" instead of letting it fail the whole
     * evaluation, so the trigger simply re-establishes a fresh baseline on the next poll.
     */
    private static Optional<KVValue> getWatermarkValue(KVStore kv, String key, Logger logger) throws IOException {
        try {
            return kv.getValue(key);
        } catch (ResourceExpiredException e) {
            logger.debug("Droplet watermark for key {} expired, re-establishing baseline", key);
            return Optional.empty();
        }
    }

    private static Map<String, Integer> freshWatermark(Set<String> ids) {
        var watermark = new LinkedHashMap<String, Integer>();
        for (var id : ids) {
            watermark.put(id, 0);
        }
        return watermark;
    }

    /**
     * Advances the watermark for the next poll. Only ids already known to the watermark are carried
     * forward here: an id still present in this poll's listing resets to 0 consecutive misses, an id
     * missing from this poll keeps its entry with an incremented miss count unless it has now reached
     * {@link #MAX_CONSECUTIVE_MISSES} in a row (dropped), and reportedId, the one id actually fired this
     * poll (or null if none fired), is added at 0 misses.
     *
     * This must NOT seed every currently-listed id: doing so would mark every other newly-appeared
     * droplet from the same poll as already-seen before it ever gets reported, silently losing it (this
     * trigger fires at most one new droplet per poll, see evaluate()).
     */
    private static Map<String, Integer> advanceWatermark(Map<String, Integer> watermark, Set<String> currentIds, String reportedId) {
        var updated = new LinkedHashMap<String, Integer>();
        for (var entry : watermark.entrySet()) {
            if (currentIds.contains(entry.getKey())) {
                updated.put(entry.getKey(), 0);
            } else if (entry.getValue() + 1 < MAX_CONSECUTIVE_MISSES) {
                updated.put(entry.getKey(), entry.getValue() + 1);
            }
        }
        if (reportedId != null) {
            updated.put(reportedId, 0);
        }
        return updated;
    }

    /**
     * Persisted as compact "id:misses,id:misses,..." pairs, e.g. "3164444:0,3164445:2". Also accepts the
     * older bare-id format ("3164444,3164445", no misses) from a watermark written before this trigger
     * tracked consecutive misses, parsing each bare id as 0 misses instead of dropping the whole entry
     * (which would otherwise treat every droplet as new and fire once, spuriously, right after upgrade).
     */
    private static Map<String, Integer> parseWatermark(Object raw) {
        var watermark = new LinkedHashMap<String, Integer>();
        if (!(raw instanceof String str) || str.isBlank()) {
            return watermark;
        }
        for (var entry : str.split(",")) {
            if (entry.isBlank()) {
                continue;
            }
            var parts = entry.split(":", 2);
            if (parts.length == 1) {
                watermark.put(parts[0], 0);
                continue;
            }
            try {
                watermark.put(parts[0], Integer.parseInt(parts[1]));
            } catch (NumberFormatException e) {
                // Malformed entry: skip it rather than fail evaluation.
            }
        }
        return watermark;
    }

    private static void persistWatermark(KVStore kv, String key, Map<String, Integer> watermark, Duration ttl) throws Exception {
        var value = watermark.entrySet().stream()
            .map(entry -> entry.getKey() + ":" + entry.getValue())
            .collect(Collectors.joining(","));
        kv.put(key, new KVValueAndMetadata(new KVMetadata(null, ttl), value));
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
