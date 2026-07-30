package io.kestra.plugin.digitalocean.volume;

import java.util.LinkedHashMap;
import java.util.Map;

/** Shared action request body builder for {@link Attach} and {@link Detach}: both post to the same actions endpoint. */
final class VolumeActionPayload {

    private VolumeActionPayload() {
    }

    static Map<String, Object> build(String type, Long dropletId, String region) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("type", type);
        payload.put("droplet_id", dropletId);
        if (region != null) {
            payload.put("region", region);
        }
        return payload;
    }

    /** DigitalOcean droplet ids are numeric; reject anything else with an actionable message instead of a raw NumberFormatException. */
    static Long parseDropletId(String dropletId) {
        try {
            return Long.valueOf(dropletId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "dropletId must be a numeric DigitalOcean droplet id, got '" + dropletId + "'", e
            );
        }
    }
}
