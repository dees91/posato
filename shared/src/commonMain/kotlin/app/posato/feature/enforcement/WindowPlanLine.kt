package app.posato.feature.enforcement

/**
 * The text form of window plans in the helpers' apply line and in Android's saved pause (TARGETS-013, ADR 0010
 * amendment): `host=until:period:length;...` per plan, plans separated by commas.
 */
public object WindowPlanLine {
    public fun encode(plans: List<DomainWindowPlan>): String {
        TODO("TARGETS-013 phase 4")
    }

    public fun decode(text: String): List<DomainWindowPlan>? {
        TODO("TARGETS-013 phase 4")
    }
}
