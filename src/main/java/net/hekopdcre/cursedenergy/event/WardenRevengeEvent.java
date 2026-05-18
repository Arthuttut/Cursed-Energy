package net.hekopdcre.cursedenergy.event;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.threetag.palladium.power.PowerUtil;

import java.util.*;

@EventBusSubscriber(modid = "ce")
public class WardenRevengeEvent {

    private static final Identifier COMEDIAN = Identifier.parse("ce:comedian");

    private static final SoundEvent POP =
            SoundEvent.createVariableRangeEvent(Identifier.parse("ce:pop"));

    // tudo num único record ao invés de 3 maps separados
    private record WardenData(UUID playerUUID, Set<UUID> targets, ResourceKey<Level> dimension) {}

    // warden UUID -> WardenData
    private static final Map<UUID, WardenData> WARDEN_MAP = new HashMap<>();

    // player UUID -> warden UUID (lookup reverso)
    private static final Map<UUID, UUID> WARDEN_BY_PLAYER = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMeleeDamage(LivingIncomingDamageEvent event) {

        if (!(event.getEntity() instanceof ServerPlayer player))
            return;
        if (!(player.level() instanceof ServerLevel level))
            return;
        if (!PowerUtil.hasPower(player, COMEDIAN))
            return;

        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof LivingEntity livingAttacker))
            return;

        // distanceToSqr evita sqrt
        if (player.distanceToSqr(livingAttacker) > 4.0)
            return;

        UUID playerId = player.getUUID();

        // raio reduzido para 24 blocos
        Set<UUID> newTargets = new HashSet<>();
        level.getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(24),
                mob -> mob.getTarget() != null
                        && mob.getTarget().getUUID().equals(playerId)
                        && !(mob instanceof Warden))
                .forEach(mob -> newTargets.add(mob.getUUID()));

        newTargets.add(livingAttacker.getUUID());

        if (WARDEN_BY_PLAYER.containsKey(playerId)) {
            // warden já existe — adiciona novos targets
            UUID wardenId = WARDEN_BY_PLAYER.get(playerId);
            WardenData data = WARDEN_MAP.get(wardenId);
            if (data != null) {
                data.targets().addAll(newTargets);
                updateWardenTarget(level, wardenId, data.targets());
            }
            return;
        }

        Vec3 pos = player.position();
        final Warden[] holder = new Warden[1];

        EntityType.WARDEN.create(
                level,
                w -> {
                    holder[0] = w;
                    w.setPos(pos.x + 2, pos.y, pos.z + 2);
                    w.setSilent(true);
                    w.increaseAngerAt(livingAttacker, 100, false);
                    w.getBrain().setMemory(
                            MemoryModuleType.ATTACK_TARGET,
                            livingAttacker); // sem Optional.of()
                    w.getBrain().eraseMemory(MemoryModuleType.ROAR_TARGET);
                    w.getBrain().eraseMemory(MemoryModuleType.SNIFF_COOLDOWN);
                },
                BlockPos.containing(pos),
                EntitySpawnReason.TRIGGERED,
                true,
                true);

        Warden warden = holder[0];
        if (warden == null)
            return;

        level.addFreshEntity(warden);

        UUID wardenId = warden.getUUID();
        WARDEN_MAP.put(wardenId, new WardenData(playerId, newTargets, level.dimension()));
        WARDEN_BY_PLAYER.put(playerId, wardenId);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {

        if (!(event.getEntity() instanceof LivingEntity dead))
            return;
        if (!(dead.level() instanceof ServerLevel level))
            return;

        UUID deadId = dead.getUUID();

        Iterator<Map.Entry<UUID, WardenData>> iterator = WARDEN_MAP.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, WardenData> entry = iterator.next();
            UUID wardenId = entry.getKey();
            WardenData data = entry.getValue();

            if (!data.targets().remove(deadId))
                continue;

            if (!data.targets().isEmpty()) {
                // ainda tem targets — passa pro próximo
                ServerLevel wardenLevel = level.getServer().getLevel(data.dimension());
                if (wardenLevel != null)
                    updateWardenTarget(wardenLevel, wardenId, data.targets());
            } else {
                // todos mortos — toca som e desaparece
                ServerLevel wardenLevel = level.getServer().getLevel(data.dimension());
                if (wardenLevel != null) {
                    Entity e = wardenLevel.getEntity(wardenId);
                    if (e instanceof Warden warden) {
                        wardenLevel.playSound(
                                null,
                                warden.getX(), warden.getY(), warden.getZ(),
                                POP,
                                SoundSource.HOSTILE,
                                2.0f, 1.2f);
                        warden.discard();
                    }
                }
                WARDEN_BY_PLAYER.remove(data.playerUUID());
                iterator.remove(); // remove seguro pelo iterator
            }

            break;
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {

        // iterator ao invés de new HashMap() a cada tick
        Iterator<Map.Entry<UUID, WardenData>> iterator = WARDEN_MAP.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, WardenData> entry = iterator.next();
            UUID wardenId = entry.getKey();
            WardenData data = entry.getValue();

            ServerLevel level = event.getServer().getLevel(data.dimension());
            if (level == null)
                continue;

            Entity e = level.getEntity(wardenId);

            // detecta warden morto/despawnado e limpa memória
            if (!(e instanceof Warden warden) || !warden.isAlive()) {
                WARDEN_BY_PLAYER.remove(data.playerUUID());
                iterator.remove();
                continue;
            }

            boolean hasTarget = warden.getBrain()
                    .getMemory(MemoryModuleType.ATTACK_TARGET)
                    .isPresent();

            if (!hasTarget && !data.targets().isEmpty())
                updateWardenTarget(level, wardenId, data.targets());
        }
    }

    private static void updateWardenTarget(ServerLevel level, UUID wardenId, Set<UUID> targets) {

        Entity e = level.getEntity(wardenId);
        if (!(e instanceof Warden warden))
            return;

        // remove targets mortos antes de iterar
        targets.removeIf(id -> {
            Entity target = level.getEntity(id);
            return !(target instanceof LivingEntity living) || !living.isAlive();
        });

        for (UUID targetId : targets) {
            Entity target = level.getEntity(targetId);
            if (target instanceof LivingEntity living && living.isAlive()) {
                warden.increaseAngerAt(living, 100, false);
                warden.getBrain().setMemory(
                        MemoryModuleType.ATTACK_TARGET,
                        living); // sem Optional.of()
                warden.getBrain().eraseMemory(MemoryModuleType.ROAR_TARGET);
                warden.getBrain().eraseMemory(MemoryModuleType.SNIFF_COOLDOWN);
                break;
            }
        }
    }
}