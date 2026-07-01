package com.aicoding.ai.harness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessPolicyEngineTest {

    private final HarnessPolicyEngine engine = new HarnessPolicyEngine();

    @Test
    void keepsQueriesReadOnly() {
        HarnessPolicyEngine.WorkflowPolicy policy = engine.classify("解释一下登录流程");

        assertThat(policy.intent()).isEqualTo(HarnessPolicyEngine.Intent.QUERY);
        assertThat(policy.requiredGates()).containsExactly("workspace-boundary", "read-only");
    }

    @Test
    void appliesFullGatesToHighRiskFeatures() {
        HarnessPolicyEngine.WorkflowPolicy policy = engine.classify("修改鉴权并完成数据库迁移");

        assertThat(policy.intent()).isEqualTo(HarnessPolicyEngine.Intent.FEATURE);
        assertThat(policy.risk()).isEqualTo(HarnessPolicyEngine.Risk.HIGH);
        assertThat(policy.requiredGates()).contains("compile", "unit-test", "integration-test", "evidence");
    }
}
