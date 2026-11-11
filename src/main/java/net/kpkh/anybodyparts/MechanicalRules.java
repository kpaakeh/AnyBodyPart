package net.kpkh.anybodyparts;

public record MechanicalRules(boolean enabled, int disabledMask, float legSpeed, float legJump, float armDamage,
    float armAttackSpeed, float armMiningSpeed, float armCrawlSpeed, float torsoSpeed) {
    public static MechanicalRules from(BodyConfig.Mechanical c) {
        return new MechanicalRules(c.enabled, BodyConfig.mask(c.disabledParts), c.legSpeed, c.legJump, c.armDamage,
            c.armAttackSpeed, c.armMiningSpeed, c.armCrawlSpeed, c.torsoSpeed);
    }
}
