package net.kpkh.anybodyparts.mixin.client;

import net.kpkh.anybodyparts.*;
import net.kpkh.anybodyparts.client.BodyRenderState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void anybodyparts$armor(HumanoidRenderState state, CallbackInfo ci) {
        if (state instanceof BodyRenderState data) {
            HumanoidModel<?> model = (HumanoidModel<?>)(Object)this;
            BodyState body = data.anybodyparts$body();
            boolean visible = !(state instanceof AvatarRenderState avatar) || !avatar.isSpectator;
            model.leftArm.visible = visible && body.has(BodyPart.LEFT_ARM);
            model.rightArm.visible = visible && body.has(BodyPart.RIGHT_ARM);
            model.leftLeg.visible = visible && body.has(BodyPart.LEFT_LEG);
            model.rightLeg.visible = visible && body.has(BodyPart.RIGHT_LEG);
            model.body.visible = visible && body.has(BodyPart.TORSO);
            if (body.headOnly() && !state.isPassenger) model.head.y = 24F;
            var usedArm = state.useItemHand.asArm(state.mainArm);
            if (state.isUsingItem && (state.getUseItemStackForArm(usedArm).is(ModItems.LIMB_SAW) || state.getUseItemStackForArm(usedArm).is(ModItems.SEWING_NEEDLE))) {
                var arm = usedArm == net.minecraft.world.entity.HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
                float side = usedArm == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1 : -1;
                arm.xRot = -1.1F + (float)Math.sin(state.ageInTicks * 0.65F) * 0.22F;
                arm.yRot = -side * 0.25F;
            }
            int cutting = data.anybodyparts$cutting();
            int part = (cutting & 7) - 1;
            if (part >= 0 && part < BodyPart.values().length) {
                var limb = switch (BodyPart.values()[part]) {
                    case LEFT_ARM -> model.leftArm; case RIGHT_ARM -> model.rightArm;
                    case LEFT_LEG -> model.leftLeg; case RIGHT_LEG -> model.rightLeg; case TORSO -> model.body;
                };
                limb.zRot += (float)Math.sin(state.ageInTicks * 2.5F) * 0.06F;
            }
        }
    }
}
