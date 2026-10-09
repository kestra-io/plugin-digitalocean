package io.kestra.plugin.digitalocean.spaces.key;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.VoidOutput;
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
    title = "Delete a DigitalOcean Spaces access key",
    description = "Permanently revokes a Spaces access key. This cannot be undone; anything still using the key loses access to the buckets."
)
@Plugin(
    examples = {
        @Example(
            title = "Revoke an access key",
            full = true,
            code = """
                id: digitalocean_delete_spaces_key
                namespace: company.team

                tasks:
                  - id: delete_key
                    type: io.kestra.plugin.digitalocean.spaces.key.Delete
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    accessKey: DO00EXAMPLEACCESSKEY
                """
        )
    }
)
public class Delete extends AbstractDigitalOceanTask implements RunnableTask<VoidOutput> {

    @Schema(title = "Access key ID", description = "ID of the access key to delete.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> accessKey;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rAccessKey = requireRendered(runContext, accessKey, String.class, "accessKey");
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        logger.info("Deleting DigitalOcean Spaces access key {}", rAccessKey);

        var url = join(rBaseUrl, "v2/spaces/keys/" + encodePathSegment(rAccessKey));
        var requestBuilder = HttpRequest.builder().uri(URI.create(url)).method("DELETE");
        request(runContext, options, rApiToken, requestBuilder, String.class);
        return null;
    }
}
