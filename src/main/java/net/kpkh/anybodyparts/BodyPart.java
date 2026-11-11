package net.kpkh.anybodyparts;

public enum BodyPart {
    LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG, TORSO;

    public int bit() { return 1 << ordinal(); }
    public String key() { return "part.anybodyparts." + name().toLowerCase(java.util.Locale.ROOT); }
}
