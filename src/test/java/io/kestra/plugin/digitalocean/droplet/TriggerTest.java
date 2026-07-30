package io.kestra.plugin.digitalocean.droplet;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContextInitializer;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class TriggerTest extends AbstractDigitalOceanTest {

    @Inject
    RunContextInitializer runContextInitializer;

    private String triggerId;

    private String buildTriggerId() {
        return "trigger-droplet-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private Trigger buildTrigger(String baseUrl) {
        return Trigger.builder()
            .id(triggerId)
            .type(Trigger.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(baseUrl))
            .interval(Duration.ofMinutes(1))
            .build();
    }

    private ConditionContext conditionContext(Trigger trigger, TriggerContext triggerContext, String flowId) throws Exception {
        var flow = Flow.builder()
            .id(flowId)
            .namespace("company.team")
            .tenantId("test-tenant")
            .build();
        var baseRunContext = (DefaultRunContext) runContextFactory.of(flow, trigger);
        var runContext = runContextInitializer.forScheduler(baseRunContext, triggerContext, trigger);
        return ConditionContext.builder()
            .runContext(runContext)
            .flow(flow)
            .build();
    }

    private TriggerContext triggerContext(String flowId) {
        return TriggerContext.builder()
            .tenantId("test-tenant")
            .namespace("company.team")
            .flowId(flowId)
            .triggerId(triggerId)
            .date(ZonedDateTime.now())
            .build();
    }

    @Test
    void firstEvaluationStoresBaselineAndDoesNotFire(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        triggerId = buildTriggerId();
        stubGetJson("/v2/droplets", """
            {"droplets": [{"id": 1, "name": "existing", "status": "active", "region": {"slug": "nyc3"}, "created_at": "2024-01-01T00:00:00Z"}], "links": {"pages": {}}, "meta": {"total": 1}}
            """);

        var trigger = buildTrigger(wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext("test-flow");
        var condCtx = conditionContext(trigger, trigCtx, "test-flow");

        var result = trigger.evaluate(condCtx, trigCtx);

        assertThat("first evaluation must not fire", result.isEmpty(), is(true));
    }

    @Test
    void doesNotRefireWhenNoNewDroplet(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        triggerId = buildTriggerId();
        stubGetJson("/v2/droplets", """
            {"droplets": [{"id": 1, "name": "existing", "status": "active", "region": {"slug": "nyc3"}, "created_at": "2024-01-01T00:00:00Z"}], "links": {"pages": {}}, "meta": {"total": 1}}
            """);

        var trigger = buildTrigger(wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext("test-flow");
        var condCtx = conditionContext(trigger, trigCtx, "test-flow");

        trigger.evaluate(condCtx, trigCtx);
        var result = trigger.evaluate(condCtx, trigCtx);

        assertThat("trigger must not re-fire when no new droplet appeared", result.isEmpty(), is(true));
    }

    @Test
    void firesWhenNewDropletAppears(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        triggerId = buildTriggerId();
        stubFor(get(urlPathEqualTo("/v2/droplets"))
            .inScenario("new-droplet")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(okJson("""
                {"droplets": [{"id": 1, "name": "existing", "status": "active", "region": {"slug": "nyc3"}, "created_at": "2024-01-01T00:00:00Z"}], "links": {"pages": {}}, "meta": {"total": 1}}
                """))
            .willSetStateTo("baseline-set"));
        stubFor(get(urlPathEqualTo("/v2/droplets"))
            .inScenario("new-droplet")
            .whenScenarioStateIs("baseline-set")
            .willReturn(okJson("""
                {"droplets": [
                    {"id": 1, "name": "existing", "status": "active", "region": {"slug": "nyc3"}, "created_at": "2024-01-01T00:00:00Z"},
                    {"id": 2, "name": "new-droplet", "status": "new", "region": {"slug": "nyc3"}, "created_at": "2024-02-01T00:00:00Z"}
                ], "links": {"pages": {}}, "meta": {"total": 2}}
                """)));

        var trigger = buildTrigger(wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext("test-flow");
        var condCtx = conditionContext(trigger, trigCtx, "test-flow");

        trigger.evaluate(condCtx, trigCtx);
        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat("trigger must fire when a new droplet appears", result.isPresent(), is(true));
        @SuppressWarnings("unchecked")
        var triggerVars = (Map<String, Object>) result.get().getTrigger().getVariables();
        assertThat(triggerVars.get("id"), is(2L));
        assertThat(triggerVars.get("name"), is("new-droplet"));
    }
}
