package net.kpkh.anybodyparts;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.resource.conditions.v1.*;
import net.minecraft.resources.*;

public record FeatureCondition(String feature) implements ResourceCondition {
    public static final ResourceConditionType<FeatureCondition> TYPE = ResourceConditionType.create(
        Identifier.fromNamespaceAndPath(AnyBodyParts.MOD_ID, "feature"), Codec.STRING.fieldOf("feature").xmap(FeatureCondition::new, FeatureCondition::feature));
    public static void register() { ResourceConditions.register(TYPE); }
    @Override public ResourceConditionType<?> getType() { return TYPE; }
    @Override public boolean test(RegistryOps.RegistryInfoLookup lookup) {
        var c = BodyConfig.get();
        if (!c.enabled) return false;
        if (feature.equals("saw")) return c.saw.enabled && c.crafting.saw;
        if (feature.equals("needle")) return c.sewing.enabled && c.crafting.needle;
        if (feature.startsWith("mechanical_")) {
            var part = BodyConfig.part(feature.substring(11));
            return c.mechanical.enabled && c.mechanical.craftingEnabled
                && ((BodyConfig.mask(c.mechanical.disabledParts) | BodyConfig.mask(c.mechanical.disabledCrafts) | c.rules().disabledMask()) & part.bit()) == 0;
        }
        throw new IllegalArgumentException("Unknown Any Body Parts feature: " + feature);
    }
}
