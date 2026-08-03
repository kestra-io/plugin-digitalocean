package io.kestra.plugin.digitalocean.droplet;

/**
 * Non-resize actions supported by {@link Action}: power state changes and snapshots. Resizing is a
 * separate task ({@link Resize}) since it needs a size and behaves differently from these fire-and-forget
 * actions.
 */
public enum DropletAction {
    POWER_ON,
    POWER_OFF,
    REBOOT,
    SNAPSHOT
}
