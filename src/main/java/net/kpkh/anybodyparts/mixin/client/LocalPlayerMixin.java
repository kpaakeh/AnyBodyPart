package net.kpkh.anybodyparts.mixin.client;

import net.kpkh.anybodyparts.BodyParts;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "isMovingSlowly", at = @At("HEAD"), cancellable = true)
    private void anybodyparts$crawlSpeed(CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer p = (LocalPlayer)(Object)this;
        if (BodyParts.active(p) && BodyParts.get(p).low() && !p.isShiftKeyDown()) cir.setReturnValue(false);
    }
}
