package pl.slogerski.heartistica;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.Function;

final class HeartTargets<E, S extends HeartTargets.State<E>> {
    private final Function<E, S> createState;
    private final Map<E, S> states = new IdentityHashMap<>();
    private final List<S> selected = new ArrayList<>();
    private final PriorityQueue<S> nearestPlayers = new PriorityQueue<>(
            Comparator.comparingDouble((S state) -> state.distanceSquared).reversed());
    private long tick;

    HeartTargets(Function<E, S> createState) {
        this.createState = createState;
    }

    void beginTick() {
        tick++;
        selected.clear();
        nearestPlayers.clear();
    }

    S update(E entity, boolean mob, double distance, float health, float maxHealth, float absorption,
             HeartisticaConfig config, long now) {
        boolean visible = config.shouldDisplayHealth(health, maxHealth, absorption);
        if (mob && !visible) return null;
        S state = states.computeIfAbsent(entity, createState);
        state.seenTick = tick;
        state.distanceSquared = distance;
        boolean changed = state.display.update(health, maxHealth, absorption,
                config.numericDisplay, config.onlyAbsorption, config.onlyWhenDamaged, now);
        if (visible) select(state, mob ? 0 : config.nearestPlayers);
        return changed ? state : null;
    }

    private void select(S state, int limit) {
        if (limit == 0) selected.add(state);
        else if (nearestPlayers.size() < limit) nearestPlayers.offer(state);
        else if (state.distanceSquared < nearestPlayers.peek().distanceSquared) {
            nearestPlayers.poll();
            nearestPlayers.offer(state);
        }
    }

    void finishPlayers() {
        for (S state : nearestPlayers) selected.add(state);
    }

    void finishTick() {
        states.values().removeIf(state -> state.seenTick != tick);
    }

    List<S> selected() {
        return selected;
    }

    long tick() {
        return tick;
    }

    void clear() {
        states.clear();
        selected.clear();
        nearestPlayers.clear();
    }

    static class State<E> {
        final E entity;
        final HeartDisplayState display = new HeartDisplayState();
        long seenTick;
        double distanceSquared;

        State(E entity) {
            this.entity = entity;
        }
    }
}
