package pl.slogerski.heartistica;

final class HeartVisibilityRules {
    private HeartVisibilityRules() {}

    static boolean matches(float health, float maxHealth, float absorption, boolean numeric,
                           boolean onlyAbsorption) {
        if (onlyAbsorption) return numeric || HeartDisplayState.finitePositive(absorption) > 0;
        return isDamaged(health, maxHealth) || HeartDisplayState.finitePositive(absorption) > 0;
    }

    static boolean isDamaged(float health, float maxHealth) {
        return Float.isFinite(health) && Float.isFinite(maxHealth) && health > 0 && maxHealth - health >= 1;
    }
}
