package net.kpkh.anybodyparts.mixin;

import net.kpkh.anybodyparts.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Avatar.class)
public abstract class AvatarMixin {
    @Inject(method = "getMainArm", at = @At("RETURN"), cancellable = true)
    private void anybodyparts$workingHand(CallbackInfoReturnable<HumanoidArm> cir) {
        if ((Object)this instanceof Player p) {
            BodyState state = BodyParts.get(p);
            if (state.arms() == 1) cir.setReturnValue(state.has(BodyPart.LEFT_ARM) ? HumanoidArm.LEFT : HumanoidArm.RIGHT);
        }
    }

    @Inject(method = "getDefaultDimensions", at = @At("HEAD"), cancellable = true)
    private void anybodyparts$dimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if ((Object)this instanceof Player p && !p.isSpectator() && BodyParts.get(p).headOnly() && p.isAlive() && !p.isSleeping()) {
            cir.setReturnValue(EntityDimensions.scalable(0.5F, 0.5F).withEyeHeight(0.3F));
        }
    }
}
