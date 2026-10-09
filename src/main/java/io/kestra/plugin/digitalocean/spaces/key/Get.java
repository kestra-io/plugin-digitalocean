package io.kestra.plugin.digitalocean.spaces.key;

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
    title = "Get a DigitalOcean Spaces access key",
    description = "Reads a Spaces access key's name and bucket grants. DigitalOcean never returns the secret key again after creation."
)
@Plugin(
    examples = {
        @Example(
            title = "Get an access key and log its grants",
            full = true,
            code = """
                id: digitalocean_get_spaces_key
                namespace: company.team

                tasks:
                  - id: get_key
                    type: io.kestra.plugin.digitalocean.spaces.key.Get
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    accessKey: DO00EXAMPLEACCESSKEY
                  - id: log_grants
                    type: io.kestra.plugin.core.log.Log
                    message: "Key {{ outputs.get_key.name }} has grants {{ outputs.get_key.grants }}"
                """
        )
    }
)
public class Get extends AbstractDigitalOceanTask implements RunnableTask<KeyOutput> {

    @Schema(title = "Access key ID", description = "ID of the access key to read.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> accessKey;

    @Override
    public KeyOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rAccessKey = requireRendered(runContext, accessKey, String.class, "accessKey");
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        logger.info("Fetching DigitalOcean Spaces access key {}", rAccessKey);

        var url = join(rBaseUrl, "v2/spaces/keys/" + encodePathSegment(rAccessKey));
        var requestBuilder = HttpRequest.builder().uri(URI.create(url)).method("GET");
        var body = requestJson(runContext, options, rApiToken, requestBuilder);

        return KeyOutput.from(unwrap(body, "key"));
    }
}
