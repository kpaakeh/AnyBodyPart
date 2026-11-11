package net.kpkh.anybodyparts;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record AdminStatePayload(String name, int mask, int effectiveMask, int editableMask, boolean canReset, boolean open) implements CustomPacketPayload {
    public static final Type<AdminStatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(AnyBodyParts.MOD_ID, "admin_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdminStatePayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, AdminStatePayload::name, ByteBufCodecs.VAR_INT, AdminStatePayload::mask,
        ByteBufCodecs.VAR_INT, AdminStatePayload::effectiveMask, ByteBufCodecs.VAR_INT, AdminStatePayload::editableMask,
        ByteBufCodecs.BOOL, AdminStatePayload::canReset, ByteBufCodecs.BOOL, AdminStatePayload::open, AdminStatePayload::new);
    @Override public Type<AdminStatePayload> type() { return TYPE; }
}
