package pl.slogerski.heartistica;

final class HeartVisibilityRules {
    private HeartVisibilityRules() {}

    static boolean matches(float health, float maxHealth, float absorption, boolean numeric,
                           boolean onlyAbsorption, boolean onlyDamaged) {
        if (onlyAbsorption) return numeric || HeartDisplayState.finitePositive(absorption) > 0;
        if (!onlyDamaged) return true;
        return Float.isFinite(health) && Float.isFinite(maxHealth) && Float.isFinite(absorption)
                && health > 0 && maxHealth > health && absorption <= 0;
    }
}
