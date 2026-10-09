package io.kestra.plugin.digitalocean.spaces.key;

import java.util.Locale;

public enum Permission {
    READ,
    READWRITE,
    FULLACCESS;

    String apiValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
