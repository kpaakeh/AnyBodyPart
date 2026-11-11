package net.kpkh.anybodyparts.mixin;

import net.kpkh.anybodyparts.BodyParts;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void anybodyparts$drop(int slot, int button, ContainerInput input, Player player, CallbackInfo ci) {
        if (BodyParts.get(player).arms() == 0 && (input == ContainerInput.PICKUP && slot == -999)) ci.cancel();
    }
}
