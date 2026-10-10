package io.kestra.plugin.digitalocean;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.http.client.configurations.TimeoutConfiguration;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
@WireMockTest
public abstract class AbstractDigitalOceanTest {

    @Inject
    protected RunContextFactory runContextFactory;

    protected RunContext runContext() {
        return runContextFactory.of();
    }

    protected static void stubGetJson(String path, String jsonBody) {
        stubFor(get(urlPathEqualTo(path)).willReturn(okJson(jsonBody)));
    }

    protected static void stubPostJson(String path, int status, String jsonBody) {
        stubFor(post(urlPathEqualTo(path)).willReturn(aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(jsonBody)));
    }

    protected static void stubStatus(String path, int status, String body) {
        stubFor(get(urlPathEqualTo(path)).willReturn(aResponse().withStatus(status).withBody(body)));
    }

    protected static void stubStatusWithHeader(String path, int status, String body, String headerName, String headerValue) {
        stubFor(get(urlPathEqualTo(path)).willReturn(aResponse().withStatus(status).withHeader(headerName, headerValue).withBody(body)));
    }

    protected static void verifyBearer(RequestPatternBuilder request, String token) {
        verify(request.withHeader("Authorization", equalTo("Bearer " + token)));
    }

    /**
     * Options shaped like the flow reproducer: {@code options.timeout.readIdleTimeout}.
     * The deprecated top-level {@code readIdleTimeout} field is ignored by the HTTP client.
     */
    protected static HttpConfiguration readIdleTimeout(Duration timeout) {
        return HttpConfiguration.builder()
            .timeout(TimeoutConfiguration.builder()
                .readIdleTimeout(Property.ofValue(timeout))
                .build())
            .build();
    }

    protected static void assertOnlyBearer(LoggedRequest request, String token) {
        assertThat(request.header("Authorization").values(), is(List.of("Bearer " + token)));
    }
}
