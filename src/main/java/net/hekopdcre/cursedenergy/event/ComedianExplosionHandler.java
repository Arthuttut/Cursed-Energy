package net.hekopdcre.cursedenergy.event;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import net.threetag.palladium.power.PowerUtil;

@EventBusSubscriber(modid = "ce")
public class ComedianExplosionHandler {

    private static final Identifier COMEDIAN =
            Identifier.parse("ce:comedian");

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {

        // Verifica jogador
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Detecta dano de explosão
        boolean explosion =
                event.getSource().is(DamageTypes.EXPLOSION)
             || event.getSource().is(DamageTypes.PLAYER_EXPLOSION);

        if (!explosion) {
            return;
        }

        // Verifica poder comedian
        if (!PowerUtil.hasPower(player, COMEDIAN)) {
            return;
        }

        // Cancela dano
        event.setCanceled(true);

        // Segurança
        if (player.level().getServer() == null) {
            return;
        }

        // Para o som da explosão
        player.level().getServer().getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withSuppressedOutput(),
                "stopsound @s * minecraft:entity.generic.explode"
        );

        // Som cartoon/feliz
        player.level().getServer().getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withSuppressedOutput(),
                "playsound minecraft:entity.firework_rocket.twinkle player @s ~ ~ ~ 3 1.7"
        );

        // Executa confetti
        player.level().getServer().getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withSuppressedOutput(),
                "function ce:confetti"
        );
    }
}