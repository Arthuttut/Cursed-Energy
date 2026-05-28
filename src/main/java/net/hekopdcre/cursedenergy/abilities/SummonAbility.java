package net.hekopdcre.cursedenergy.abilities;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.hekopdcre.cursedenergy.summon.ISummonedEntity;
import net.hekopdcre.cursedenergy.summon.SummonRegistry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.ability.*;
import net.threetag.palladium.power.energybar.EnergyBarUsage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SummonAbility extends Ability {

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
                    Codec.INT.fieldOf("spawn_count")
                            .orElse(1)
                            .forGetter(a -> a.spawnCount),
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
    public final int spawnCount;
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
            int spawnCount,
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
        this.spawnCount = Math.max(1, spawnCount); // mínimo 1
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
            Integer spawnCount,
            Integer spawnDelayTicks,
            Boolean tame,
            Identifier spawnParticle,
            Integer spawnParticleCount,
            Boolean freezeUntilSpawn,
            Boolean showParticles) {
        return new SummonAbility(
                properties, stateManager, energyBarUsages,
                entityId, spawnSide,
                spawnDistance,
                spawnCount,
                spawnDelayTicks,
                tame,
                spawnParticle,
                spawnParticleCount,
                freezeUntilSpawn,
                showParticles);
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return CursedEnergyAbilities.SUMMON.get();
    }

    // -----------------------------------------------------------------------
    // Helper: coleta entidades desta ability específica do player
    // -----------------------------------------------------------------------

    private List<Entity> collectEntitiesToRemove(UUID playerUUID, ServerLevel serverLevel) {
        List<Entity> toRemove = new ArrayList<>();

        EntityType<?> targetType = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).orElse(null);

        for (Entity e : serverLevel.getAllEntities()) {
            if (!e.isAlive())
                continue;

            if (e instanceof ISummonedEntity s
                    && s.getSummonOwner() != null
                    && s.getSummonOwner().equals(playerUUID)
                    && entityId.equals(s.getSummonAbilityId())) {
                toRemove.add(e);
                continue;
            }

            if (targetType != null
                    && e.getType() == targetType
                    && e instanceof TamableAnimal tamable
                    && tamable.getOwner() instanceof Player owner
                    && playerUUID.equals(owner.getUUID())) {
                toRemove.add(e);
            }
        }

        return toRemove;
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (entity.level().isClientSide())
            return;
        if (!(entity instanceof ServerPlayer player))
            return;
        if (!(player.level() instanceof ServerLevel serverLevel))
            return;

        UUID playerUUID = player.getUUID();

        List<Entity> toRemove = collectEntitiesToRemove(playerUUID, serverLevel);
        for (Entity e : toRemove) {
            e.remove(Entity.RemovalReason.DISCARDED);
        }

        SummonRegistry.cleanupPlayer(playerUUID, entityId, serverLevel);
        SummonRegistry.putActivationTick(playerUUID, entityId, 0);
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (entity.level().isClientSide())
            return super.tick(entity, abilityInstance, enabled);
        if (!(entity instanceof ServerPlayer player))
            return super.tick(entity, abilityInstance, enabled);
        if (!(player.level() instanceof ServerLevel serverLevel))
            return super.tick(entity, abilityInstance, enabled);

        UUID playerUUID = player.getUUID();

        if (!enabled) {
            SummonRegistry.removeActivationTick(playerUUID, entityId);
            SummonRegistry.cleanupPlayer(playerUUID, entityId, serverLevel);
            return super.tick(entity, abilityInstance, enabled);
        }

        Integer activationTick = SummonRegistry.getActivationTick(playerUUID, entityId);

        if (activationTick != null) {
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

            if (freezeUntilSpawn && activationTick < spawnDelayTicks) {
                player.setDeltaMovement(Vec3.ZERO);
                player.hurtMarked = true;
            }

            if (activationTick >= spawnDelayTicks) {
                SummonRegistry.removeActivationTick(playerUUID, entityId);
                spawnEntities(player, serverLevel);
            } else {
                SummonRegistry.putActivationTick(playerUUID, entityId, activationTick + 1);
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

        UUID playerUUID = player.getUUID();
        SummonRegistry.removeActivationTick(playerUUID, entityId);
        SummonRegistry.cleanupPlayer(playerUUID, entityId, serverLevel);
    }

    // -----------------------------------------------------------------------
    // Spawn — cria spawnCount entidades espalhadas ao redor do ponto base
    // -----------------------------------------------------------------------

    private void spawnEntities(ServerPlayer player, ServerLevel serverLevel) {
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).orElse(null);
        if (entityType == null) {
            System.out.println("[SummonAbility] entityType null para: " + entityId);
            return;
        }

        UUID playerUUID = player.getUUID();

        List<Entity> toRemove = collectEntitiesToRemove(playerUUID, serverLevel);
        for (Entity e : toRemove) {
            e.remove(Entity.RemovalReason.DISCARDED);
        }
        SummonRegistry.cleanupPlayer(playerUUID, entityId, serverLevel);

        double[] baseOffset = getSpawnOffset(player, spawnSide, spawnDistance);
        float yRot = player.getYRot();

        for (int i = 0; i < spawnCount; i++) {
            // Espalha as entidades em arco ao redor do ponto base
            // Se só 1, spawna direto no ponto base sem desvio
            double spreadX = spawnCount > 1 ? (i - (spawnCount - 1) / 2.0) * 1.5 : 0.0;

            double x = player.getX() + baseOffset[0] + spreadX * Math.cos(Math.toRadians(yRot));
            double y = player.getY();
            double z = player.getZ() + baseOffset[2] + spreadX * Math.sin(Math.toRadians(yRot));

            System.out.println("[SummonAbility] criando " + (i + 1) + "/" + spawnCount + ": " + entityId + " em " + x
                    + " " + y + " " + z);

            Entity created = entityType.create(serverLevel, EntitySpawnReason.MOB_SUMMONED);
            if (!(created instanceof LivingEntity summoned)) {
                System.out.println("[SummonAbility] não é LivingEntity ou é null: " + created);
                continue;
            }

            summoned.setPos(x, y, z);
            summoned.setYRot(yRot);

            if (summoned instanceof ISummonedEntity s) {
                s.setSummonOwner(playerUUID);
                s.setSummonAbilityId(entityId);
            }

            if (tame && summoned instanceof TamableAnimal tamable) {
                tamable.tame(player);
            }

            serverLevel.addFreshEntity(summoned);
            SummonRegistry.register(playerUUID, summoned.getUUID(), entityId);
            System.out.println("[SummonAbility] spawnou: " + summoned.getType().toShortString());
        }
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