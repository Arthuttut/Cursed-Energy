package net.hekopdcre.cursedenergy.client;

import net.hekopdcre.cursedenergy.network.BeeRideInputPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.entity.animal.bee.Bee;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "ce", value = Dist.CLIENT)
public class BeeRideClientEvent {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null)
            return;
        if (mc.getConnection() == null)
            return;
        if (!(player.getVehicle() instanceof Bee))
            return;

        boolean forward  = mc.options.keyUp.isDown();
        boolean backward = mc.options.keyDown.isDown();
        boolean left     = mc.options.keyLeft.isDown();
        boolean right    = mc.options.keyRight.isDown();
        boolean jump     = mc.options.keyJump.isDown();
        boolean sprint   = mc.options.keySprint.isDown(); // ✅ Ctrl = descer

        float forwardImpulse = (forward ? 1f : 0f) - (backward ? 1f : 0f);
        float strafeImpulse  = (left   ? 1f : 0f) - (right    ? 1f : 0f);

        mc.getConnection().send(
            new ServerboundCustomPayloadPacket(
                new BeeRideInputPacket(forwardImpulse, strafeImpulse, jump, sprint)
            )
        );
    }
}