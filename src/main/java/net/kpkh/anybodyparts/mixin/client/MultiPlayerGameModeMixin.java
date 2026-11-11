package net.kpkh.anybodyparts.mixin.client;

import net.kpkh.anybodyparts.BodyParts;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
    private void anybodyparts$drop(LocalPlayer player, boolean all, CallbackInfo ci) {
        if (!BodyParts.hasArm(player, InteractionHand.MAIN_HAND)) ci.cancel();
    }
}
