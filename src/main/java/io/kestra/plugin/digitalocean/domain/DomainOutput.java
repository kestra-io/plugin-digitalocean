package io.kestra.plugin.digitalocean.domain;

import io.kestra.core.models.tasks.Output;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asInteger;
import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asString;

/** Shared output shape for {@link Get} and {@link Create}: both return the same domain zone JSON object. */
@Builder
@Getter
public class DomainOutput implements Output {

    @Schema(title = "Domain name", description = "The zone's fully qualified domain name, e.g. example.com.")
    private final String name;

    @Schema(title = "TTL", description = "Default time to live, in seconds, for records in this zone.")
    private final Integer ttl;

    @Schema(title = "Zone file", description = "Raw zone file content in BIND format, null right after creation until DigitalOcean generates it.")
    private final String zoneFile;

    public static DomainOutput from(Map<String, Object> domain) {
        return DomainOutput.builder()
            .name(asString(domain.get("name")))
            .ttl(asInteger(domain.get("ttl")))
            .zoneFile(asString(domain.get("zone_file")))
            .build();
    }
}
