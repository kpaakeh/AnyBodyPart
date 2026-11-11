package net.kpkh.anybodyparts;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class SawItem extends Item {
    private static final Map<UUID, Cut> CUTS = new HashMap<>();
    private static final class Cut {
        final UUID target;
        final BodyPart part;
        final Vec3 surgeonStart, targetStart;
        final float surgeonHealth, targetHealth, yaw, pitch;
        final int duration;
        int elapsed;
        Cut(ServerPlayer actor, ServerPlayer target, BodyPart part, int duration) {
            this.target = target.getUUID(); this.part = part; this.duration = duration;
            surgeonStart = actor.position(); targetStart = target.position();
            surgeonHealth = actor.getHealth(); targetHealth = target.getHealth();
            yaw = target.getYRot(); pitch = target.getXRot();
        }
    }
    public SawItem(Properties properties) { super(properties); }
    public static BodyPart selected(ItemStack stack) {
        int index = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr("anybodyparts_part", 0);
        return BodyPart.values()[Math.floorMod(index, BodyPart.values().length)];
    }
    public static void select(ItemStack stack, BodyPart part) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt("anybodyparts_part", part.ordinal()));
    }
    @Override public Component getName(ItemStack stack) {
        return Component.empty().append(super.getName(stack)).append(" · ").append(Component.translatable(selected(stack).key()));
    }
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }
    @Override public ItemUseAnimation getUseAnimation(ItemStack stack) { return ItemUseAnimation.BRUSH; }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                ItemStack stack = player.getItemInHand(hand);
                BodyPart next = BodyPart.values()[(selected(stack).ordinal() + 1) % BodyPart.values().length];
                select(stack, next);
            }
            return InteractionResult.SUCCESS;
        }
        return begin(player, player, hand);
    }
    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        return target instanceof Player other ? begin(player, other, hand) : InteractionResult.FAIL;
    }
    public static InteractionResult begin(Player player, Player target, InteractionHand hand) {
        if (player.level().isClientSide()) {
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
        if (!(player instanceof ServerPlayer actor) || !(target instanceof ServerPlayer subject)) return InteractionResult.FAIL;
        if (CUTS.containsKey(actor.getUUID())) return InteractionResult.FAIL;
        BodyPart part = selected(actor.getItemInHand(hand));
        String denied = denial(actor, subject, part);
        if (denied != null) { actor.sendOverlayMessage(Component.translatable(denied)); return InteractionResult.FAIL; }
        if (subject.getAttachedOrElse(BodyParts.CUTTING, 0) != 0) {
            actor.sendOverlayMessage(Component.translatable("message.anybodyparts.busy")); return InteractionResult.FAIL;
        }
        int ticks = (int)Math.ceil((actor == subject ? BodyConfig.get().saw.selfSeconds : BodyConfig.get().saw.otherSeconds) * 20);
        CUTS.put(actor.getUUID(), new Cut(actor, subject, part, ticks));
        subject.setAttached(BodyParts.CUTTING, part.ordinal() + 1);
        actor.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }
    public static String denial(ServerPlayer actor, ServerPlayer target, BodyPart part) {
        BodyConfig config = BodyConfig.get();
        if (!config.enabled || !config.saw.enabled) return "message.anybodyparts.saw_disabled";
        if (!actor.isAlive() || !target.isAlive() || actor.isSpectator() || target.isSpectator() || actor.isSleeping() || target.isSleeping()) return "message.anybodyparts.cannot_cut";
        boolean self = actor == target;
        if (!BodyPermissions.allow(actor, self ? "saw.self" : "saw.others", true)) return "message.anybodyparts.no_permission";
        if (!self && !config.saw.allowOtherPlayers) return "message.anybodyparts.other_disabled";
        if (!BodyParts.get(target).has(part)) return "message.anybodyparts.already_missing";
        if ((BodyConfig.mask(config.global.uncuttableParts) & part.bit()) != 0) return "message.anybodyparts.protected";
        if ((BodyConfig.mask(config.saw.opOnlyParts) & part.bit()) != 0
            && !BodyPermissions.admin(actor, "saw.bypass." + part.name().toLowerCase(Locale.ROOT))) return "message.anybodyparts.op_only";
        if (part == BodyPart.TORSO && config.saw.requireLimbsRemovedForTorso
            && (BodyParts.stored(target).mask() & 15) != 0) return "message.anybodyparts.torso_last";
        if (!config.saw.allowLastArm && (part == BodyPart.LEFT_ARM || part == BodyPart.RIGHT_ARM) && BodyParts.get(target).arms() <= 1) return "message.anybodyparts.last_arm";
        if (!actor.onGround() || !target.onGround() || actor.isPassenger() || target.isPassenger() || actor.isInLiquid() || target.isInLiquid()) return "message.anybodyparts.stand_still";
        if (!self && (actor.level() != target.level() || actor.distanceToSqr(target) > config.saw.reach * config.saw.reach || !actor.hasLineOfSight(target))) return "message.anybodyparts.out_of_reach";
        if (!self && config.saw.requireTargetSneaking && !target.isShiftKeyDown()) return "message.anybodyparts.consent";
        if (config.saw.preventLethalCut && config.saw.damageOnCut > 0 && target.getHealth() <= config.saw.damageOnCut) return "message.anybodyparts.lethal";
        return null;
    }
    @Override public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (!(user instanceof ServerPlayer actor)) return;
        Cut cut = CUTS.get(actor.getUUID());
        if (cut == null) { actor.stopUsingItem(); return; }
        ServerPlayer target = actor.level().getServer().getPlayerList().getPlayer(cut.target);
        if (target == null) { cancel(actor); return; }
        BodyConfig.Saw config = BodyConfig.get().saw;
        boolean self = actor == target;
        String denied = denial(actor, target, cut.part);
        double tolerance = self ? 0.1 : config.movementTolerance;
        boolean moved = target.position().distanceToSqr(cut.targetStart) > tolerance * tolerance
            || actor.position().distanceToSqr(cut.surgeonStart) > 0.01;
        boolean looked = !self && config.cancelOnLook && (Math.abs(net.minecraft.util.Mth.wrapDegrees(target.getYRot() - cut.yaw)) > 2 || Math.abs(target.getXRot() - cut.pitch) > 2);
        boolean hurt = config.cancelOnDamage && (actor.getHealth() < cut.surgeonHealth || target.getHealth() < cut.targetHealth || actor.hurtTime > 0 || target.hurtTime > 0);
        boolean aimLost = !self && actor.getLookAngle().dot(target.getBoundingBox().getCenter().subtract(actor.getEyePosition()).normalize()) < 0.65;
        if (denied != null || moved || looked || hurt || aimLost || !BodyParts.canUse(actor, actor.getUsedItemHand())) { cancel(actor); return; }
        cut.elapsed++;
        if (cut.elapsed % 10 == 0) {
            if (BodyConfig.get().features.progressOverlay) actor.sendOverlayMessage(Component.translatable("message.anybodyparts.progress", cut.elapsed * 100 / cut.duration));
            Vec3 spot = partPosition(target, cut.part);
            actor.level().sendParticles(ParticleTypes.CRIT, spot.x, spot.y, spot.z, 3, 0.08, 0.08, 0.08, 0.01);
            actor.level().playSound(null, spot.x, spot.y, spot.z, SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.25F, 1.25F);
        }
        if (cut.elapsed < cut.duration) return;
        CUTS.remove(actor.getUUID());
        target.setAttached(BodyParts.CUTTING, 0);
        InteractionHand hand = actor.getUsedItemHand();
        ItemStack detached = MechanicalParts.drop(target, cut.part);
        boolean changed = BodyParts.set(target, BodyParts.stored(target).with(cut.part, false));
        if (changed) {
            Vec3 spot = partPosition(target, cut.part);
            var drop = new net.minecraft.world.entity.item.ItemEntity(target.level(), spot.x, spot.y, spot.z, detached);
            drop.setDefaultPickUpDelay();
            if (BodyConfig.get().features.detachedDrops) target.level().addFreshEntity(drop);
            stack.hurtAndBreak(config.durabilityCost, actor, hand);
            if (config.damageOnCut > 0) target.hurtServer(target.level(), target.damageSources().generic(), config.damageOnCut);
            actor.getCooldowns().addCooldown(stack, 20);
            BodyAdvancements.award(actor, "first_cut");
            if (BodyParts.get(target).headOnly()) BodyAdvancements.award(target, "head_only");
            if (config.logCuts) AnyBodyParts.LOGGER.info("{} removed {} from {} with limb saw", actor.getGameProfile().name(), cut.part, target.getGameProfile().name());
        }
        actor.stopUsingItem();
        actor.sendOverlayMessage(Component.empty());
    }
    public static Vec3 partPosition(Player target, BodyPart part) {
        float height = target.getBbHeight();
        double side = switch (part) { case LEFT_ARM, LEFT_LEG -> 0.25; case RIGHT_ARM, RIGHT_LEG -> -0.25; default -> 0; };
        double y = part == BodyPart.LEFT_LEG || part == BodyPart.RIGHT_LEG ? height * 0.25 : height * 0.65;
        return target.position().add(new Vec3(side, y, 0).yRot(-target.getYRot() * (float)Math.PI / 180));
    }
    private static void cancel(ServerPlayer actor) {
        Cut cut = CUTS.remove(actor.getUUID());
        if (cut != null) {
            var target = actor.level().getServer().getPlayerList().getPlayer(cut.target);
            if (target != null) target.setAttached(BodyParts.CUTTING, 0);
        }
        actor.stopUsingItem();
        actor.sendOverlayMessage(Component.empty());
    }
    @Override public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        if (user instanceof ServerPlayer actor && CUTS.containsKey(actor.getUUID())) cancel(actor);
        return false;
    }
    public static void cleanup(net.minecraft.server.MinecraftServer server) {
        for (UUID id : List.copyOf(CUTS.keySet())) {
            var actor = server.getPlayerList().getPlayer(id);
            if (actor == null) {
                Cut cut = CUTS.remove(id);
                var target = server.getPlayerList().getPlayer(cut.target);
                if (target != null) target.setAttached(BodyParts.CUTTING, 0);
            }
            else if (!actor.isUsingItem() || !actor.getUseItem().is(ModItems.LIMB_SAW)) cancel(actor);
        }
    }
    public static void cancelAll() { CUTS.clear(); }
}
