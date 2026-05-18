package net.hekopdcre.cursedenergy.event;

import net.hekopdcre.cursedenergy.network.BeeRideInputPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.bee.Bee;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.threetag.palladium.power.PowerUtil;

@EventBusSubscriber(modid = "ce")
public class BeeRideEvent {

    private static final Identifier COMEDIAN = Identifier.parse("ce:comedian");

    public static void registerPackets(IEventBus modEventBus) {
        modEventBus.addListener((RegisterPayloadHandlersEvent e) ->
            e.registrar("1").playToServer(
                BeeRideInputPacket.TYPE,
                BeeRideInputPacket.CODEC,
                BeeRideInputPacket::handle
            )
        );
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player))
            return;
        if (!(event.getTarget() instanceof Bee bee))
            return;
        if (!PowerUtil.hasPower(player, COMEDIAN))
            return;
        if (player.isPassenger())
            return;

        event.setCanceled(true);
        player.startRiding(bee, true, true);
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Bee bee))
            return;

        ServerPlayer player = (ServerPlayer) bee.getPassengers()
                .stream()
                .filter(e -> e instanceof ServerPlayer)
                .findFirst()
                .orElse(null);

        if (player == null)
            return;

        BeeRideInputPacket input = BeeRideInputPacket.INPUTS.get(player.getUUID());
        if (input == null)
            return;

        float speed = 0.8f;
        double yaw  = Math.toRadians(player.getYRot());

        double dx = (-Math.sin(yaw) * input.forward() + Math.cos(yaw) * input.strafe()) * speed;
        double dz = ( Math.cos(yaw) * input.forward() + Math.sin(yaw) * input.strafe()) * speed;

        double dy = -0.05; // leve gravidade para não flutuar
        if (input.jump())    dy =  speed; // espaço = sobe
        if (input.descend()) dy = -speed; // ctrl = desce

        bee.setDeltaMovement(dx, dy, dz);
        bee.hurtMarked = true;
        bee.setYRot(player.getYRot());
        bee.yRotO = player.getYRot();

        if (!player.isPassenger())
            BeeRideInputPacket.INPUTS.remove(player.getUUID());
    }
}