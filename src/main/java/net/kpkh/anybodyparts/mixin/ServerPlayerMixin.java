package net.kpkh.anybodyparts.mixin;

import net.kpkh.anybodyparts.BodyParts;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "drop(Z)V", at = @At("HEAD"), cancellable = true)
    private void anybodyparts$drop(boolean all, CallbackInfo ci) {
        if (!BodyParts.hasArm((ServerPlayer)(Object)this, InteractionHand.MAIN_HAND)) ci.cancel();
    }
}
