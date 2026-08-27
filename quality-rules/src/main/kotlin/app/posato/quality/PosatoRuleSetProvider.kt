package app.posato.quality

import com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.rule.engine.core.api.RuleSetId

internal const val POSATO_RULE_SET_ID = "posato"

public class PosatoRuleSetProvider : RuleSetProviderV3(RuleSetId(POSATO_RULE_SET_ID)) {
    override fun getRuleProviders(): Set<RuleProvider> = setOf(
        RuleProvider { RightHandSideOnAssignmentLineRule() },
    )
}
