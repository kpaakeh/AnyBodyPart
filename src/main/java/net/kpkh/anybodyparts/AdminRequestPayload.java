package net.kpkh.anybodyparts;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record AdminRequestPayload(String name, int part, boolean enabled) implements CustomPacketPayload {
    public static final Type<AdminRequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(AnyBodyParts.MOD_ID, "admin_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdminRequestPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, AdminRequestPayload::name, ByteBufCodecs.VAR_INT, AdminRequestPayload::part,
        ByteBufCodecs.BOOL, AdminRequestPayload::enabled, AdminRequestPayload::new);
    @Override public Type<AdminRequestPayload> type() { return TYPE; }
}
