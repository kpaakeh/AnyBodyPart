package net.kpkh.anybodyparts.mixin;

import net.kpkh.anybodyparts.*;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.advancements.AdvancementHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
    @Inject(method = "award", at = @At("HEAD"), cancellable = true)
    private void anybodyparts$enabled(AdvancementHolder advancement, String criterion, CallbackInfoReturnable<Boolean> cir) {
        if (advancement.id().getNamespace().equals(AnyBodyParts.MOD_ID) && advancement.id().getPath().startsWith("body/") && (!BodyConfig.get().enabled || !BodyConfig.get().features.achievements)) cir.setReturnValue(false);
    }
}
