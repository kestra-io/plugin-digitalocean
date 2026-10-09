package io.kestra.plugin.digitalocean.monitoring.models;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Builder
@Getter
@Jacksonized
public class AlertChannels {

    @Schema(title = "Email notification recipients")
    private final List<String> email;

    @Schema(title = "Slack notification channels")
    private final List<SlackAlert> slack;

    public Map<String, Object> toMap() {
        var map = new HashMap<String, Object>();
        if (email != null) {
            map.put("email", email);
        }
        if (slack != null) {
            map.put("slack", slack.stream().map(SlackAlert::toMap).toList());
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    public static AlertChannels from(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        var emailList = map.get("email") instanceof List<?> list
            ? list.stream().map(String::valueOf).toList()
            : null;

        var slackList = map.get("slack") instanceof List<?> list
            ? list.stream()
                .filter(item -> item instanceof Map)
                .map(item -> SlackAlert.from((Map<String, Object>) item))
                .toList()
            : null;

        return AlertChannels.builder()
            .email(emailList)
            .slack(slackList)
            .build();
    }
}
