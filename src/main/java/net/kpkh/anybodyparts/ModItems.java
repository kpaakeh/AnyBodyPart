package net.kpkh.anybodyparts;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;

public final class ModItems {
    public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(AnyBodyParts.MOD_ID, "main"));
    private static final ResourceKey<Item> SAW_KEY = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(AnyBodyParts.MOD_ID, "limb_saw"));
    public static final Item LIMB_SAW = Registry.register(BuiltInRegistries.ITEM, SAW_KEY,
        new SawItem(new Item.Properties().setId(SAW_KEY).durability(256).repairable(Items.IRON_INGOT)));
    private static final ResourceKey<Item> NEEDLE_KEY = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(AnyBodyParts.MOD_ID, "sewing_needle"));
    public static final Item SEWING_NEEDLE = Registry.register(BuiltInRegistries.ITEM, NEEDLE_KEY,
        new SewingNeedleItem(new Item.Properties().setId(NEEDLE_KEY).durability(128).repairable(Items.IRON_NUGGET)));
    public static final java.util.Map<BodyPart, Item> PARTS = new java.util.EnumMap<>(BodyPart.class);
    public static final java.util.Map<BodyPart, Item> MECHANICAL = new java.util.EnumMap<>(BodyPart.class);
    static {
        for (BodyPart part : BodyPart.values()) {
            var key = ResourceKey.<Item>create(Registries.ITEM, Identifier.fromNamespaceAndPath(AnyBodyParts.MOD_ID, "detached_" + part.name().toLowerCase(java.util.Locale.ROOT)));
            var mechanicalKey = ResourceKey.<Item>create(Registries.ITEM, Identifier.fromNamespaceAndPath(AnyBodyParts.MOD_ID, "mechanical_" + part.name().toLowerCase(java.util.Locale.ROOT)));
            MECHANICAL.put(part, Registry.register(BuiltInRegistries.ITEM, mechanicalKey, new MechanicalPartItem(new Item.Properties().setId(mechanicalKey).stacksTo(1), part)));
            PARTS.put(part, Registry.register(BuiltInRegistries.ITEM, key, new DetachedPartItem(new Item.Properties().setId(key).stacksTo(1), part)));
        }
    }
    private ModItems() {}
    public static void initialize() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY,
            net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab.builder()
                .title(net.minecraft.network.chat.Component.translatable("itemGroup.anybodyparts"))
                .icon(() -> new ItemStack(LIMB_SAW))
                .displayItems((parameters, output) -> {
                    output.accept(LIMB_SAW); output.accept(SEWING_NEEDLE); output.accept(Items.STRING);
                    var profile = net.minecraft.world.item.component.ResolvableProfile.createResolved(new com.mojang.authlib.GameProfile(new java.util.UUID(0, 0), "Creative"));
                    for (var part : BodyPart.values()) output.accept(DetachedPartItem.create(part, profile));
                    for (var part : BodyPart.values()) output.accept(MECHANICAL.get(part));
                }).build());
    }
}
