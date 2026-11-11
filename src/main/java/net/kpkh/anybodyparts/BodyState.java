package net.kpkh.anybodyparts;

public record BodyState(int mask) {
    public static final int ALL = 31;
    public static final BodyState COMPLETE = new BodyState(ALL);

    public BodyState {
        if (mask < 0 || mask > ALL) throw new IllegalArgumentException("Invalid body mask: " + mask);
    }

    public boolean has(BodyPart part) { return (mask & part.bit()) != 0; }
    public BodyState with(BodyPart part, boolean enabled) {
        return new BodyState(enabled ? mask | part.bit() : mask & ~part.bit());
    }
    public int arms() { return Integer.bitCount(mask & 3); }
    public int legs() { return Integer.bitCount((mask >> 2) & 3); }
    public boolean headOnly() { return mask == 0; }
    public boolean low() { return legs() == 0; }
    public boolean canSprint() { return legs() == 2 && has(BodyPart.TORSO); }
    public boolean canJump() { return !headOnly(); }
    public boolean canClimb() { return arms() > 0; }
    public boolean canGlide() { return has(BodyPart.TORSO) && arms() == 2; }

    public String movement() {
        if (headOnly()) return "roll";
        if (legs() == 2) return "walk";
        if (legs() == 1) return "hop";
        if (arms() == 2) return "crawl_two";
        if (arms() == 1) return "crawl_one";
        return "wriggle";
    }

    public float speed() {
        if (headOnly()) return 0.45F;
        if (legs() == 2) return has(BodyPart.TORSO) ? 1F : 0.75F;
        if (legs() == 1) return 0.55F;
        if (arms() == 2) return 0.32F;
        if (arms() == 1) return 0.18F;
        return 0.06F;
    }
}
