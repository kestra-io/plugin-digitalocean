package io.kestra.plugin.digitalocean.domain.record;

import io.kestra.core.models.tasks.Output;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asInteger;
import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asLong;
import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asString;

/** Shared output shape for {@link Get} and {@link Create}: both return the same DNS record JSON object. */
@Builder
@Getter
public class DomainRecordOutput implements Output {

    @Schema(title = "Record ID")
    private final Long id;

    @Schema(title = "Record type", description = "One of A, AAAA, CNAME, MX, TXT, SRV, NS, or CAA.")
    private final String type;

    @Schema(title = "Record name", description = "Host name, alias, or service being defined by the record, relative to the domain.")
    private final String name;

    @Schema(title = "Record data", description = "Variable data depending on record type, e.g. an IP address for an A record.")
    private final String data;

    @Schema(title = "TTL", description = "Time to live for the record, in seconds.")
    private final Integer ttl;

    public static DomainRecordOutput from(Map<String, Object> record) {
        return DomainRecordOutput.builder()
            .id(asLong(record.get("id")))
            .type(asString(record.get("type")))
            .name(asString(record.get("name")))
            .data(asString(record.get("data")))
            .ttl(asInteger(record.get("ttl")))
            .build();
    }
}
