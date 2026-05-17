package net.hekopdcre.cursedenergy;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.threetag.palladium.power.ability.AbilityUtil;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

public class LateDamage {

    private static final Identifier CURSED_ENERGY_POWER = Identifier.parse("ce:cursed_energy_control");

    private static final String DIVERGENT_FIST_ABILITY = "divergent_fist";

    private static final int DELAY_TICKS = 20;
    private static final float DAMAGE_AMOUNT = 3.0F;

    // FIX: DustParticleOptions cacheado — evita criar objeto novo a cada hit
    private static final DustParticleOptions DIVERGENT_PARTICLE = new DustParticleOptions(0x00CCFF, 1.3F);

    // FIX: WeakHashMap — GC limpa entidades mortas/descarregadas automaticamente,
    // sem memory leak
    private static final Map<LivingEntity, Integer> LATE_DAMAGE = new WeakHashMap<>();

    @SubscribeEvent
    public static void onEntityDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        Entity attacker = event.getSource().getEntity();

        if (!(attacker instanceof ServerPlayer player))
            return;
        if (!hasCursedEnergyHandEnabled(player))
            return;
        if (target == null || !target.isAlive())
            return;

        // putIfAbsent — primeiro hit marca o timer, spam subsequente não reseta
        LATE_DAMAGE.putIfAbsent(target, DELAY_TICKS);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (LATE_DAMAGE.isEmpty())
            return;

        Iterator<Map.Entry<LivingEntity, Integer>> iterator = LATE_DAMAGE.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<LivingEntity, Integer> entry = iterator.next();
            LivingEntity entity = entry.getKey();

            // Entidade morreu antes do timer — limpa sem dar dano
            if (!entity.isAlive()) {
                iterator.remove();
                continue;
            }

            int timer = entry.getValue() - 1;

            if (timer > 0) {
                entry.setValue(timer);
                continue;
            }

            // Timer zerou — aplica dano e partículas
            if (entity.level() instanceof ServerLevel level) {
                double x = entity.getX();
                double y = entity.getY() + entity.getBbHeight() * 0.5;
                double z = entity.getZ();

                level.sendParticles(
                        DIVERGENT_PARTICLE,
                        x, y, z,
                        35,
                        0.35, 0.35, 0.35,
                        0.08);

                // FIX: API direta em vez de comando — sem parser, sem string, sem UUID lookup
                entity.hurtServer(
                        level,
                        level.damageSources().magic(),
                        DAMAGE_AMOUNT);
            }

            iterator.remove();
        }
    }

    private static boolean hasCursedEnergyHandEnabled(ServerPlayer player) {
        return AbilityUtil.isEnabled(
                player,
                CURSED_ENERGY_POWER,
                DIVERGENT_FIST_ABILITY);
    }
}