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
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;

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
        return DropletOutput.from(unwrap(body, "droplet"));
    }
}
