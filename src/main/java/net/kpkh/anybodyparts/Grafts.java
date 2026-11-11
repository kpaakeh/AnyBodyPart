package net.kpkh.anybodyparts;

import com.mojang.serialization.*;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ResolvableProfile;

public final class Grafts {
    private static final Codec<BodyPart> PART_CODEC = Codec.STRING.comapFlatMap(name -> {
        try { return DataResult.success(BodyConfig.part(name)); }
        catch (IllegalArgumentException e) { return DataResult.error(e::getMessage); }
    }, part -> part.name().toLowerCase(Locale.ROOT));
    public static final Codec<Map<BodyPart, ResolvableProfile>> CODEC = Codec.unboundedMap(PART_CODEC, ResolvableProfile.CODEC);
    public static final AttachmentType<Map<BodyPart, ResolvableProfile>> DATA = AttachmentRegistry.create(
        Identifier.fromNamespaceAndPath(AnyBodyParts.MOD_ID, "grafts"), builder -> builder.initializer(Map::of)
            .persistent(CODEC).copyOnDeath().syncWith(ByteBufCodecs.fromCodec(CODEC), AttachmentSyncPredicate.all()));
    private Grafts() {}
    public static Map<BodyPart, ResolvableProfile> get(Player player) { return player.getAttachedOrElse(DATA, Map.of()); }
    public static ResolvableProfile donor(Player player, BodyPart part) {
        return get(player).getOrDefault(part, ResolvableProfile.createResolved(player.getGameProfile()));
    }
    public static void put(Player player, BodyPart part, ResolvableProfile profile) {
        var map = new EnumMap<BodyPart, ResolvableProfile>(BodyPart.class);
        map.putAll(get(player)); map.put(part, profile);
        player.setAttached(DATA, Map.copyOf(map));
    }
    public static void removeMissing(Player player, BodyState body) {
        var map = new EnumMap<BodyPart, ResolvableProfile>(BodyPart.class);
        map.putAll(get(player));
        if (map.keySet().removeIf(part -> !body.has(part))) player.setAttached(DATA, Map.copyOf(map));
    }
    public static void remove(Player player, BodyPart part) {
        var map = new EnumMap<BodyPart, ResolvableProfile>(BodyPart.class);
        map.putAll(get(player)); map.remove(part); player.setAttached(DATA, Map.copyOf(map));
    }
    public static void clear(Player player) { player.setAttached(DATA, Map.of()); }
}
