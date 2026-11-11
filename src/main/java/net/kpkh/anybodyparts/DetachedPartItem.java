package net.kpkh.anybodyparts;

import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;

public class DetachedPartItem extends Item {
    private final BodyPart part;
    public DetachedPartItem(Properties properties, BodyPart part) { super(properties); this.part = part; }
    public BodyPart part() { return part; }
    public static ItemStack create(BodyPart part, ResolvableProfile donor) {
        var stack = new ItemStack(ModItems.PARTS.get(part));
        stack.set(DataComponents.PROFILE, donor);
        return stack;
    }
    @Override public Component getName(ItemStack stack) {
        return Component.translatable(part.key());
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> text, TooltipFlag flag) {
        var profile = stack.get(DataComponents.PROFILE);
        text.accept(Component.translatable("item.anybodyparts.detached_part.hint"));
        if (profile == null) text.accept(Component.translatable("item.anybodyparts.detached_part.unbound"));
        else {
            text.accept(Component.translatable("item.anybodyparts.donor", profile.name().orElse(profile.partialProfile().id().toString())));
            if (flag.isAdvanced()) text.accept(Component.literal("UUID: " + profile.partialProfile().id()));
        }
    }
}
