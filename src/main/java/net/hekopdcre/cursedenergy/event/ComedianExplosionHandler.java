package net.hekopdcre.cursedenergy.event;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.threetag.palladium.power.PowerUtil;

import java.util.Random;

@EventBusSubscriber(modid = "ce")
public class ComedianExplosionHandler {

        private static final Identifier COMEDIAN = Identifier.parse("ce:comedian");

        private static final Random RANDOM = new Random();

        private static final int[] CONFETTI_COLORS = {
                        0xFFFF3333,
                        0xFF33FF33,
                        0xFF3333FF,
                        0xFFFFFF33,
                        0xFFFF33FF,
                        0xFF33FFFF,
                        0xFFFF9922
        };

        // CANCELA DANO DE EXPLOSÃO
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void onDamage(LivingIncomingDamageEvent event) {
                if (!(event.getEntity() instanceof ServerPlayer player))
                        return;

                boolean explosion = event.getSource().is(DamageTypes.EXPLOSION)
                                || event.getSource().is(DamageTypes.PLAYER_EXPLOSION);

                if (!explosion)
                        return;
                if (!PowerUtil.hasPower(player, COMEDIAN))
                        return;

                event.setCanceled(true);
        }

        // CONTROLE PRINCIPAL DA EXPLOSÃO
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void onExplosionStart(ExplosionEvent.Start event) {
                if (!(event.getExplosion().getDirectSourceEntity() instanceof Creeper))
                        return;
                if (!(event.getLevel() instanceof ServerLevel level))
                        return;

                Vec3 center = event.getExplosion().center();

                boolean hasComedian = level.players().stream()
                                .filter(p -> p instanceof ServerPlayer sp)
                                .anyMatch(p -> PowerUtil.hasPower(p, COMEDIAN) &&
                                                p.distanceToSqr(center.x, center.y, center.z) < 100);

                if (!hasComedian)
                        return;

                // cancela explosão ANTES dela acontecer (mata som original também)
                event.setCanceled(true);

                // efeito de confete + som substituto
                spawnConfetti(level, center);
        }

        // CONFETE + SOM DE FOGOS
        private static void spawnConfetti(ServerLevel level, Vec3 pos) {

                for (int i = 0; i < 120; i++) {
                        int color = CONFETTI_COLORS[RANDOM.nextInt(CONFETTI_COLORS.length)];
                        DustParticleOptions dust = new DustParticleOptions(color, 1.2f);

                        level.sendParticles(
                                        dust,
                                        pos.x + (RANDOM.nextDouble() - 0.5),
                                        pos.y + RANDOM.nextDouble(),
                                        pos.z + (RANDOM.nextDouble() - 0.5),
                                        1,
                                        0,
                                        0.25,
                                        0,
                                        0.05);
                }

                // som principal (explosão de firework)
                level.playSound(
                                null,
                                pos.x, pos.y, pos.z,
                                SoundEvents.FIREWORK_ROCKET_BLAST,
                                SoundSource.PLAYERS,
                                3.0f,
                                1.8f);

                // estalo final
                level.playSound(
                                null,
                                pos.x, pos.y, pos.z,
                                SoundEvents.FIREWORK_ROCKET_TWINKLE,
                                SoundSource.PLAYERS,
                                2.5f,
                                1.6f);
        }

        // REMOVE QUALQUER DANO DE BLOCO (FALLBACK)
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
                if (!(event.getLevel() instanceof ServerLevel level))
                        return;

                Vec3 center = event.getExplosion().center();

                boolean hasComedian = level.players().stream()
                                .filter(p -> p instanceof ServerPlayer sp)
                                .anyMatch(p -> PowerUtil.hasPower(p, COMEDIAN) &&
                                                p.distanceToSqr(center.x, center.y, center.z) < 100);

                if (!hasComedian)
                        return;

                event.getAffectedBlocks().clear();
        }

        // SEM KNOCKBACK
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void onKnockback(LivingKnockBackEvent event) {
                if (!(event.getEntity() instanceof ServerPlayer player))
                        return;
                if (!PowerUtil.hasPower(player, COMEDIAN))
                        return;

                event.setStrength(0.0F);
        }
}