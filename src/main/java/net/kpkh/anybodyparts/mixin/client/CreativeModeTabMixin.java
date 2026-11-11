package net.kpkh.anybodyparts.mixin.client;

import net.kpkh.anybodyparts.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CreativeModeTab.class)
public abstract class CreativeModeTabMixin {
    @Inject(method = "shouldDisplay", at = @At("HEAD"), cancellable = true)
    private void anybodyparts$visible(CallbackInfoReturnable<Boolean> cir) {
        if (ModItems.TAB_KEY.identifier().equals(BuiltInRegistries.CREATIVE_MODE_TAB.getKey((CreativeModeTab)(Object)this))) {
            var player = Minecraft.getInstance().player;
            if (player != null && (!BodyParts.rules(player).enabled() || !BodyParts.rules(player).creativeTab())) cir.setReturnValue(false);
        }
    }
}
