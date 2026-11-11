package net.kpkh.anybodyparts.mixin.client;

import net.kpkh.anybodyparts.*;
import net.kpkh.anybodyparts.client.BodyRenderState;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void anybodyparts$hide(AvatarRenderState state, CallbackInfo ci) {
        PlayerModel model = (PlayerModel)(Object)this;
        BodyState body = ((BodyRenderState)state).anybodyparts$body();
        model.leftArm.visible &= body.has(BodyPart.LEFT_ARM);
        model.rightArm.visible &= body.has(BodyPart.RIGHT_ARM);
        model.leftLeg.visible &= body.has(BodyPart.LEFT_LEG);
        model.rightLeg.visible &= body.has(BodyPart.RIGHT_LEG);
        model.body.visible &= body.has(BodyPart.TORSO);
        for (var part : ((BodyRenderState)state).anybodyparts$grafts().keySet()) {
            net.kpkh.anybodyparts.client.SkinLimbModel.part(model, part).visible = false;
        }
        if (body.headOnly() && !state.isPassenger) model.head.y = 24F;
        if (body.legs() == 0 && body.arms() == 0 && body.has(BodyPart.TORSO) && !state.isPassenger) {
            model.head.xRot += (float)Math.sin(state.walkAnimationPos * 6F) * Math.min(0.35F, state.walkAnimationSpeed * 8F);
        }
    }
}
