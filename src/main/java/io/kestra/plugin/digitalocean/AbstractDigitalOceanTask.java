package io.kestra.plugin.digitalocean;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared authentication, HTTP plumbing, pagination, and error handling for all DigitalOcean tasks.
 * {@code droplet.Trigger} extends AbstractTrigger (not Task) so it cannot extend this class; it reuses
 * the static helpers below instead, the same way plugin-metaplane's trigger reuses AbstractMetaplaneTask.
 */
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractDigitalOceanTask extends Task {

    public static final String DEFAULT_BASE_URL = "https://api.digitalocean.com";

    protected static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    @Schema(
        title = "DigitalOcean API token",
        description = "Personal access token used to authenticate against the DigitalOcean API v2, sent as " +
            "`Authorization: Bearer <token>`. Create one (prefixed `dop_v1_`) in the DigitalOcean control " +
            "panel under API > Tokens, and store it as a Kestra secret."
    )
    @NotNull
    @PluginProperty(secret = true, group = "connection")
    @ToString.Exclude
    protected Property<String> apiToken;

    @Schema(
        title = "DigitalOcean API base URL",
        description = "Base endpoint for all DigitalOcean API calls. Defaults to `" + DEFAULT_BASE_URL + "`."
    )
    @NotNull
    @Builder.Default
    @PluginProperty(group = "connection")
    protected Property<String> baseUrl = Property.ofValue(DEFAULT_BASE_URL);

    @Schema(
        title = "HTTP client options",
        description = "Optional HTTP configuration (timeouts, proxy, SSL) applied to every DigitalOcean API call."
    )
    @PluginProperty(group = "advanced")
    protected HttpConfiguration options;

    protected String renderApiToken(RunContext runContext) throws IllegalVariableEvaluationException {
        return renderApiToken(runContext, this.apiToken);
    }

    protected String renderBaseUrl(RunContext runContext) throws IllegalVariableEvaluationException {
        return renderBaseUrl(runContext, this.baseUrl);
    }

    public static String renderApiToken(RunContext runContext, Property<String> apiToken) throws IllegalVariableEvaluationException {
        return runContext.render(apiToken).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("apiToken is required")
        );
    }

    public static String renderBaseUrl(RunContext runContext, Property<String> baseUrl) throws IllegalVariableEvaluationException {
        return runContext.render(baseUrl).as(String.class).orElse(DEFAULT_BASE_URL);
    }

    /**
     * Renders a required property and fails with a message naming the property instead of an opaque
     * NoSuchElementException, replacing the repeated
     * {@code runContext.render(x).as(T.class).orElseThrow(() -> new IllegalArgumentException("x is required"))}
     * pattern used across every task.
     */
    public static <T> T requireRendered(RunContext runContext, Property<T> property, Class<T> type, String propertyName) throws IllegalVariableEvaluationException {
        return runContext.render(property).as(type).orElseThrow(
            () -> new IllegalArgumentException(propertyName + " is required")
        );
    }

    /**
     * Shared HTTP call logic: attaches the bearer token, executes the request, and on a non-2xx response
     * rewrites the failure into a clear, actionable message (never a raw stack trace). Defaults to
     * {@code Accept: application/json}, the shape every endpoint but the kubeconfig download returns.
     */
    public static <RES> HttpResponse<RES> request(
        RunContext runContext,
        HttpConfiguration options,
        String apiToken,
        HttpRequest.HttpRequestBuilder requestBuilder,
        Class<RES> responseType
    ) throws Exception {
        return request(runContext, options, apiToken, requestBuilder, responseType, "application/json");
    }

    /**
     * Same as {@link #request(RunContext, HttpConfiguration, String, HttpRequest.HttpRequestBuilder, Class)}
     * but with an explicit Accept header, for the rare endpoint (the DOKS kubeconfig download) that does
     * not return JSON. Opens and closes its own HttpClient for this single call.
     */
    public static <RES> HttpResponse<RES> request(
        RunContext runContext,
        HttpConfiguration options,
        String apiToken,
        HttpRequest.HttpRequestBuilder requestBuilder,
        Class<RES> responseType,
        String acceptHeader
    ) throws Exception {
        var configBuilder = options != null ? options.toBuilder() : HttpConfiguration.builder();
        try (var client = new HttpClient(runContext, configBuilder.build())) {
            return request(client, runContext, apiToken, requestBuilder, responseType, acceptHeader);
        }
    }

    /**
     * Core request logic shared by every overload above and by {@link #fetchAllPages}: attaches the bearer
     * token, executes the request against the given (already open) client, and on a non-2xx response
     * rewrites the failure into a clear, actionable message. Accepting the client lets a single task run,
     * especially {@code fetchAllPages}'s multi-page loop, reuse one HttpClient (and its connection pool)
     * instead of doing a fresh TLS handshake per page.
     */
    private static <RES> HttpResponse<RES> request(
        HttpClient client,
        RunContext runContext,
        String apiToken,
        HttpRequest.HttpRequestBuilder requestBuilder,
        Class<RES> responseType,
        String acceptHeader
    ) throws Exception {
        var request = requestBuilder
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", acceptHeader)
            .addHeader("Authorization", "Bearer " + apiToken)
            .build();

        try {
            var response = client.request(request, String.class);
            var rawBody = response.getBody();

            @SuppressWarnings("unchecked")
            RES parsedResponse = responseType == String.class
                ? (RES) rawBody
                : MAPPER.readValue(rawBody != null && !rawBody.isBlank() ? rawBody : "{}", responseType);

            return HttpResponse.<RES>builder()
                .request(request)
                .body(parsedResponse)
                .headers(response.getHeaders())
                .status(response.getStatus())
                .build();
        } catch (HttpClientResponseException e) {
            throw rewriteError(runContext.logger(), e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse the DigitalOcean API response: " + e.getMessage(), e);
        }
    }

    /**
     * Fetches a single JSON object body as a plain map, defaulting to an empty map on an empty response
     * instead of letting the caller hit a NullPointerException while unwrapping a resource key.
     */
    public static Map<String, Object> requestJson(
        RunContext runContext,
        HttpConfiguration options,
        String apiToken,
        HttpRequest.HttpRequestBuilder requestBuilder
    ) throws Exception {
        var body = request(runContext, options, apiToken, requestBuilder, Map.class).getBody();
        @SuppressWarnings("unchecked")
        var result = body != null ? (Map<String, Object>) body : new LinkedHashMap<String, Object>();
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requestJson(HttpClient client, RunContext runContext, String apiToken, HttpRequest.HttpRequestBuilder requestBuilder) throws Exception {
        var body = request(client, runContext, apiToken, requestBuilder, Map.class, "application/json").getBody();
        return body != null ? (Map<String, Object>) body : new LinkedHashMap<>();
    }

    private static HttpClientResponseException rewriteError(Logger logger, HttpClientResponseException e) {
        var response = e.getResponse();
        var status = response != null && response.getStatus() != null ? response.getStatus().getCode() : -1;
        var doError = extractDoError(response);
        var doMessage = doError != null ? doError.message() : null;

        logger.debug("DigitalOcean API call failed with HTTP {} (id={}): {}", status, doError != null ? doError.id() : null, e.getMessage());

        if (status == 401) {
            return new HttpClientResponseException(
                "DigitalOcean API returned HTTP 401: invalid or missing API token. Create a personal " +
                    "access token (prefixed dop_v1_) in the DigitalOcean control panel under API > Tokens and " +
                    "set it as apiToken" + (doMessage != null ? ": " + doMessage : "."),
                response, e
            );
        }

        if (status == 403) {
            return new HttpClientResponseException(
                "DigitalOcean API returned HTTP 403 (forbidden): " + (doMessage != null ? doMessage + ". " : "") +
                    "This usually means the token lacks the required scope for this action, or the resource is " +
                    "busy or in a state that does not allow it (for example a load balancer still processing a " +
                    "previous action). Verify the token scopes and the resource state, then retry.",
                response, e
            );
        }

        if (status == 404) {
            return new HttpClientResponseException(
                "DigitalOcean API returned HTTP 404: resource not found. Verify baseUrl (e.g. " + DEFAULT_BASE_URL +
                    ") and the id used in the request are correct." + (doMessage != null ? " " + doMessage : ""),
                response, e
            );
        }

        if (status == 429) {
            var retryAfter = response != null && response.getHeaders() != null
                ? response.getHeaders().firstValue("retry-after").orElse(null)
                : null;

            return new HttpClientResponseException(
                "DigitalOcean API rate limit hit (HTTP 429)." +
                    (retryAfter != null ? " Retry after " + retryAfter + " second(s)." : " Honor the retry-after response header before retrying.") +
                    (doMessage != null ? " " + doMessage : ""),
                response, e
            );
        }

        return new HttpClientResponseException(
            "DigitalOcean API request failed with HTTP " + status + ": " + (doMessage != null ? doMessage : e.getMessage()),
            response, e
        );
    }

    /** DigitalOcean's error body, e.g. {"id":"forbidden","message":"..."}. Both fields are optional and informational. */
    private record DoError(String id, String message) {
    }

    /**
     * Extracts DigitalOcean's error body id/message so {@link #rewriteError} can surface the real reason
     * for a failure (a load balancer busy processing a previous action, a token missing a scope, ...)
     * instead of assuming every 401/403 means a bad token. Never throws: a non-JSON or malformed body
     * (or none at all) just means no extra detail is available.
     */
    private static DoError extractDoError(HttpResponse<?> response) {
        if (response == null || !(response.getBody() instanceof byte[] bytes) || bytes.length == 0) {
            return null;
        }
        try {
            var node = MAPPER.readTree(bytes);
            var id = node.hasNonNull("id") ? node.get("id").asText() : null;
            var message = node.hasNonNull("message") ? node.get("message").asText() : null;
            return id != null || message != null ? new DoError(id, message) : null;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Joins a base URL and a path segment with exactly one slash, so a trailing slash on a
     * user-overridden baseUrl never produces a double slash in the request URI.
     */
    public static String join(String base, String path) {
        return base.replaceAll("/+$", "") + "/" + path.replaceAll("^/+", "");
    }

    /**
     * URL-encodes a single dynamic path segment (a droplet id, domain name, record id, ...) before it is
     * concatenated into a request path. {@link java.net.URLEncoder} encodes for form/query context and
     * turns a space into {@code +}, which is not valid in a URL path, so it is rewritten to {@code %20}.
     * Never apply this to a static path prefix (e.g. {@code "v2/droplets/"}) or to the slash separators.
     */
    public static String encodePathSegment(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Unwraps a singular resource key (e.g. "droplet", "kubernetes_cluster") from a DigitalOcean JSON
     * response, failing with a clear message instead of a raw NullPointerException if the API's response
     * shape ever changes.
     */
    public static Map<String, Object> unwrap(Map<String, Object> body, String key) {
        var value = body != null ? body.get(key) : null;
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalStateException(
                "Unexpected response from the DigitalOcean API: expected a \"" + key + "\" object but got none."
            );
        }
        @SuppressWarnings("unchecked")
        var result = (Map<String, Object>) map;
        return result;
    }

    public static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public static Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    public static Integer asInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    /**
     * Enforces a numeric range at render time. jakarta.validation's {@code @Min}/{@code @Max} annotations
     * cannot be placed directly on a {@code Property<Integer>} or {@code Property<Long>} field: the
     * built-in constraint validators only support {@link Number} and {@link CharSequence}, so Kestra's
     * {@code ModelValidator} throws {@code UnexpectedTypeException} the moment any flow using the task is
     * validated, regardless of the actual value. Bounds on a {@code Property<...>} field must therefore be
     * enforced here, once the rendered value is known.
     */
    public static int requireInRange(String fieldName, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(fieldName + " must be between " + min + " and " + max + ", got " + value);
        }
        return value;
    }

    public static long requireInRange(String fieldName, long value, long min, long max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(fieldName + " must be between " + min + " and " + max + ", got " + value);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    /** Generous ceiling on the number of pages a single fetchAllPages call will follow, see {@link #fetchAllPages}. */
    private static final int MAX_PAGES = 10_000;

    /**
     * Page-based pagination following DigitalOcean's `links.pages.next` URL, collecting the named array
     * (e.g. "droplets") across pages, and reading the grand total from `meta.total`.
     */
    public static FetchAllResult fetchAllPages(
        RunContext runContext,
        HttpConfiguration options,
        String apiToken,
        String baseUrl,
        String path,
        int perPage,
        String arrayKey
    ) throws Exception {
        return fetchAllPages(runContext, options, apiToken, baseUrl, path, perPage, arrayKey, MAX_PAGES);
    }

    /**
     * Package-visible overload taking an explicit page ceiling, so a test can exercise the cyclic-next
     * guard with a small, fast, deterministic value instead of the real {@link #MAX_PAGES}.
     */
    static FetchAllResult fetchAllPages(
        RunContext runContext,
        HttpConfiguration options,
        String apiToken,
        String baseUrl,
        String path,
        int perPage,
        String arrayKey,
        int maxPages
    ) throws Exception {
        var items = new ArrayList<Map<String, Object>>();
        long total = 0;
        String url = join(baseUrl, path) + (path.contains("?") ? "&" : "?") + "per_page=" + perPage;
        var pageCount = 0;

        // One HttpClient (and its connection pool) for every page of this call, instead of a fresh
        // client, and a fresh TLS handshake, per page.
        var configBuilder = options != null ? options.toBuilder() : HttpConfiguration.builder();
        try (var client = new HttpClient(runContext, configBuilder.build())) {
            while (url != null) {
                pageCount++;
                if (pageCount > maxPages) {
                    throw new IllegalStateException(
                        "DigitalOcean API pagination for \"" + path + "\" exceeded " + maxPages + " pages without " +
                            "reaching the end of links.pages.next. This looks like a cyclic or self-referential " +
                            "next link rather than a legitimate result set, so pagination was aborted instead of " +
                            "looping forever."
                    );
                }

                var requestBuilder = HttpRequest.builder().uri(URI.create(url)).method("GET");
                var body = requestJson(client, runContext, apiToken, requestBuilder);

                items.addAll(extractArray(body, arrayKey));

                var meta = asMap(body.get("meta"));
                if (meta != null && meta.get("total") instanceof Number number) {
                    total = number.longValue();
                }

                var links = asMap(body.get("links"));
                var pages = links != null ? asMap(links.get("pages")) : null;
                url = pages != null && pages.get("next") != null ? String.valueOf(pages.get("next")) : null;
            }
        }

        // Some DigitalOcean list endpoints (databases is one) return the items but leave meta.total
        // unset or zero. fetchAllPages already follows every links.pages.next page, so the collected
        // items list is the true complete result set: it can only ever under-report, never over-report.
        total = Math.max(total, items.size());

        return new FetchAllResult(items, total);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractArray(Map<String, Object> body, String arrayKey) {
        if (body == null || !(body.get(arrayKey) instanceof List<?> list)) {
            return List.of();
        }
        return (List<Map<String, Object>>) list;
    }

    public record FetchAllResult(List<Map<String, Object>> items, long total) {
    }

    /**
     * Shared FetchType handling for list tasks: FETCH keeps all items, FETCH_ONE keeps the first,
     * STORE writes the items to an ion file in internal storage, NONE keeps only the count.
     */
    protected static <T> FetchResult<T> fetchOutput(RunContext runContext, Property<FetchType> fetchType, List<T> items) throws Exception {
        return switch (runContext.render(fetchType).as(FetchType.class).orElse(FetchType.FETCH)) {
            case FETCH -> new FetchResult<>(items, null, null);
            case FETCH_ONE -> new FetchResult<>(null, items.isEmpty() ? null : items.getFirst(), null);
            case STORE -> new FetchResult<>(null, null, store(runContext, items));
            case NONE -> new FetchResult<>(null, null, null);
        };
    }

    private static <T> URI store(RunContext runContext, List<T> items) throws Exception {
        var tempFile = runContext.workingDir().createTempFile(".ion").toFile();

        try (var writer = Files.newBufferedWriter(tempFile.toPath(), StandardCharsets.UTF_8)) {
            FileSerde.writeAll(writer, Flux.fromIterable(items)).block();
        }

        return runContext.storage().putFile(tempFile);
    }

    public record FetchResult<T>(List<T> items, T first, URI uri) {
    }

    /**
     * Shared output shape for every List task: rows/row/uri per fetchType, plus the API's grand total.
     * Reused across all resource packages instead of a near-identical Output class per List task.
     */
    @Builder
    @Getter
    public static class PageOutput implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Fetched rows", description = "Populated when fetchType is FETCH.")
        private final List<Map<String, Object>> rows;

        @Schema(title = "First row", description = "Populated when fetchType is FETCH_ONE.")
        private final Map<String, Object> row;

        @Schema(title = "Stored data URI", description = "Populated when fetchType is STORE, points to an ion file in Kestra internal storage.")
        private final URI uri;

        @Schema(title = "Number of rows fetched", description = "Size of the fetched result set for this call.")
        private final Integer size;

        @Schema(title = "Total resources available", description = "Total number of resources reported by the DigitalOcean API (meta.total), regardless of fetchType.")
        private final Long total;
    }

    protected static PageOutput toPageOutput(RunContext runContext, Property<FetchType> fetchType, List<Map<String, Object>> items, long total) throws Exception {
        var result = fetchOutput(runContext, fetchType, items);
        var size = result.items() != null ? result.items().size() : (result.first() != null ? 1 : 0);

        return PageOutput.builder()
            .rows(result.items())
            .row(result.first())
            .uri(result.uri())
            .size(size)
            .total(total)
            .build();
    }

    /**
     * Shared output shape for DigitalOcean's async action responses (droplet resize, volume attach/detach):
     * both endpoints return the same `{"action": {...}}` envelope.
     */
    @Builder
    @Getter
    public static class ActionOutput implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Action ID", description = "Identifier of the asynchronous DigitalOcean action that was triggered.")
        private final Long actionId;

        @Schema(title = "Action status", description = "Status of the action at request time, one of in-progress, completed, or errored.")
        private final String status;

        @Schema(title = "Action type", description = "Type of the action that was triggered, e.g. resize, attach_volume.")
        private final String type;
    }

    protected static ActionOutput toActionOutput(Map<String, Object> body) {
        var action = unwrap(body, "action");
        return ActionOutput.builder()
            .actionId(asLong(action.get("id")))
            .status(asString(action.get("status")))
            .type(asString(action.get("type")))
            .build();
    }
}
