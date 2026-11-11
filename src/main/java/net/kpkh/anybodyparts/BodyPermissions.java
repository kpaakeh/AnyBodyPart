package net.kpkh.anybodyparts;

import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.PermissionLevel;

public final class BodyPermissions {
    private BodyPermissions() {}
    public static boolean admin(PermissionContextOwner source, String node) {
        return source.checkPermission(Identifier.fromNamespaceAndPath(AnyBodyParts.MOD_ID, node), PermissionLevel.byId(BodyConfig.get().adminPermissionLevel));
    }
    public static boolean allow(PermissionContextOwner source, String node, boolean fallback) {
        return source.checkPermission(Identifier.fromNamespaceAndPath(AnyBodyParts.MOD_ID, node), fallback);
    }
    public static boolean set(PermissionContextOwner source, BodyPart part) {
        return allow(source, "command.set." + part.name().toLowerCase(java.util.Locale.ROOT), admin(source, "command.set"));
    }
}
