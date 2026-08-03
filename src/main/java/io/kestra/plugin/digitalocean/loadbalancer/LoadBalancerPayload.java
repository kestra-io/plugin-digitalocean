package io.kestra.plugin.digitalocean.loadbalancer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared request body builder for {@link Create} and {@link Update}: both send the same full configuration. */
final class LoadBalancerPayload {

    private LoadBalancerPayload() {
    }

    static Map<String, Object> build(String name, String region, List<ForwardingRule> forwardingRules, List<Long> dropletIds) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("name", name);
        payload.put("region", region);
        payload.put("forwarding_rules", forwardingRules.stream().map(ForwardingRule::toMap).toList());
        if (dropletIds != null && !dropletIds.isEmpty()) {
            payload.put("droplet_ids", dropletIds);
        }
        return payload;
    }
}
