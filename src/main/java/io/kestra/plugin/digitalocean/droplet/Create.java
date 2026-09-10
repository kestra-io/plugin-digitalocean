package io.kestra.plugin.digitalocean.droplet;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.configurations.HttpConfiguration;
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
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create a DigitalOcean droplet",
    description = "Creates a new droplet (virtual machine) from a region, size, and image."
)
@Plugin(
    examples = {
        @Example(
            title = "Create a droplet in nyc3",
            full = true,
            code = """
                id: digitalocean_create_droplet
                namespace: company.team

                tasks:
                  - id: create_droplet
                    type: io.kestra.plugin.digitalocean.droplet.Create
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    name: "web-01"
                    region: "nyc3"
                    size: "s-1vcpu-1gb"
                    image: "ubuntu-22-04-x64"
                    tags:
                      - "web"
                  - id: log_ip
                    type: io.kestra.plugin.core.log.Log
                    message: "Droplet is up at {{ outputs.create_droplet.ip }}"
                """
        )
    }
)
public class Create extends AbstractDigitalOceanTask implements RunnableTask<DropletOutput> {

    @Schema(title = "Droplet name", description = "Human-readable name for the droplet, must be unique and a valid hostname.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> name;

    @Schema(title = "Region", description = "Datacenter region slug to create the droplet in, e.g. nyc3, ams3, sgp1.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> region;

    @Schema(title = "Size", description = "Droplet size slug, e.g. s-1vcpu-1gb.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> size;

    @Schema(title = "Image", description = "Image slug (e.g. ubuntu-22-04-x64) or numeric image ID to boot the droplet from.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> image;

    @Schema(title = "SSH keys", description = "Fingerprints or IDs of SSH keys already registered on the DigitalOcean account to install on the droplet.")
    @PluginProperty(group = "advanced")
    private Property<List<String>> sshKeys;

    @Schema(title = "Tags", description = "Tags to apply to the droplet.")
    @PluginProperty(group = "advanced")
    private Property<List<String>> tags;

    @Schema(title = "Enable backups", description = "Whether automatic backups should be enabled. Defaults to false.")
    @Builder.Default
    @PluginProperty(group = "advanced")
    private Property<Boolean> backups = Property.ofValue(false);

    @Schema(title = "Enable IPv6", description = "Whether an IPv6 address should be assigned. Defaults to false.")
    @Builder.Default
    @PluginProperty(group = "advanced")
    private Property<Boolean> ipv6 = Property.ofValue(false);

    @Schema(title = "User data", description = "Cloud-init user data script to run on first boot.")
    @PluginProperty(group = "advanced")
    private Property<String> userData;

    @Schema(
        title = "Wait for the droplet to be active",
        description = """
            Whether to poll the droplet until it reports status active before returning. DigitalOcean \
            only assigns the public IPv4 address once the droplet is active, so the ip output is null \
            while it is still starting up if this is disabled. Defaults to true."""
    )
    @Builder.Default
    @PluginProperty(group = "advanced")
    private Property<Boolean> wait = Property.ofValue(true);

    @Schema(
        title = "Wait timeout",
        description = """
            Maximum time to wait for the droplet to become active when wait is true, between 1 second \
            and 1 hour. Defaults to PT5M (5 minutes)."""
    )
    @Builder.Default
    @PluginProperty(group = "advanced")
    private Property<Duration> waitTimeout = Property.ofValue(Duration.ofMinutes(5));

    private static final long MIN_WAIT_TIMEOUT_SECONDS = 1;
    private static final long MAX_WAIT_TIMEOUT_SECONDS = 3600;

    /**
     * Fixed delay between two consecutive activation polls, see {@link #waitUntilActive}. Package-private
     * and non-final (instead of a {@code static final}) so tests can shrink it and avoid a real multi-second
     * sleep per run; production code always sees the 5000ms default.
     */
    static long pollIntervalMillis = 5000;

    @Override
    public DropletOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rName = requireRendered(runContext, name, String.class, "name");
        var rRegion = requireRendered(runContext, region, String.class, "region");
        var rSize = requireRendered(runContext, size, String.class, "size");
        var rImage = requireRendered(runContext, image, String.class, "image");
        var rSshKeys = runContext.render(sshKeys).asList(String.class);
        var rTags = runContext.render(tags).asList(String.class);
        var rBackups = runContext.render(backups).as(Boolean.class).orElse(false);
        var rIpv6 = runContext.render(ipv6).as(Boolean.class).orElse(false);
        var rWait = runContext.render(wait).as(Boolean.class).orElse(true);
        // Only render and range-check waitTimeout when it actually matters: a flow with wait: false must
        // not fail because of an out-of-range waitTimeout that will never be used.
        Duration rWaitTimeout = null;
        if (rWait) {
            var rWaitTimeoutSeconds = requireInRange(
                "waitTimeout",
                runContext.render(waitTimeout).as(Duration.class).orElse(Duration.ofMinutes(5)).toSeconds(),
                MIN_WAIT_TIMEOUT_SECONDS,
                MAX_WAIT_TIMEOUT_SECONDS
            );
            rWaitTimeout = Duration.ofSeconds(rWaitTimeoutSeconds);
        }
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        var payload = new LinkedHashMap<String, Object>();
        payload.put("name", rName);
        payload.put("region", rRegion);
        payload.put("size", rSize);
        payload.put("image", rImage);
        payload.put("backups", rBackups);
        payload.put("ipv6", rIpv6);
        if (!rSshKeys.isEmpty()) {
            payload.put("ssh_keys", rSshKeys);
        }
        if (!rTags.isEmpty()) {
            payload.put("tags", rTags);
        }
        runContext.render(userData).as(String.class).ifPresent(v -> payload.put("user_data", v));

        logger.info("Creating DigitalOcean droplet '{}' in {}", rName, rRegion);

        var url = join(rBaseUrl, "v2/droplets");
        var requestBuilder = HttpRequest.builder()
            .uri(URI.create(url))
            .method("POST")
            .body(HttpRequest.JsonRequestBody.of(payload));

        var body = requestJson(runContext, options, rApiToken, requestBuilder);
        var droplet = unwrap(body, "droplet");

        if (rWait && !"active".equals(asString(droplet.get("status")))) {
            droplet = waitUntilActive(runContext, rApiToken, rBaseUrl, asLong(droplet.get("id")), rWaitTimeout);
        }

        return DropletOutput.from(droplet);
    }

    /**
     * Polls GET /v2/droplets/{id} every 5s (or less, right before the deadline) until the droplet reports
     * status active or the timeout elapses. The caller only invokes this when the create response isn't
     * already active, so the "already active" case never issues an extra GET. Reuses a single HttpClient
     * across every poll of the loop instead of paying a fresh TLS handshake per iteration, the same
     * pattern as {@code AbstractDigitalOceanTask#fetchAllPages}.
     */
    private Map<String, Object> waitUntilActive(RunContext runContext, String apiToken, String baseUrl, Long dropletId, Duration timeout) throws Exception {
        var logger = runContext.logger();
        logger.info("Waiting up to {} for droplet {} to become active", timeout, dropletId);

        var deadline = Instant.now().plus(timeout);
        var url = join(baseUrl, "v2/droplets/" + dropletId);

        var configBuilder = options != null ? options.toBuilder() : HttpConfiguration.builder();
        try (var client = new HttpClient(runContext, configBuilder.build())) {
            while (true) {
                var requestBuilder = HttpRequest.builder().uri(URI.create(url)).method("GET");
                var body = requestJson(client, runContext, apiToken, requestBuilder);
                var droplet = unwrap(body, "droplet");
                var status = asString(droplet.get("status"));

                if ("active".equals(status)) {
                    var ip = DropletOutput.publicIpv4(droplet);
                    if (ip == null) {
                        logger.warn("Droplet {} is active but has no public IPv4 address; ip output will be null", dropletId);
                    } else {
                        logger.info("Droplet {} is active with IP {}", dropletId, ip);
                    }
                    return droplet;
                }

                var now = Instant.now();
                if (!now.isBefore(deadline)) {
                    throw new IllegalStateException(
                        "Droplet " + dropletId + " did not become active within " + timeout + " (last status: " + status +
                            "). Increase waitTimeout, or set wait: false and poll separately."
                    );
                }

                // Never sleep past the deadline: the last poll of the loop must fire as close to it as
                // possible instead of always waiting a full pollIntervalMillis first.
                var remainingMillis = Duration.between(now, deadline).toMillis();
                var sleepMillis = Math.min(pollIntervalMillis, remainingMillis);
                try {
                    Thread.sleep(sleepMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }
}
