package io.kestra.plugin.digitalocean.domain.record;

/**
 * DNS record types accepted by DigitalOcean's create-record endpoint. SOA is deliberately omitted: every
 * zone has exactly one automatically-managed SOA record and DigitalOcean's API does not allow creating one.
 */
public enum RecordType {
    A,
    AAAA,
    CAA,
    CNAME,
    MX,
    NS,
    SRV,
    TXT
}
