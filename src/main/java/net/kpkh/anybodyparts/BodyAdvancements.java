package net.kpkh.anybodyparts;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public final class BodyAdvancements {
    private BodyAdvancements() {}
    public static void award(ServerPlayer player, String name) {
        grant(player, "root", "participated");
        grant(player, name, "done");
    }
    private static void grant(ServerPlayer player, String name, String criterion) {
        var advancement = player.level().getServer().getAdvancements().get(Identifier.fromNamespaceAndPath(AnyBodyParts.MOD_ID, "body/" + name));
        if (advancement != null) player.getAdvancements().award(advancement, criterion);
    }
}
