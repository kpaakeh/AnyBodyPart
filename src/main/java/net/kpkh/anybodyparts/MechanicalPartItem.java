package net.kpkh.anybodyparts;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;

public final class MechanicalPartItem extends DetachedPartItem {
    public MechanicalPartItem(Properties properties, BodyPart part) { super(properties, part); }
    @Override public Component getName(ItemStack stack) { return Component.translatable("item.anybodyparts.mechanical_" + part().name().toLowerCase(java.util.Locale.ROOT)); }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> text, TooltipFlag flag) {
        text.accept(Component.translatable("item.anybodyparts.mechanical.hint"));
        text.accept(Component.translatable("item.anybodyparts.mechanical." + (part() == BodyPart.TORSO ? "torso" : (part().bit() & 3) != 0 ? "arm" : "leg")));
    }
}
