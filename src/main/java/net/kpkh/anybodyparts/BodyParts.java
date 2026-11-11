package net.kpkh.anybodyparts;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.network.chat.Component;

public final class BodyParts {
    public static final AttachmentType<Integer> CUTTING = AttachmentRegistry.create(
        Identifier.fromNamespaceAndPath(AnyBodyParts.MOD_ID, "cutting"), builder -> builder
            .initializer(() -> 0).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
    public static final AttachmentType<BodyRules> RULES = AttachmentRegistry.create(
        Identifier.fromNamespaceAndPath(AnyBodyParts.MOD_ID, "rules"), builder -> builder
            .initializer(() -> BodyRules.DEFAULT).syncWith(ByteBufCodecs.STRING_UTF8.map(BodyRules::parse, BodyRules::json), AttachmentSyncPredicate.all()));
    public static final AttachmentType<Integer> STATE = AttachmentRegistry.create(
        Identifier.fromNamespaceAndPath(AnyBodyParts.MOD_ID, "body"), builder -> builder
            .initializer(() -> BodyState.ALL).persistent(Codec.intRange(0, BodyState.ALL)).copyOnDeath()
            .syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

    private BodyParts() {}

    public static BodyState stored(Player player) {
        return new BodyState(player.getAttachedOrElse(STATE, BodyState.ALL));
    }

    public static BodyRules rules(Player player) {
        return player.level().isClientSide() ? player.getAttachedOrElse(RULES, BodyRules.DEFAULT) : BodyConfig.get().rules();
    }
    public static BodyState get(Player player) { return rules(player).effective(new BodyState(stored(player).mask() & ~MechanicalParts.disabled(player))); }
    public static float speed(Player player) { return rules(player).speed(get(player)) * MechanicalParts.speed(player); }

    public static boolean canSprint(Player p) { return get(p).canSprint() && (MechanicalParts.active(p) & BodyPart.TORSO.bit()) == 0; }
    public static boolean canGlide(Player p) { return get(p).canGlide() && (MechanicalParts.active(p) & BodyPart.TORSO.bit()) == 0; }
    public static boolean active(Player player) {
        return rules(player).enabled() && !player.isSpectator() && !player.getAbilities().flying;
    }

    public static boolean hasArm(Player player, InteractionHand hand) {
        HumanoidArm arm = hand.asArm(player.getMainArm());
        return get(player).has(arm == HumanoidArm.LEFT ? BodyPart.LEFT_ARM : BodyPart.RIGHT_ARM);
    }

    public static boolean canUse(Player player, InteractionHand hand) {
        if (player.isSpectator()) return true;
        if (!hasArm(player, hand)) return false;
        return !rules(player).twoHanded(player.getItemInHand(hand)) || get(player).arms() == 2;
    }

    public static boolean set(Player player, BodyState state) {
        BodyState raw = stored(player);
        int removing = raw.mask() & ~state.mask();
        if ((removing & BodyConfig.mask(BodyConfig.get().global.uncuttableParts)) != 0) {
            player.sendOverlayMessage(Component.translatable("message.anybodyparts.protected"));
            return false;
        }
        BodyState previous = get(player);
        BodyState effective = rules(player).effective(state);
        if (previous.low() && !effective.headOnly() && !player.isSpectator() && !player.getAbilities().flying) {
            if (!player.level().noCollision(player, EntityDimensions.scalable(0.6F, 0.6F).makeBoundingBox(player.position()))) {
                player.sendOverlayMessage(Component.translatable("message.anybodyparts.no_room"));
                return false;
            }
            player.setPose(Pose.SWIMMING);
        }
        player.setAttached(STATE, state.mask());
        Grafts.removeMissing(player, state);
        MechanicalParts.removeMissing(player, state);
        if (player.isUsingItem() && !canUse(player, player.getUsedItemHand())) player.stopUsingItem();
        if (!canSprint(player)) player.setSprinting(false);
        player.refreshDimensions();
        return true;
    }
    public static boolean reset(Player player) {
        if (!set(player, BodyState.COMPLETE)) return false;
        Grafts.clear(player);
        player.setAttached(MechanicalParts.DATA, 0);
        return true;
    }
}
