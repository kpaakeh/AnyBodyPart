package net.kpkh.anybodyparts;

import java.util.List;
import com.google.gson.Gson;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

public record BodyRules(boolean enabled, int disabledMask, List<String> twoHandedItems, boolean restoreOnDeath,
    float withoutTorso, float hop, float crawlTwo, float crawlOne, float wriggle, float roll, MechanicalRules mechanical, boolean creativeTab, boolean allowLeglessJump, float leglessJumpStrength) {
    public static final BodyRules DEFAULT = new BodyRules(true, 0, List.of("minecraft:bow", "minecraft:crossbow"), true,
        0.75F, 0.55F, 0.32F, 0.18F, 0.06F, 0.45F, MechanicalRules.from(new BodyConfig.Mechanical()), true, true, 0.65F);
    private static final Gson GSON = new Gson();
    public String json() { return GSON.toJson(this); }
    public static BodyRules parse(String json) { return GSON.fromJson(json, BodyRules.class); }
    public BodyState effective(BodyState stored) { return enabled ? new BodyState(stored.mask() & ~disabledMask) : BodyState.COMPLETE; }
    public boolean twoHanded(ItemStack item) {
        for (String id : twoHandedItems) {
            if (id.startsWith("#") ? item.is(TagKey.create(Registries.ITEM, Identifier.parse(id.substring(1))))
                : item.is(net.minecraft.resources.ResourceKey.create(Registries.ITEM, Identifier.parse(id)))) return true;
        }
        return false;
    }
    public float speed(BodyState body) {
        return switch (body.movement()) {
            case "roll" -> roll;
            case "hop" -> hop;
            case "crawl_two" -> crawlTwo;
            case "crawl_one" -> crawlOne;
            case "wriggle" -> wriggle;
            default -> body.has(BodyPart.TORSO) ? 1F : withoutTorso;
        };
    }
}
