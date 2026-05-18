package net.hekopdcre.cursedenergy.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.bee.Bee;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public record BeeRideInputPacket(float forward, float strafe, boolean jump, boolean descend)
        implements CustomPacketPayload {

    public static final Type<BeeRideInputPacket> TYPE =
            new Type<>(Identifier.parse("ce:bee_ride_input"));

    public static final StreamCodec<ByteBuf, BeeRideInputPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, BeeRideInputPacket::forward,
            ByteBufCodecs.FLOAT, BeeRideInputPacket::strafe,
            ByteBufCodecs.BOOL,  BeeRideInputPacket::jump,
            ByteBufCodecs.BOOL,  BeeRideInputPacket::descend,
            (f, s, j, d) -> new BeeRideInputPacket(f, s, j, d)
    );

    public static final Map<UUID, BeeRideInputPacket> INPUTS = new ConcurrentHashMap<>();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BeeRideInputPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player))
                return;
            if (!(player.getVehicle() instanceof Bee))
                return;
            INPUTS.put(player.getUUID(), packet);
        });
    }
}