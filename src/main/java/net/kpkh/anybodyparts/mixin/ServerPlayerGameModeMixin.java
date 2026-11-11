package net.kpkh.anybodyparts.mixin;

import net.kpkh.anybodyparts.BodyParts;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
    @Shadow protected ServerPlayer player;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void anybodyparts$break(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!BodyParts.canUse(player, InteractionHand.MAIN_HAND)) cir.setReturnValue(false);
    }
}
