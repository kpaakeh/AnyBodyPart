package net.kpkh.anybodyparts;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class SewingNeedleItem extends Item {
    private static final Map<UUID, Stitch> STITCHES = new HashMap<>();
    private static final class Stitch {
        final UUID target;
        final ServerPlayer patient;
        final BodyPart part;
        final int slot, duration;
        final ItemStack limb;
        final Vec3 actorStart, targetStart;
        final float actorHealth, targetHealth;
        int elapsed;
        Stitch(ServerPlayer actor, ServerPlayer target, BodyPart part, int slot) {
            this.target = target.getUUID(); this.patient = target; this.part = part; this.slot = slot;
            duration = (int)Math.ceil(BodyConfig.get().sewing.seconds * 20);
            limb = actor.getInventory().getItem(slot).copy();
            actorStart = actor.position(); targetStart = target.position();
            actorHealth = actor.getHealth(); targetHealth = target.getHealth();
        }
    }
    public SewingNeedleItem(Properties properties) { super(properties); }
    @Override public Component getName(ItemStack stack) {
        return Component.empty().append(super.getName(stack)).append(" В· ").append(Component.translatable(SawItem.selected(stack).key()));
    }
    @Override public int getUseDuration(ItemStack stack, LivingEntity user) { return 72000; }
    @Override public ItemUseAnimation getUseAnimation(ItemStack stack) { return ItemUseAnimation.BRUSH; }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                var stack = player.getItemInHand(hand);
                BodyPart next = BodyPart.values()[(SawItem.selected(stack).ordinal() + 1) % BodyPart.values().length];
                SawItem.select(stack, next);
            }
            return InteractionResult.SUCCESS;
        }
        return begin(player, player, hand);
    }
    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        return target instanceof Player other ? begin(player, other, hand) : InteractionResult.FAIL;
    }
    private static int findPart(Player actor, Player target, BodyPart part) {
        int fallback = -1;
        for (int slot = 0; slot < actor.getInventory().getNonEquipmentItems().size(); slot++) {
            ItemStack stack = actor.getInventory().getItem(slot);
            if (!(stack.getItem() instanceof DetachedPartItem item) || item.part() != part) continue;
            if (item instanceof MechanicalPartItem) {
                if (BodyConfig.get().mechanical.enabled && (BodyConfig.mask(BodyConfig.get().mechanical.disabledParts) & part.bit()) == 0) return slot;
            } else {
                var profile = stack.get(DataComponents.PROFILE);
                if (profile == null) continue;
                if (BodyConfig.get().sewing.allowForeignParts || profile.partialProfile().id().equals(target.getUUID())) return slot;
            }
            if (fallback < 0) fallback = slot;
        }
        return fallback;
    }
    private static String denial(ServerPlayer actor, ServerPlayer target, BodyPart part, InteractionHand hand) {
        var config = BodyConfig.get().sewing;
        if (!BodyConfig.get().enabled || !config.enabled) return "message.anybodyparts.sewing_disabled";
        if (!actor.isAlive() || !target.isAlive() || actor.isSpectator() || target.isSpectator() || actor.isSleeping() || target.isSleeping()) return "message.anybodyparts.cannot_cut";
        if (!BodyPermissions.allow(actor, actor == target ? "sewing.self" : "sewing.others", true)) return "message.anybodyparts.no_permission";
        if (hand != InteractionHand.MAIN_HAND) return "message.anybodyparts.needle_main_hand";
        if (BodyParts.get(actor).arms() != 2) return "message.anybodyparts.need_two_arms";
        if (!actor.getOffhandItem().is(Items.STRING) || actor.getOffhandItem().getCount() < config.threadCost) return "message.anybodyparts.need_thread";
        if (BodyParts.stored(target).has(part)) return "message.anybodyparts.part_present";
        if ((BodyConfig.get().rules().disabledMask() & part.bit()) != 0) return "message.anybodyparts.global_disabled";
        if (!actor.onGround() || !target.onGround() || actor.isPassenger() || target.isPassenger() || actor.isInLiquid() || target.isInLiquid()) return "message.anybodyparts.stand_still";
        if (actor != target && (actor.level() != target.level() || actor.distanceToSqr(target) > config.reach * config.reach || !actor.hasLineOfSight(target))) return "message.anybodyparts.out_of_reach";
        if (actor != target && config.requireTargetSneaking && !target.isShiftKeyDown()) return "message.anybodyparts.consent";
        int slot = findPart(actor, target, part);
        if (slot < 0) return "message.anybodyparts.need_part";
        if (actor.getInventory().getItem(slot).getItem() instanceof MechanicalPartItem) {
            if (!BodyConfig.get().mechanical.enabled || (BodyConfig.mask(BodyConfig.get().mechanical.disabledParts) & part.bit()) != 0) return "message.anybodyparts.mechanical_disabled";
            return null;
        }
        var profile = actor.getInventory().getItem(slot).get(DataComponents.PROFILE);
        if (!config.allowForeignParts && !profile.partialProfile().id().equals(target.getUUID())) return "message.anybodyparts.foreign_disabled";
        return null;
    }
    public static InteractionResult begin(Player player, Player target, InteractionHand hand) {
        if (player.level().isClientSide()) { player.startUsingItem(hand); return InteractionResult.CONSUME; }
        if (!(player instanceof ServerPlayer actor) || !(target instanceof ServerPlayer subject)) return InteractionResult.FAIL;
        if (STITCHES.containsKey(actor.getUUID())) return InteractionResult.FAIL;
        BodyPart part = SawItem.selected(actor.getItemInHand(hand));
        String denied = denial(actor, subject, part, hand);
        if (denied != null) { actor.sendOverlayMessage(Component.translatable(denied)); return InteractionResult.FAIL; }
        if (subject.getAttachedOrElse(BodyParts.CUTTING, 0) != 0) { actor.sendOverlayMessage(Component.translatable("message.anybodyparts.busy")); return InteractionResult.FAIL; }
        STITCHES.put(actor.getUUID(), new Stitch(actor, subject, part, findPart(actor, subject, part)));
        subject.setAttached(BodyParts.CUTTING, part.ordinal() + 1);
        actor.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }
    @Override public void onUseTick(Level level, LivingEntity user, ItemStack needle, int remaining) {
        if (!(user instanceof ServerPlayer actor)) return;
        Stitch stitch = STITCHES.get(actor.getUUID());
        if (stitch == null) { actor.stopUsingItem(); return; }
        var target = actor.level().getServer().getPlayerList().getPlayer(stitch.target);
        if (target != stitch.patient) { cancel(actor); return; }
        var config = BodyConfig.get().sewing;
        boolean self = actor == target;
        double tolerance = self ? 0.1 : config.movementTolerance;
        boolean moved = actor.position().distanceToSqr(stitch.actorStart) > 0.01 || target.position().distanceToSqr(stitch.targetStart) > tolerance * tolerance;
        boolean hurt = config.cancelOnDamage && (actor.getHealth() < stitch.actorHealth || target.getHealth() < stitch.targetHealth || actor.hurtTime > 0 || target.hurtTime > 0);
        boolean aimLost = !self && actor.getLookAngle().dot(target.getBoundingBox().getCenter().subtract(actor.getEyePosition()).normalize()) < 0.65;
        if (denial(actor, target, stitch.part, actor.getUsedItemHand()) != null || moved || hurt || aimLost
            || !ItemStack.matches(actor.getInventory().getItem(stitch.slot), stitch.limb)) { cancel(actor); return; }
        stitch.elapsed++;
        if (stitch.elapsed % 10 == 0) {
            if (BodyConfig.get().features.progressOverlay) actor.sendOverlayMessage(Component.translatable("message.anybodyparts.progress", stitch.elapsed * 100 / stitch.duration));
            var spot = SawItem.partPosition(target, stitch.part);
            target.level().sendParticles(ParticleTypes.HAPPY_VILLAGER, spot.x, spot.y, spot.z, 2, 0.05, 0.05, 0.05, 0);
            target.level().playSound(null, spot.x, spot.y, spot.z, SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.3F, 1.4F);
        }
        if (stitch.elapsed < stitch.duration) return;
        STITCHES.remove(actor.getUUID()); target.setAttached(BodyParts.CUTTING, 0);
        if (BodyParts.set(target, BodyParts.stored(target).with(stitch.part, true))) {
            boolean mechanical = stitch.limb.getItem() instanceof MechanicalPartItem;
            MechanicalParts.set(target, stitch.part, mechanical);
            if (!mechanical) Grafts.put(target, stitch.part, stitch.limb.get(DataComponents.PROFILE));
            actor.getInventory().getItem(stitch.slot).shrink(1);
            actor.getOffhandItem().shrink(config.threadCost);
            needle.hurtAndBreak(config.durabilityCost, actor, InteractionHand.MAIN_HAND);
            actor.getCooldowns().addCooldown(needle, 20);
            BodyAdvancements.award(actor, "first_stitch");
            if (mechanical) {
                BodyAdvancements.award(target, "first_mechanical");
                if (MechanicalParts.stored(target) == BodyState.ALL) BodyAdvancements.award(target, "full_mechanical");
            } else {
                boolean own = stitch.limb.get(DataComponents.PROFILE).partialProfile().id().equals(target.getUUID());
                BodyAdvancements.award(target, own ? "own_part" : "foreign_part");
            }
            if (Grafts.get(target).values().stream().filter(p -> !p.partialProfile().id().equals(target.getUUID())).count() >= 3) BodyAdvancements.award(target, "patchwork");
        }
        actor.stopUsingItem();
        actor.sendOverlayMessage(Component.empty());
    }
    private static void cancel(ServerPlayer actor) {
        Stitch stitch = STITCHES.remove(actor.getUUID());
        if (stitch != null) {
            if (!stitch.patient.isRemoved()) stitch.patient.setAttached(BodyParts.CUTTING, 0);
        }
        actor.stopUsingItem(); actor.sendOverlayMessage(Component.empty());
    }
    @Override public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        if (user instanceof ServerPlayer actor && STITCHES.containsKey(actor.getUUID())) cancel(actor);
        return false;
    }
    public static void cleanup(net.minecraft.server.MinecraftServer server) {
        for (UUID id : List.copyOf(STITCHES.keySet())) {
            var actor = server.getPlayerList().getPlayer(id);
            if (actor == null) {
                var patient = STITCHES.remove(id).patient;
                if (!patient.isRemoved()) patient.setAttached(BodyParts.CUTTING, 0);
            } else if (!actor.isUsingItem() || !actor.getUseItem().is(ModItems.SEWING_NEEDLE)) cancel(actor);
        }
    }
    public static void cancelAll() { STITCHES.clear(); }
}
