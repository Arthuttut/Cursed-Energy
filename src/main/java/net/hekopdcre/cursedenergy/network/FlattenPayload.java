package net.hekopdcre.cursedenergy.network;

import io.netty.buffer.ByteBuf;
import net.hekopdcre.cursedenergy.CursedEnergy;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record FlattenPayload(UUID playerUUID, boolean flattened) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FlattenPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(CursedEnergy.MOD_ID, "flatten"));

    public static final StreamCodec<ByteBuf, FlattenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString),
            FlattenPayload::playerUUID,
            ByteBufCodecs.BOOL,
            p -> p.flattened(),
            (uuid, flattened) -> new FlattenPayload(uuid, flattened));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}