package net.kpkh.anybodyparts;

import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class BodyAdmin {
    private BodyAdmin() {}
    public static boolean change(ServerPlayer target, BodyPart part, boolean enabled) {
        if (enabled && (BodyConfig.get().rules().disabledMask() & part.bit()) != 0) {
            target.sendOverlayMessage(Component.translatable("message.anybodyparts.global_disabled"));
            return false;
        }
        return BodyParts.set(target, BodyParts.stored(target).with(part, enabled));
    }
    public static void send(ServerPlayer viewer, ServerPlayer target, boolean open) {
        if (!ServerPlayNetworking.canSend(viewer, AdminStatePayload.TYPE)) return;
        int editable = 0;
        int forced = BodyConfig.get().rules().disabledMask();
        int protectedMask = BodyConfig.mask(BodyConfig.get().global.uncuttableParts);
        for (var part : BodyPart.values()) {
            boolean protectedPresent = (protectedMask & part.bit()) != 0 && BodyParts.stored(target).has(part);
            if (BodyPermissions.set(viewer, part) && !protectedPresent && (forced & part.bit()) == 0) editable |= part.bit();
        }
        ServerPlayNetworking.send(viewer, new AdminStatePayload(target.getGameProfile().name(), BodyParts.stored(target).mask(),
            BodyParts.get(target).mask(), editable, BodyPermissions.admin(viewer, "command.reset"), open));
    }
    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(AdminRequestPayload.TYPE, AdminRequestPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AdminStatePayload.TYPE, AdminStatePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(AdminRequestPayload.TYPE, (request, context) -> {
            ServerPlayer viewer = context.player();
            if (!BodyPermissions.admin(viewer, "command.menu")) { deny(viewer); return; }
            int action = request.part();
            boolean allowed = action == -1 ? BodyPermissions.admin(viewer, "command.status")
                : action == -2 ? BodyPermissions.admin(viewer, "command.reset")
                : action >= 0 && action < BodyPart.values().length && BodyPermissions.set(viewer, BodyPart.values()[action]);
            if (!allowed) { deny(viewer); return; }
            if (!request.name().matches("[A-Za-z0-9_]{1,16}")) { deny(viewer); return; }
            ServerPlayer target = context.server().getPlayerList().getPlayerByName(request.name());
            if (target == null) { viewer.sendOverlayMessage(Component.translatable("message.anybodyparts.not_online")); return; }
            boolean success = true;
            if (action == -2) success = BodyParts.reset(target);
            else if (action >= 0) success = change(target, BodyPart.values()[action], request.enabled());
            if (!success) viewer.sendOverlayMessage(Component.translatable("message.anybodyparts.change_failed"));
            send(viewer, target, false);
        });
    }
    private static void deny(ServerPlayer player) { player.sendOverlayMessage(Component.translatable("message.anybodyparts.no_permission")); }
}
