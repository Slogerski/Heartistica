package pl.slogerski.heartistica;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;

final class HeartDisplayState {
    static final int MAX_HEARTS = 100;
    static final int HEARTS_PER_ROW = 10;
    static final int PERSISTENT_EMPTY_HEARTS = 20;
    static final long EMPTY_DURATION_MS = 5_000;
    static final byte HIDDEN = -1, FULL = 0, HALF = 1, EMPTY = 2, GOLD = 3, GOLD_HALF = 4;

    final byte[] sprites = new byte[MAX_HEARTS * 2];
    private final long[] emptyUntil = new long[MAX_HEARTS];
    private boolean initialized;
    private float previousHealth, previousMax, previousAbsorption;
    private boolean previousNumeric, previousOnlyAbsorption;
    private boolean previousShowHealth;
    private int previousFilled, previousCapacity;
    private long nextExpiry = Long.MAX_VALUE;
    int slots;
    int anchorSlots;
    String label = "";
    String anchorLabel = "";
    boolean goldLabel;

    boolean update(float health, float maxHealth, float absorption,
                   boolean numeric, boolean onlyAbsorption, long now) {
        boolean showHealth = !onlyAbsorption && HeartVisibilityRules.isDamaged(health, maxHealth);
        health = finitePositive(health);
        maxHealth = finitePositive(maxHealth);
        absorption = finitePositive(absorption);
        if (initialized && health == previousHealth && maxHealth == previousMax
                && absorption == previousAbsorption && numeric == previousNumeric
                && onlyAbsorption == previousOnlyAbsorption && showHealth == previousShowHealth
                && now < nextExpiry) return false;

        int healthUnits = halfUnits(health);
        int filled = (healthUnits + 1) / 2;
        int capacity = Math.max(filled, Math.max(1, (halfUnits(maxHealth) + 1) / 2));
        if (initialized && (health != previousHealth || maxHealth != previousMax)) {
            for (int i = PERSISTENT_EMPTY_HEARTS; i < MAX_HEARTS; i++) {
                if (i >= capacity || i < filled) emptyUntil[i] = 0;
                else if (i < previousFilled && i < previousCapacity) {
                    emptyUntil[i] = now + EMPTY_DURATION_MS;
                }
            }
        }
        previousHealth = health;
        previousMax = maxHealth;
        previousAbsorption = absorption;
        previousNumeric = numeric;
        previousOnlyAbsorption = onlyAbsorption;
        previousShowHealth = showHealth;
        previousFilled = filled;
        previousCapacity = capacity;
        initialized = true;

        nextExpiry = Long.MAX_VALUE;
        slots = 0;
        anchorSlots = Math.min(HEARTS_PER_ROW, capacity);
        goldLabel = onlyAbsorption || absorption > 0;
        if (numeric) {
            if (!onlyAbsorption && !showHealth && absorption == 0) {
                label = "";
                anchorLabel = "";
                return true;
            }
            label = onlyAbsorption ? format(absorption) + "/?"
                    : format((double) health + absorption) + "/" + format(maxHealth);
            anchorLabel = onlyAbsorption ? "0/?" : format(maxHealth) + "/" + format(maxHealth);
            return true;
        }

        label = "";
        anchorLabel = "";
        Arrays.fill(sprites, HIDDEN);
        if (showHealth) {
            for (int i = 0; i < capacity; i++) {
                int remaining = healthUnits - i * 2;
                if (remaining > 0) sprites[i] = remaining == 1 ? HALF : FULL;
                else if (i < PERSISTENT_EMPTY_HEARTS || emptyUntil[i] > now) sprites[i] = EMPTY;
                if (emptyUntil[i] > now) nextExpiry = Math.min(nextExpiry, emptyUntil[i]);
                if (sprites[i] != HIDDEN) slots = i + 1;
            }
        }
        int absorptionUnits = halfUnits(absorption);
        for (int remaining = absorptionUnits; remaining > 0; remaining -= 2) {
            sprites[slots++] = remaining == 1 ? GOLD_HALF : GOLD;
        }
        return true;
    }

    float iconStartX(float heartWidth, float advance) {
        if (slots <= HEARTS_PER_ROW) return -((Math.max(1, slots) - 1) * advance + heartWidth) / 2 - 1;
        return -anchorSlots * advance / 2 - 1;
    }

    static float finitePositive(float value) {
        return Float.isFinite(value) && value > 0 ? value : 0;
    }

    private static int halfUnits(float hp) {
        return (int) Math.ceil(Math.min(MAX_HEARTS * 2, hp));
    }

    private static String format(double hp) {
        return BigDecimal.valueOf(hp).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
