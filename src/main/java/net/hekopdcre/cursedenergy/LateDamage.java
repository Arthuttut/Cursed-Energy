package net.hekopdcre.cursedenergy;

import net.hekopdcre.cursedenergy.abilities.CursedEnergyAbilities;
import net.hekopdcre.cursedenergy.abilities.DivergentFistAbility;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityUtil;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = "ce")
public class LateDamage {

    private static final Map<LivingEntity, LateDamageData> LATE_DAMAGE = new WeakHashMap<>();

    @SubscribeEvent
    public static void onEntityDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        Entity attacker = event.getSource().getEntity();

        if (!(attacker instanceof ServerPlayer player))
            return;
        if (target == null || !target.isAlive())
            return;

        AbilityInstance<DivergentFistAbility> instance = AbilityUtil.getEnabledInstances(
                player,
                CursedEnergyAbilities.DIVERGENT_FIST.get()).stream().findFirst().orElse(null);

        if (instance == null)
            return;

        DivergentFistAbility ability = instance.getAbility();
        if (ability == null)
            return;

        // Aplica o dano inicial imediatamente
        if (ability.initialDamage > 0 && target.level() instanceof ServerLevel level) {
            target.hurtServer(
                    level,
                    level.damageSources().magic(),
                    (float) ability.initialDamage);
        }

        // Registra o dano atrasado (não reseta se já estiver marcado)
        LATE_DAMAGE.putIfAbsent(
                target,
                new LateDamageData(
                        ability.delayTicks,
                        ability.extraDamage,
                        ability.particleColor));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (LATE_DAMAGE.isEmpty())
            return;

        Iterator<Map.Entry<LivingEntity, LateDamageData>> iterator = LATE_DAMAGE.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<LivingEntity, LateDamageData> entry = iterator.next();
            LivingEntity entity = entry.getKey();
            LateDamageData data = entry.getValue();

            if (entity == null || !entity.isAlive()) {
                iterator.remove();
                continue;
            }

            data.timer--;
            if (data.timer > 0)
                continue;

            if (entity.level() instanceof ServerLevel level) {
                level.sendParticles(
                        data.particle,
                        entity.getX(),
                        entity.getY() + entity.getBbHeight() * 0.5,
                        entity.getZ(),
                        35,
                        0.35,
                        0.35,
                        0.35,
                        0.08);
                entity.hurtServer(
                        level,
                        level.damageSources().magic(),
                        (float) data.damage);
            }

            iterator.remove();
        }
    }

    private static class LateDamageData {
        int timer;
        final double damage;
        final DustParticleOptions particle;

        LateDamageData(int timer, double damage, int color) {
            this.timer = timer;
            this.damage = damage;
            this.particle = new DustParticleOptions(color, 1.3F);
        }
    }
}