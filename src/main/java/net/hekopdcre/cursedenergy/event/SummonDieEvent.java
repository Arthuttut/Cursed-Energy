package net.hekopdcre.cursedenergy.event;

import net.hekopdcre.cursedenergy.abilities.SummonAbility;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public class SummonDieEvent {

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        SummonAbility.forceCleanup(player.getUUID(), serverLevel);
    }
}