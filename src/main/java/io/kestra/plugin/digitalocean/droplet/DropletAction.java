package io.kestra.plugin.digitalocean.droplet;

/**
 * Actions supported by {@link Resize}. RESIZE requires {@code size}; the others act on the droplet's
 * power state or take a snapshot without needing any further property.
 */
public enum DropletAction {
    RESIZE,
    POWER_ON,
    POWER_OFF,
    REBOOT,
    SNAPSHOT
}
