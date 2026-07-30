package io.kestra.plugin.digitalocean.domain.record;

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

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create a DigitalOcean DNS record",
    description = "Creates a new DNS record under a domain already managed by DigitalOcean."
)
@Plugin(
    examples = {
        @Example(
            title = "Create an A record pointing www to a droplet's IP",
            full = true,
            code = """
                id: digitalocean_create_record
                namespace: company.team

                tasks:
                  - id: create_record
                    type: io.kestra.plugin.digitalocean.domain.record.Create
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    domain: "example.com"
                    recordType: "A"
                    name: "www"
                    data: "104.131.186.241"
                    ttl: 3600
                """
        )
    }
)
public class Create extends AbstractDigitalOceanTask implements RunnableTask<DomainRecordOutput> {

    @Schema(title = "Domain", description = "Domain name to create the record under, e.g. example.com.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> domain;

    @Schema(title = "Record type", description = "One of A, AAAA, CNAME, MX, TXT, SRV, NS, or CAA.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> recordType;

    @Schema(title = "Record name", description = "Host name, alias, or service being defined by the record, relative to the domain, e.g. www.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> name;

    @Schema(title = "Record data", description = "Variable data depending on record type, e.g. an IP address for an A record.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> data;

    @Schema(title = "TTL", description = "Time to live for the record, in seconds. Defaults to 1800.")
    @Builder.Default
    @PluginProperty(group = "advanced")
    private Property<Integer> ttl = Property.ofValue(1800);

    @Schema(title = "Priority", description = "Priority, required for MX and SRV records.")
    @PluginProperty(group = "advanced")
    private Property<Integer> priority;

    @Schema(title = "Port", description = "Port, required for SRV records.")
    @PluginProperty(group = "advanced")
    private Property<Integer> port;

    @Schema(title = "Weight", description = "Weight, required for SRV records.")
    @PluginProperty(group = "advanced")
    private Property<Integer> weight;

    @Override
    public DomainRecordOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rDomain = runContext.render(domain).as(String.class).orElseThrow(() -> new IllegalArgumentException("domain is required"));
        var rType = runContext.render(recordType).as(String.class).orElseThrow(() -> new IllegalArgumentException("recordType is required"));
        var rName = runContext.render(name).as(String.class).orElseThrow(() -> new IllegalArgumentException("name is required"));
        var rData = runContext.render(data).as(String.class).orElseThrow(() -> new IllegalArgumentException("data is required"));
        var rTtl = runContext.render(ttl).as(Integer.class).orElse(1800);
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        var payload = new LinkedHashMap<String, Object>();
        payload.put("type", rType);
        payload.put("name", rName);
        payload.put("data", rData);
        payload.put("ttl", rTtl);
        runContext.render(priority).as(Integer.class).ifPresent(v -> payload.put("priority", v));
        runContext.render(port).as(Integer.class).ifPresent(v -> payload.put("port", v));
        runContext.render(weight).as(Integer.class).ifPresent(v -> payload.put("weight", v));

        logger.info("Creating DigitalOcean DNS record {} {} for domain {}", rType, rName, rDomain);

        var url = join(rBaseUrl, "v2/domains/" + encodePathSegment(rDomain) + "/records");
        var requestBuilder = HttpRequest.builder()
            .uri(URI.create(url))
            .method("POST")
            .body(HttpRequest.JsonRequestBody.of(payload));

        var body = requestJson(runContext, options, rApiToken, requestBuilder);
        return DomainRecordOutput.from(unwrap(body, "domain_record"));
    }
}
