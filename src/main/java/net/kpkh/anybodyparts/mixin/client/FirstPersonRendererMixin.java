package net.kpkh.anybodyparts.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.kpkh.anybodyparts.*;
import net.kpkh.anybodyparts.client.BodyRenderState;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.math.Axis;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonRendererMixin {
    @WrapOperation(method = "renderPlayerHand", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V"))
    private void anybodyparts$rightGraft(net.minecraft.client.renderer.entity.player.AvatarRenderer<?> renderer, PoseStack poses,
        SubmitNodeCollector collector, int light, net.minecraft.resources.Identifier texture, boolean sleeve, Operation<Void> original,
        @Local(argsOnly = true) PlayerRenderState player) {
        anybodyparts$graftHand(renderer, poses, collector, light, texture, sleeve, original, player, HumanoidArm.RIGHT);
    }
    @WrapOperation(method = "renderPlayerHand", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V"))
    private void anybodyparts$leftGraft(net.minecraft.client.renderer.entity.player.AvatarRenderer<?> renderer, PoseStack poses,
        SubmitNodeCollector collector, int light, net.minecraft.resources.Identifier texture, boolean sleeve, Operation<Void> original,
        @Local(argsOnly = true) PlayerRenderState player) {
        anybodyparts$graftHand(renderer, poses, collector, light, texture, sleeve, original, player, HumanoidArm.LEFT);
    }
    @org.spongepowered.asm.mixin.Unique
    private static void anybodyparts$graftHand(net.minecraft.client.renderer.entity.player.AvatarRenderer<?> renderer, PoseStack poses,
        SubmitNodeCollector collector, int light, net.minecraft.resources.Identifier texture, boolean sleeve, Operation<Void> original,
        PlayerRenderState player, HumanoidArm arm) {
        var state = player.avatarRenderState;
        var skin = state == null ? null : ((BodyRenderState)state).anybodyparts$grafts().get(arm == HumanoidArm.RIGHT ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM);
        if (skin != null) {
            var saved = state.skin;
            state.skin = skin;
            renderer = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(state);
            state.skin = saved;
            texture = skin.body().texturePath();
        }
        original.call(renderer, poses, collector, light, texture, sleeve);
    }

    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;applyBrushTransform(Lcom/mojang/blaze3d/vertex/PoseStack;FLnet/minecraft/world/entity/HumanoidArm;F)V"))
    private void anybodyparts$sawMotion(FirstPersonHandsAndItemsRenderer renderer, PoseStack poses, float delta,
        HumanoidArm arm, float remaining, Operation<Void> original, @Local(argsOnly = true) ItemStack item) {
        boolean needle = item.is(ModItems.SEWING_NEEDLE);
        if (!item.is(ModItems.LIMB_SAW) && !needle) { original.call(renderer, poses, delta, arm, remaining); return; }
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float stroke = (float)Math.sin((72000F - remaining + delta) * 0.65F);
        poses.translate(side * 0.4F, -0.35F, -0.72F + stroke * (needle ? 0.03F : 0.10F));
        poses.rotateDegrees(Axis.XP, -20F);
        poses.rotateDegrees(Axis.YP, side * 20F);
    }

    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void anybodyparts$hand(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float delta,
        float xRot, InteractionHand hand, float attack, ItemStack item, float height, PoseStack poses,
        SubmitNodeCollector collector, int light, CallbackInfo ci) {
        if (player.avatarRenderState != null) {
            BodyState body = ((BodyRenderState)player.avatarRenderState).anybodyparts$body();
            HumanoidArm arm = hand.asArm(player.avatarRenderState.mainArm);
            if (!body.has(arm == HumanoidArm.LEFT ? BodyPart.LEFT_ARM : BodyPart.RIGHT_ARM)) ci.cancel();
        }
    }

    @Inject(method = "renderPlayerHand", at = @At("HEAD"), cancellable = true)
    private void anybodyparts$mapHand(PoseStack poses, SubmitNodeCollector collector, int light, HumanoidArm arm,
        PlayerRenderState player, CallbackInfo ci) {
        if (player.avatarRenderState != null && !((BodyRenderState)player.avatarRenderState).anybodyparts$body()
            .has(arm == HumanoidArm.LEFT ? BodyPart.LEFT_ARM : BodyPart.RIGHT_ARM)) ci.cancel();
    }
}
