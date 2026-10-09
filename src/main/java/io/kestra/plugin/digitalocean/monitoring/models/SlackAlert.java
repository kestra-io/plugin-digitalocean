package io.kestra.plugin.digitalocean.monitoring.models;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;

@Builder
@Getter
@Jacksonized
public class SlackAlert {

    @NotNull
    @Schema(title = "Slack incoming webhook URL")
    private final String url;

    @NotNull
    @Schema(title = "Slack channel name")
    private final String channel;

    public Map<String, Object> toMap() {
        return Map.of(
            "url", url,
            "channel", channel
        );
    }

    public static SlackAlert from(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        return SlackAlert.builder()
            .url(map.get("url") != null ? map.get("url").toString() : null)
            .channel(map.get("channel") != null ? map.get("channel").toString() : null)
            .build();
    }
}
