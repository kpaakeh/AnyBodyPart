package net.kpkh.anybodyparts.mixin;

import net.kpkh.anybodyparts.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "getAttributeValue", at = @At("RETURN"), cancellable = true)
    private void anybodyparts$mechanical(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, CallbackInfoReturnable<Double> cir) {
        if ((Object)this instanceof Player p) {
            double value = MechanicalParts.attribute(p, attribute, cir.getReturnValue());
            if (attribute == net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH && BodyParts.active(p) && BodyParts.get(p).legs() == 0)
                value *= BodyParts.rules(p).leglessJumpStrength();
            cir.setReturnValue(value);
        }
    }
    @WrapOperation(method = "checkTotemDeathProtection", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;getItemInHand(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack anybodyparts$totem(LivingEntity entity, InteractionHand hand, Operation<ItemStack> original) {
        return entity instanceof Player p && !BodyParts.hasArm(p, hand) ? ItemStack.EMPTY : original.call(entity, hand);
    }

    @ModifyExpressionValue(method = "travelInAir", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;computeModifiedFriction(FF)F", ordinal = 0))
    private float anybodyparts$rollingFriction(float friction) {
        if ((Object)this instanceof Player p && BodyParts.active(p) && BodyParts.get(p).headOnly()) {
            return Math.max(friction, 0.9F);
        }
        return friction;
    }

    @Inject(method = "maxUpStep", at = @At("RETURN"), cancellable = true)
    private void anybodyparts$step(CallbackInfoReturnable<Float> cir) {
        if ((Object)this instanceof Player p && BodyParts.active(p) && BodyParts.get(p).legs() < 2) {
            cir.setReturnValue(BodyParts.get(p).legs() == 0 ? Math.min(0.25F, cir.getReturnValue()) : 0F);
        }
    }

    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void anybodyparts$jump(CallbackInfo ci) {
        if ((Object)this instanceof Player p && BodyParts.active(p) && (!BodyParts.get(p).canJump() || BodyParts.get(p).legs() == 0 && !BodyParts.rules(p).allowLeglessJump())) ci.cancel();
    }

    @Inject(method = "jumpFromGround", at = @At("TAIL"))
    private void anybodyparts$hop(CallbackInfo ci) {
        if ((Object)this instanceof Player p && BodyParts.active(p) && BodyParts.get(p).legs() == 1) {
            Vec3 input = new Vec3(p.xxa, 0, p.zza);
            if (input.lengthSqr() > 0.001) {
                Vec3 impulse = input.normalize().scale(0.15 * MechanicalParts.factor(p, 1, BodyParts.rules(p).mechanical().legJump(), 1)).yRot(-p.getYRot() * (float)Math.PI / 180F);
                p.addDeltaMovement(impulse);
            }
        }
    }

    @ModifyVariable(method = "setSprinting", at = @At("HEAD"), argsOnly = true)
    private boolean anybodyparts$sprint(boolean sprinting) {
        return sprinting && (!((Object)this instanceof Player p) || !BodyParts.active(p) || BodyParts.canSprint(p));
    }

    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void anybodyparts$climb(CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this instanceof Player p && BodyParts.active(p) && !BodyParts.get(p).canClimb()) cir.setReturnValue(false);
    }

    @Inject(method = "startUsingItem", at = @At("HEAD"), cancellable = true)
    private void anybodyparts$use(InteractionHand hand, CallbackInfo ci) {
        if ((Object)this instanceof Player p && !BodyParts.canUse(p, hand)) ci.cancel();
    }
}
