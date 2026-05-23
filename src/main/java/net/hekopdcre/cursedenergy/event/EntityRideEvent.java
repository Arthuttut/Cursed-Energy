package net.hekopdcre.cursedenergy.event;

import net.hekopdcre.cursedenergy.CursedEnergy;
import net.hekopdcre.cursedenergy.abilities.CursedEnergyAbilities;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.threetag.palladium.power.ability.AbilityUtil;

@EventBusSubscriber(modid = CursedEnergy.MOD_ID)
public class EntityRideEvent {

    @SubscribeEvent
    public static void onRightClickEntity(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        Entity target = event.getTarget();

        if (player.level().isClientSide())
            return;

        if (target instanceof Player)
            return;

        if (player.isPassenger())
            return;

        if (!AbilityUtil.isTypeEnabled(player, CursedEnergyAbilities.ENTITY_RIDE.get()))
            return;

        player.startRiding(target);
        event.setCanceled(true);
    }
}