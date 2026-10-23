package pl.slogerski.heartistica;

final class CatalogScroll {
    private double position, target, maximum;
    private long last;
    void limit(double maximum) {
        this.maximum = Math.max(0, maximum);
        target = Math.max(0, Math.min(this.maximum, target));
        position = Math.max(0, Math.min(this.maximum, position));
    }
    void to(double value) { target = Math.max(0, Math.min(maximum, value)); }
    void by(double amount) { to(target + amount); }
    double target() { return target; }
    double maximum() { return maximum; }
    double step(long now) {
        double seconds = last == 0 ? 1.0 / 60 : Math.min(0.1, Math.max(0, (now - last) / 1_000_000_000.0));
        last = now;
        position += (target - position) * (1 - Math.exp(-18 * seconds));
        if (Math.abs(target - position) < 0.01) position = target;
        return position;
    }
}
