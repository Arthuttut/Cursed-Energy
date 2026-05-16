package net.hekopdcre.cursedenergy;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.threetag.palladium.power.ability.AbilityUtil;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class LateDamage {

    private static final Identifier CURSED_ENERGY_POWER =
            Identifier.parse("ce:cursed_energy_control");

    private static final String DIVERGENT_FIST_ABILITY = "divergent_fist";

    private static final int DELAY_TICKS = 20;
    private static final int DAMAGE_AMOUNT = 3;

    private static final int PARTICLE_COLOR = 0x00CCFF;

    private static final Map<UUID, Integer> LATE_DAMAGE_ENTITIES = new HashMap<>();

    @SubscribeEvent
    public static void onEntityDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        Entity attacker = event.getSource().getEntity();

        if (!(attacker instanceof ServerPlayer player)) {
            return;
        }

        if (!hasCursedEnergyHandEnabled(player)) {
            return;
        }

        applyLateDamage(target);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();

        Iterator<Map.Entry<UUID, Integer>> iterator = LATE_DAMAGE_ENTITIES.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();

            UUID entityUuid = entry.getKey();
            int timer = entry.getValue() - 1;

            if (timer > 0) {
                entry.setValue(timer);
                continue;
            }

            LivingEntity entity = findLivingEntity(server, entityUuid);

            if (entity != null && entity.isAlive()) {
                processLateDamage(server, entity);
            }

            iterator.remove();
        }
    }

    private static boolean hasCursedEnergyHandEnabled(ServerPlayer player) {
        return AbilityUtil.isEnabled(
                player,
                CURSED_ENERGY_POWER,
                DIVERGENT_FIST_ABILITY
        );
    }

    private static void applyLateDamage(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) {
            return;
        }

        LATE_DAMAGE_ENTITIES.put(entity.getUUID(), DELAY_TICKS);
    }

    private static LivingEntity findLivingEntity(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);

            if (entity instanceof LivingEntity livingEntity) {
                return livingEntity;
            }
        }

        return null;
    }

    private static void processLateDamage(MinecraftServer server, LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        double x = entity.getX();
        double y = entity.getY() + entity.getBbHeight() * 0.5D;
        double z = entity.getZ();

        level.sendParticles(
                new DustParticleOptions(PARTICLE_COLOR, 1.3F),
                x,
                y,
                z,
                35,
                0.35D,
                0.35D,
                0.35D,
                0.08D
        );

        runServerCommand(
                server,
                "damage " + entity.getUUID() + " " + DAMAGE_AMOUNT + " minecraft:magic"
        );
    }

    private static void runServerCommand(MinecraftServer server, String command) {
        CommandSourceStack source = server.createCommandSourceStack()
                .withSuppressedOutput();

        server.getCommands().performPrefixedCommand(source, command);
    }
}