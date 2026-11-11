package net.kpkh.anybodyparts.mixin;

import net.kpkh.anybodyparts.*;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "canDropItems", at = @At("HEAD"), cancellable = true)
    private void anybodyparts$canDrop(CallbackInfoReturnable<Boolean> cir) {
        if (BodyParts.get((Player)(Object)this).arms() == 0) cir.setReturnValue(false);
    }

    @Unique private int anybodyparts$lastMask = BodyState.ALL;

    @Inject(method = "tick", at = @At("HEAD"))
    private void anybodyparts$refresh(CallbackInfo ci) {
        Player player = (Player)(Object)this;
        int mask = BodyParts.get(player).mask();
        if (mask != anybodyparts$lastMask) {
            anybodyparts$lastMask = mask;
            player.refreshDimensions();
        }
        if (player.isUsingItem() && !BodyParts.canUse(player, player.getUsedItemHand())) player.stopUsingItem();
    }

    @Inject(method = "getDesiredPose", at = @At("HEAD"), cancellable = true)
    private void anybodyparts$pose(CallbackInfoReturnable<Pose> cir) {
        Player p = (Player)(Object)this;
        if (!BodyParts.active(p) || p.isSleeping() || p.isPassenger() || !p.isAlive()) return;
        BodyState body = BodyParts.get(p);
        if (body.low()) cir.setReturnValue(body.headOnly() ? Pose.STANDING : Pose.SWIMMING);
    }

    @Inject(method = "getSpeed", at = @At("RETURN"), cancellable = true)
    private void anybodyparts$speed(CallbackInfoReturnable<Float> cir) {
        Player p = (Player)(Object)this;
        if (BodyParts.active(p)) cir.setReturnValue(cir.getReturnValue() * BodyParts.speed(p));
    }

    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true)
    private Vec3 anybodyparts$travel(Vec3 input) {
        Player p = (Player)(Object)this;
        if (!BodyParts.active(p) || p.isPassenger()) return input;
        BodyState body = BodyParts.get(p);
        if (body.legs() == 1 && p.onGround() && !p.isInWater()) return Vec3.ZERO;
        // Air and water acceleration do not use Player.getSpeed().
        if (!p.onGround() || p.isInWater()) return input.scale(BodyParts.speed(p));
        return input;
    }

    @Inject(method = "canGlide", at = @At("HEAD"), cancellable = true)
    private void anybodyparts$glide(CallbackInfoReturnable<Boolean> cir) {
        if (!BodyParts.canGlide((Player)(Object)this)) cir.setReturnValue(false);
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void anybodyparts$attack(Entity target, CallbackInfo ci) {
        if (!BodyParts.canUse((Player)(Object)this, InteractionHand.MAIN_HAND)) ci.cancel();
    }

    @Inject(method = "stabAttack", at = @At("HEAD"), cancellable = true)
    private void anybodyparts$stab(EquipmentSlot slot, Entity target, float damage, boolean dealsDamage,
        boolean knockback, boolean dismounts, CallbackInfoReturnable<Boolean> cir) {
        InteractionHand hand = slot == EquipmentSlot.OFFHAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (!BodyParts.canUse((Player)(Object)this, hand)) cir.setReturnValue(false);
    }
}
