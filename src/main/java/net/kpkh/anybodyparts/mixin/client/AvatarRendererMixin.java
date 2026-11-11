package net.kpkh.anybodyparts.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.kpkh.anybodyparts.*;
import net.kpkh.anybodyparts.client.BodyRenderState;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void anybodyparts$extract(Avatar entity, AvatarRenderState state, float delta, CallbackInfo ci) {
        BodyState body = entity instanceof Player p && !p.isSpectator() ? BodyParts.get(p) : BodyState.COMPLETE;
        BodyRenderState data = (BodyRenderState)state;
        data.anybodyparts$body(body);
        var skins = new java.util.EnumMap<BodyPart, net.minecraft.world.entity.player.PlayerSkin>(BodyPart.class);
        if (entity instanceof Player p && !p.isSpectator() && BodyParts.rules(p).enabled()) {
            var cache = net.minecraft.client.Minecraft.getInstance().playerSkinRenderCache();
            Grafts.get(p).forEach((part, profile) -> { if (body.has(part)) skins.put(part, cache.getOrDefault(profile).playerSkin()); });
        }
        if (entity instanceof Player p && !p.isSpectator()) {
            int mechanical = MechanicalParts.active(p);
            for (var part : BodyPart.values()) if ((mechanical & part.bit()) != 0) skins.put(part, net.kpkh.anybodyparts.client.MechanicalSkin.SKIN);
        }
        data.anybodyparts$grafts(java.util.Map.copyOf(skins));
        data.anybodyparts$cutting(entity instanceof Player p ? p.getAttachedOrElse(BodyParts.CUTTING, 0) : 0);
        data.anybodyparts$roll(entity.walkAnimation.position(delta) * 2F);
        if (!body.has(BodyPart.TORSO)) {
            state.chestEquipment = ItemStack.EMPTY;
            state.showCape = false;
            state.parrotOnLeftShoulder = null;
            state.parrotOnRightShoulder = null;
        }
        if (body.legs() == 0) {
            state.legsEquipment = ItemStack.EMPTY;
            state.feetEquipment = ItemStack.EMPTY;
        }
        if (body.headOnly()) {
            state.shadowRadius = 0.25F;
            state.swimAmount = 0;
            state.isVisuallySwimming = false;
        }
    }

    @Inject(method = "setupRotations", at = @At("TAIL"))
    private void anybodyparts$roll(AvatarRenderState state, PoseStack poses, float bodyRot, float scale, CallbackInfo ci) {
        BodyRenderState data = (BodyRenderState)state;
        if (data.anybodyparts$body().headOnly() && !state.isPassenger) {
            poses.translate(0, 0.25F, 0);
            poses.rotate(Axis.XP, -data.anybodyparts$roll());
            poses.translate(0, -0.25F, 0);
        }
    }
}
