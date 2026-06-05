package net.hekopdcre.cursedenergy.event;

import net.hekopdcre.cursedenergy.CursedEnergy;
import net.hekopdcre.cursedenergy.abilities.CursedEnergyAbilities;
import net.hekopdcre.cursedenergy.abilities.EntityRideAbility;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
        if (!(target instanceof LivingEntity livingTarget))
            return;
        if (!AbilityUtil.isTypeEnabled(player, CursedEnergyAbilities.ENTITY_RIDE.get()))
            return;

        // Pega a primeira ability ativa e verifica as regras
        boolean allowed = AbilityUtil.getEnabledInstances(player, CursedEnergyAbilities.ENTITY_RIDE.get())
                .stream()
                .findFirst()
                .map(inst -> ((EntityRideAbility) inst.getAbility()).canRide(player, livingTarget))
                .orElse(false);

        if (!allowed)
            return;

        player.startRiding(target);
        event.setCanceled(true);
    }
}