package net.kpkh.anybodyparts;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;

public final class MechanicalParts {
    public static final AttachmentType<Integer> DATA = AttachmentRegistry.create(
        Identifier.fromNamespaceAndPath(AnyBodyParts.MOD_ID, "mechanical"), builder -> builder.initializer(() -> 0)
            .persistent(Codec.intRange(0, BodyState.ALL)).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
    private MechanicalParts() {}
    public static int stored(Player p) { return p.getAttachedOrElse(DATA, 0); }
    public static int active(Player p) { return BodyParts.rules(p).enabled() ? stored(p) & BodyParts.get(p).mask() : 0; }
    public static int disabled(Player p) {
        var rules = BodyParts.rules(p).mechanical();
        return stored(p) & (rules.enabled() ? rules.disabledMask() : BodyState.ALL);
    }
    public static void set(Player p, BodyPart part, boolean mechanical) {
        p.setAttached(DATA, mechanical ? stored(p) | part.bit() : stored(p) & ~part.bit());
        if (mechanical) Grafts.remove(p, part);
    }
    public static void removeMissing(Player p, BodyState body) { p.setAttached(DATA, stored(p) & body.mask()); }
    public static ItemStack drop(Player p, BodyPart part) {
        return (stored(p) & part.bit()) != 0 ? new ItemStack(ModItems.MECHANICAL.get(part)) : DetachedPartItem.create(part, Grafts.donor(p, part));
    }
    public static float factor(Player p, float perArm, float perLeg, float torso) {
        int mask = active(p);
        return (float)(Math.pow(perArm, Integer.bitCount(mask & 3)) * Math.pow(perLeg, Integer.bitCount(mask & 12))
            * ((mask & BodyPart.TORSO.bit()) != 0 ? torso : 1));
    }
    public static float speed(Player p) {
        var c = BodyParts.rules(p).mechanical();
        return BodyParts.get(p).legs() == 0 ? factor(p, c.armCrawlSpeed(), 1, c.torsoSpeed()) : factor(p, 1, c.legSpeed(), c.torsoSpeed());
    }
    public static double attribute(Player p, Holder<Attribute> attribute, double value) {
        var c = BodyParts.rules(p).mechanical();
        if (attribute == Attributes.ATTACK_DAMAGE) return value * factor(p, c.armDamage(), 1, 1);
        if (attribute == Attributes.ATTACK_SPEED) return value * factor(p, c.armAttackSpeed(), 1, 1);
        if (attribute == Attributes.BLOCK_BREAK_SPEED) return value * factor(p, c.armMiningSpeed(), 1, 1);
        if (attribute == Attributes.JUMP_STRENGTH) return value * factor(p, 1, c.legJump(), 1);
        return value;
    }
}
