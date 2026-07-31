package io.kestra.plugin.digitalocean;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AbstractDigitalOceanTaskTest extends AbstractDigitalOceanTest {

    @Test
    void fetchAllPagesAbortsOnACyclicNextLinkInsteadOfLoopingForever(WireMockRuntimeInfo wireMockRuntimeInfo) {
        // links.pages.next always points back to the same page: a self-referential/cyclic pagination
        // response that must never be followed indefinitely.
        var baseUrl = wireMockRuntimeInfo.getHttpBaseUrl();
        stubGetJson("/v2/widgets", """
            {"widgets": [{"id": 1}], "links": {"pages": {"next": "%s/v2/widgets?page=2&per_page=1"}}, "meta": {"total": 1}}
            """.formatted(baseUrl));

        var runContext = runContext();
        var ex = assertThrows(IllegalStateException.class, () -> AbstractDigitalOceanTask.fetchAllPages(
            runContext, null, "test-token", baseUrl, "v2/widgets", 1, "widgets", 3
        ));

        assertThat(ex.getMessage(), containsString("v2/widgets"));
        assertThat(ex.getMessage(), containsString("exceeded 3 pages"));
    }
}
