package net.hekopdcre.cursedenergy.abilities;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.hekopdcre.cursedenergy.entity.custom.RikaEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.ability.*;
import net.threetag.palladium.power.energybar.EnergyBarUsage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SummonAbility extends Ability {

    private static final Map<UUID, UUID> ACTIVE_SUMMONS = new HashMap<>();
    private static final Map<UUID, Integer> ACTIVATION_TICKS = new HashMap<>();

    public static final MapCodec<SummonAbility> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec(),
                    Identifier.CODEC.fieldOf("entity")
                            .forGetter(a -> a.entityId),
                    Codec.STRING.fieldOf("spawn_side")
                            .orElse("front")
                            .forGetter(a -> a.spawnSide),
                    Codec.DOUBLE.fieldOf("spawn_distance")
                            .orElse(2.0)
                            .forGetter(a -> a.spawnDistance),
                    Codec.INT.fieldOf("spawn_delay_ticks")
                            .orElse(0)
                            .forGetter(a -> a.spawnDelayTicks),
                    Codec.BOOL.fieldOf("tame")
                            .orElse(false)
                            .forGetter(a -> a.tame),
                    Identifier.CODEC.fieldOf("spawn_particle")
                            .orElse(Identifier.parse("minecraft:portal"))
                            .forGetter(a -> a.spawnParticle),
                    Codec.INT.fieldOf("spawn_particle_count")
                            .orElse(5)
                            .forGetter(a -> a.spawnParticleCount),
                    Codec.BOOL.fieldOf("freeze_until_spawn")
                            .orElse(false)
                            .forGetter(a -> a.freezeUntilSpawn),
                    Codec.BOOL.fieldOf("show_particles")
                            .orElse(true)
                            .forGetter(a -> a.showParticles))
            .apply(instance, SummonAbility::create));

    public final Identifier entityId;
    public final String spawnSide;
    public final double spawnDistance;
    public final int spawnDelayTicks;
    public final boolean tame;
    public final Identifier spawnParticle;
    public final int spawnParticleCount;
    public final boolean freezeUntilSpawn;
    public final boolean showParticles;

    public SummonAbility(
            AbilityProperties properties,
            AbilityStateManager stateManager,
            List<EnergyBarUsage> energyBarUsages,
            Identifier entityId,
            String spawnSide,
            double spawnDistance,
            int spawnDelayTicks,
            boolean tame,
            Identifier spawnParticle,
            int spawnParticleCount,
            boolean freezeUntilSpawn,
            boolean showParticles) {
        super(properties, stateManager, energyBarUsages);
        this.entityId = entityId;
        this.spawnSide = spawnSide;
        this.spawnDistance = spawnDistance;
        this.spawnDelayTicks = spawnDelayTicks;
        this.tame = tame;
        this.spawnParticle = spawnParticle;
        this.spawnParticleCount = spawnParticleCount;
        this.freezeUntilSpawn = freezeUntilSpawn;
        this.showParticles = showParticles;
    }

    private static SummonAbility create(
            AbilityProperties properties,
            AbilityStateManager stateManager,
            List<EnergyBarUsage> energyBarUsages,
            Identifier entityId,
            String spawnSide,
            Double spawnDistance,
            Integer spawnDelayTicks,
            Boolean tame,
            Identifier spawnParticle,
            Integer spawnParticleCount,
            Boolean freezeUntilSpawn,
            Boolean showParticles) {
        return new SummonAbility(
                properties, stateManager, energyBarUsages,
                entityId, spawnSide,
                spawnDistance.doubleValue(),
                spawnDelayTicks.intValue(),
                tame.booleanValue(),
                spawnParticle,
                spawnParticleCount.intValue(),
                freezeUntilSpawn.booleanValue(),
                showParticles.booleanValue());
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return CursedEnergyAbilities.SUMMON.get();
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (entity.level().isClientSide())
            return;
        if (!(entity instanceof ServerPlayer player))
            return;
        ACTIVATION_TICKS.put(player.getUUID(), 0);
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (entity.level().isClientSide())
            return super.tick(entity, abilityInstance, enabled);

        if (entity instanceof ServerPlayer player && player.level() instanceof ServerLevel serverLevel) {
            UUID playerUUID = player.getUUID();

            // Cleanup quando desativado — cobre morte, desativação manual, qualquer motivo
            if (!enabled) {
                if (ACTIVATION_TICKS.containsKey(playerUUID) || ACTIVE_SUMMONS.containsKey(playerUUID)) {
                    forceCleanup(playerUUID, serverLevel);
                }
                return super.tick(entity, abilityInstance, enabled);
            }

            Integer activationTick = ACTIVATION_TICKS.get(playerUUID);

            if (activationTick != null) {
                // Partículas durante todo o período de espera
                if (showParticles && activationTick <= spawnDelayTicks) {
                    double[] offset = getSpawnOffset(player, spawnSide, spawnDistance);
                    SimpleParticleType particleType = getParticleType();
                    if (particleType != null) {
                        serverLevel.sendParticles(particleType,
                                player.getX() + offset[0],
                                player.getY() + 1.0,
                                player.getZ() + offset[2],
                                spawnParticleCount, 0.2, 0.5, 0.2, 0.05);
                    }
                }

                // Freeze durante o delay
                if (freezeUntilSpawn && activationTick < spawnDelayTicks) {
                    player.setDeltaMovement(Vec3.ZERO);
                    player.hurtMarked = true;
                }

                // Spawn quando o contador próprio atinge o delay
                if (activationTick >= spawnDelayTicks) {
                    ACTIVATION_TICKS.remove(playerUUID);
                    spawnEntity(player, serverLevel);
                } else {
                    ACTIVATION_TICKS.put(playerUUID, activationTick + 1);
                }
            }
        }

        return super.tick(entity, abilityInstance, enabled);
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player))
            return;
        if (!(player.level() instanceof ServerLevel serverLevel))
            return;

        forceCleanup(player.getUUID(), serverLevel);
    }

    // -----------------------------------------------------------------------
    // Cleanup público estático — chamável por eventos externos (morte, logout)
    // -----------------------------------------------------------------------

    public static void forceCleanup(UUID playerUUID, ServerLevel serverLevel) {
        ACTIVATION_TICKS.remove(playerUUID);
        UUID summonUUID = ACTIVE_SUMMONS.remove(playerUUID);

        if (summonUUID == null) {
            System.out.println("[SummonAbility] forceCleanup: nenhum summon registrado para " + playerUUID);
            return;
        }

        Entity e = serverLevel.getEntity(summonUUID);
        if (e != null && e.isAlive()) {
            e.remove(Entity.RemovalReason.DISCARDED);
            System.out.println("[SummonAbility] descartado: " + summonUUID);
        } else {
            System.out.println("[SummonAbility] forceCleanup: entidade já não existia (" + summonUUID + ")");
        }
    }

    // -----------------------------------------------------------------------
    // Spawn
    // -----------------------------------------------------------------------

    private void spawnEntity(ServerPlayer player, ServerLevel serverLevel) {
        EntityType<?> entityType = getEntityType();
        if (entityType == null) {
            System.out.println("[SummonAbility] entityType null para: " + entityId);
            return;
        }

        UUID playerUUID = player.getUUID();
        UUID existingUUID = ACTIVE_SUMMONS.get(playerUUID);
        if (existingUUID != null) {
            Entity existing = serverLevel.getEntity(existingUUID);
            if (existing != null && existing.isAlive()) {
                System.out.println("[SummonAbility] summon já ativo (" + existingUUID + "), abortando");
                return;
            }
            ACTIVE_SUMMONS.remove(playerUUID);
        }

        double[] offset = getSpawnOffset(player, spawnSide, spawnDistance);
        double x = player.getX() + offset[0];
        double y = player.getY();
        double z = player.getZ() + offset[2];
        float yRot = player.getYRot();

        final LivingEntity[] holder = new LivingEntity[1];
        entityType.create(serverLevel, e -> {
            if (e instanceof LivingEntity le)
                holder[0] = le;
            e.setPos(x, y, z);
            e.setYRot(yRot);
        }, BlockPos.containing(x, y, z), EntitySpawnReason.MOB_SUMMONED, false, false);

        LivingEntity summoned = holder[0];
        if (summoned == null) {
            System.out.println("[SummonAbility] holder[0] null após create");
            return;
        }

        if (tame) {
            applyOwnership(summoned, player);
        }

        serverLevel.addFreshEntity(summoned);

        ACTIVE_SUMMONS.put(playerUUID, summoned.getUUID());
        System.out.println("[SummonAbility] spawnado: " + entityId + " uuid=" + summoned.getUUID());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private SimpleParticleType getParticleType() {
        try {
            ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE
                    .getOptional(spawnParticle).orElse(null);
            if (type instanceof SimpleParticleType simple)
                return simple;
        } catch (Exception ignored) {
        }
        return ParticleTypes.PORTAL;
    }

    private static void applyOwnership(LivingEntity entity, ServerPlayer player) {
        if (entity instanceof RikaEntity rika) {
            rika.setOwnerUUID(player.getUUID());
            return;
        }
        if (entity instanceof TamableAnimal tamable) {
            tamable.tame(player);
            return;
        }
        try {
            entity.getClass().getMethod("setOwnerUUID", UUID.class).invoke(entity, player.getUUID());
        } catch (Exception ignored) {
        }
    }

    private EntityType<?> getEntityType() {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).orElse(null);
    }

    private static double[] getSpawnOffset(ServerPlayer player, String side, double dist) {
        double yaw = Math.toRadians(player.getYRot());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);
        return switch (side.toLowerCase()) {
            case "back" -> new double[] { -forwardX * dist, 0, -forwardZ * dist };
            case "left" -> new double[] { -rightX * dist, 0, -rightZ * dist };
            case "right" -> new double[] { rightX * dist, 0, rightZ * dist };
            default -> new double[] { forwardX * dist, 0, forwardZ * dist };
        };
    }
}