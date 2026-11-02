package io.kestra.plugin.digitalocean.reservedip;

import java.util.regex.Pattern;

/** encodePathSegment leaves dots unencoded, so values like `..` would otherwise reach the URL path and surface as a confusing 404. */
final class ReservedIpValidation {

    private static final Pattern IPV4 = Pattern.compile("(?:(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)");

    private ReservedIpValidation() {
    }

    static String requireIpv4(String reservedIp) {
        if (!IPV4.matcher(reservedIp).matches()) {
            throw new IllegalArgumentException("reservedIp must be an IPv4 address such as 45.55.96.47, got '" + reservedIp + "'");
        }
        return reservedIp;
    }

    static Long parseActionId(String actionId) {
        try {
            return Long.valueOf(actionId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("actionId must be a numeric DigitalOcean action id, got '" + actionId + "'", e);
        }
    }
}
