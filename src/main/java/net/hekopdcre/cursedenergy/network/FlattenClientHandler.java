package net.hekopdcre.cursedenergy.network;

import net.hekopdcre.cursedenergy.event.FallFlattenEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class FlattenClientHandler {

    public static void handle(FlattenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null)
                return;

            if (payload.flattened()) {
                FallFlattenEvent.FLATTENED_CLIENT.add(payload.playerUUID());
            } else {
                FallFlattenEvent.FLATTENED_CLIENT.remove(payload.playerUUID());
            }

            Player player = mc.level.getPlayerByUUID(payload.playerUUID());
            if (player != null)
                player.refreshDimensions();
        });
    }
}