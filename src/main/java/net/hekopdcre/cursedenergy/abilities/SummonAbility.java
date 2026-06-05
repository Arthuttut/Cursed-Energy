package net.hekopdcre.cursedenergy.abilities;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.hekopdcre.cursedenergy.entity.goals.FollowOwnerGoal;
import net.hekopdcre.cursedenergy.entity.goals.OwnerDefendGoal;
import net.hekopdcre.cursedenergy.entity.goals.OwnerHurtTargetGoal;
import net.hekopdcre.cursedenergy.summon.ISummonedEntity;
import net.hekopdcre.cursedenergy.summon.SummonRegistry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.ability.*;
import net.threetag.palladium.power.energybar.EnergyBarUsage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

public class SummonAbility extends Ability {

    private record SpawnOptions(
            Identifier particle,
            int particleCount,
            boolean freezeUntilSpawn,
            boolean showParticles) {

        static final MapCodec<SpawnOptions> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.fieldOf("spawn_particle")
                        .orElse(Identifier.parse("minecraft:portal"))
                        .forGetter(SpawnOptions::particle),
                Codec.INT.fieldOf("spawn_particle_count")
                        .orElse(5)
                        .forGetter(SpawnOptions::particleCount),
                Codec.BOOL.fieldOf("freeze_until_spawn")
                        .orElse(false)
                        .forGetter(SpawnOptions::freezeUntilSpawn),
                Codec.BOOL.fieldOf("show_particles")
                        .orElse(true)
                        .forGetter(SpawnOptions::showParticles))
                .apply(i, SpawnOptions::new));
    }

    public static final MapCodec<SummonAbility> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(
                    propertiesCodec(), // 1
                    stateCodec(), // 2
                    energyBarUsagesCodec(), // 3
                    Identifier.CODEC.fieldOf("entity") // 4
                            .forGetter(a -> a.entityId),
                    Codec.STRING.fieldOf("spawn_side") // 5
                            .orElse("front")
                            .forGetter(a -> a.spawnSide),
                    Codec.DOUBLE.fieldOf("spawn_distance") // 6
                            .orElse(2.0)
                            .forGetter(a -> a.spawnDistance),
                    Codec.INT.fieldOf("spawn_count") // 7
                            .orElse(1)
                            .forGetter(a -> a.spawnCount),
                    Codec.INT.fieldOf("spawn_delay_ticks") // 8
                            .orElse(0)
                            .forGetter(a -> a.spawnDelayTicks),
                    Codec.BOOL.fieldOf("tame") // 9
                            .orElse(false)
                            .forGetter(a -> a.tame),
                    Codec.BOOL.fieldOf("override_ai") // 10
                            .orElse(false)
                            .forGetter(a -> a.overrideAi),
                    SpawnOptions.CODEC.forGetter(a -> new SpawnOptions( // 11
                            a.spawnParticle, a.spawnParticleCount,
                            a.freezeUntilSpawn, a.showParticles)),
                    Codec.DOUBLE.fieldOf("max_wander_distance") // 12
                            .orElse(20.0)
                            .forGetter(a -> a.maxWanderDistance),
                    Codec.BOOL.fieldOf("teleport_to_owner") // 13
                            .orElse(true)
                            .forGetter(a -> a.teleportToOwner),
                    Codec.BOOL.fieldOf("random_spawn_position") // 14
                            .orElse(false)
                            .forGetter(a -> a.randomSpawnPosition),
                    Codec.DOUBLE.fieldOf("random_spawn_radius") // 15
                            .orElse(2.0)
                            .forGetter(a -> a.randomSpawnRadius))
            .apply(instance, SummonAbility::create));

    public final Identifier entityId;
    public final String spawnSide;
    public final double spawnDistance;
    public final int spawnCount;
    public final int spawnDelayTicks;
    public final boolean tame;
    public final boolean overrideAi;
    public final Identifier spawnParticle;
    public final int spawnParticleCount;
    public final boolean freezeUntilSpawn;
    public final boolean showParticles;
    public final double maxWanderDistance;
    public final boolean teleportToOwner;
    public final boolean randomSpawnPosition;
    public final double randomSpawnRadius;

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
            boolean overrideAi,
            Identifier spawnParticle,
            int spawnParticleCount,
            boolean freezeUntilSpawn,
            boolean showParticles,
            double maxWanderDistance,
            boolean teleportToOwner,
            boolean randomSpawnPosition,
            double randomSpawnRadius) {
        super(properties, stateManager, energyBarUsages);
        this.entityId = entityId;
        this.spawnSide = spawnSide;
        this.spawnDistance = spawnDistance;
        this.spawnCount = Math.max(1, spawnCount);
        this.spawnDelayTicks = spawnDelayTicks;
        this.tame = tame;
        this.overrideAi = overrideAi;
        this.spawnParticle = spawnParticle;
        this.spawnParticleCount = spawnParticleCount;
        this.freezeUntilSpawn = freezeUntilSpawn;
        this.showParticles = showParticles;
        this.maxWanderDistance = Math.max(1.0, maxWanderDistance);
        this.teleportToOwner = teleportToOwner;
        this.randomSpawnPosition = randomSpawnPosition;
        this.randomSpawnRadius = Math.max(0.5, randomSpawnRadius);
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
            Boolean overrideAi,
            SpawnOptions spawnOptions,
            Double maxWanderDistance,
            Boolean teleportToOwner,
            Boolean randomSpawnPosition,
            Double randomSpawnRadius) {
        return new SummonAbility(
                properties, stateManager, energyBarUsages,
                entityId, spawnSide,
                spawnDistance,
                spawnCount,
                spawnDelayTicks,
                tame,
                overrideAi,
                spawnOptions.particle(),
                spawnOptions.particleCount(),
                spawnOptions.freezeUntilSpawn(),
                spawnOptions.showParticles(),
                maxWanderDistance,
                teleportToOwner,
                randomSpawnPosition,
                randomSpawnRadius);
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return CursedEnergyAbilities.SUMMON.get();
    }

    public static void putOwnerUUID(CompoundTag tag, UUID uuid) {
        tag.store("Owner", UUIDUtil.CODEC, uuid);
    }

    public static Optional<UUID> readOwnerUUID(CompoundTag tag) {
        return tag.read("Owner", UUIDUtil.CODEC);
    }

    private static void putAbilityId(CompoundTag tag, Identifier abilityId) {
        tag.putString("SummonAbilityId", abilityId.toString());
    }

    private static Optional<Identifier> readAbilityId(CompoundTag tag) {
        return tag.getString("SummonAbilityId")
                .map(Identifier::parse);
    }

    private List<Entity> collectEntitiesToRemove(UUID playerUUID, ServerLevel serverLevel) {
        List<Entity> toRemove = new ArrayList<>();
        EntityType<?> targetType = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).orElse(null);

        for (Entity e : serverLevel.getAllEntities()) {
            if (!e.isAlive())
                continue;
            if (targetType != null && e.getType() != targetType)
                continue;

            CompoundTag data = e.getPersistentData();
            readOwnerUUID(data)
                    .filter(playerUUID::equals)
                    .ifPresent(ignored -> readAbilityId(data)
                            .filter(entityId::equals)
                            .ifPresent(ignored2 -> toRemove.add(e)));
        }
        return toRemove;
    }

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
        for (Entity e : toRemove)
            e.remove(Entity.RemovalReason.DISCARDED);

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

    private static final Random RANDOM = new Random();

    private void spawnEntities(ServerPlayer player, ServerLevel serverLevel) {
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).orElse(null);
        if (entityType == null) {
            System.out.println("[SummonAbility] entityType null para: " + entityId);
            return;
        }

        UUID playerUUID = player.getUUID();

        List<Entity> toRemove = collectEntitiesToRemove(playerUUID, serverLevel);
        for (Entity e : toRemove)
            e.remove(Entity.RemovalReason.DISCARDED);
        SummonRegistry.cleanupPlayer(playerUUID, entityId, serverLevel);

        double[] baseOffset = getSpawnOffset(player, spawnSide, spawnDistance);
        float yRot = player.getYRot();

        double baseX = player.getX() + baseOffset[0];
        double baseY = player.getY();
        double baseZ = player.getZ() + baseOffset[2];

        for (int i = 0; i < spawnCount; i++) {
            double x, z;

            if (randomSpawnPosition) {
                double angle = RANDOM.nextDouble() * 2.0 * Math.PI;
                double radius = Math.sqrt(RANDOM.nextDouble()) * randomSpawnRadius;
                x = baseX + Math.cos(angle) * radius;
                z = baseZ + Math.sin(angle) * radius;
            } else {
                double spreadX = spawnCount > 1 ? (i - (spawnCount - 1) / 2.0) * 1.5 : 0.0;
                x = baseX + spreadX * Math.cos(Math.toRadians(yRot));
                z = baseZ + spreadX * Math.sin(Math.toRadians(yRot));
            }

            System.out.println("[SummonAbility] criando " + (i + 1) + "/" + spawnCount
                    + ": " + entityId + " em " + x + " " + baseY + " " + z);

            Entity created = entityType.create(serverLevel, EntitySpawnReason.MOB_SUMMONED);
            if (!(created instanceof LivingEntity summoned)) {
                System.out.println("[SummonAbility] não é LivingEntity ou é null: " + created);
                continue;
            }

            summoned.setPos(x, baseY, z);
            summoned.setYRot(yRot);
            putOwnerUUID(summoned.getPersistentData(), playerUUID);
            putAbilityId(summoned.getPersistentData(), entityId);

            if (summoned instanceof ISummonedEntity s) {
                s.setSummonOwner(playerUUID);
                s.setSummonAbilityId(entityId);
            }

            serverLevel.addFreshEntity(summoned);
            SummonRegistry.register(playerUUID, summoned.getUUID(), entityId);

            applyPetBehavior(summoned, player);

            System.out.println("[SummonAbility] spawnou: " + summoned.getType().toShortString());
        }
    }

    private void applyPetBehavior(LivingEntity summoned, ServerPlayer player) {
        if (!(summoned instanceof Mob mob))
            return;

        if (summoned instanceof ISummonedEntity)
            return;

        if (summoned instanceof TamableAnimal tamable && tame) {
            tamable.tame(player);
        }

        if (overrideAi) {
            mob.goalSelector.removeAllGoals(g -> true);
            mob.targetSelector.removeAllGoals(g -> true);
        } else {
            mob.targetSelector.removeAllGoals(g -> true);
            mob.goalSelector.removeAllGoals(g -> !(g instanceof FloatGoal) && !(g instanceof OpenDoorGoal));
        }

        injectGenericPetGoals(mob);

        System.out.println("[SummonAbility] goals injetadas em: "
                + summoned.getType().toShortString()
                + " | goalSelector: " + mob.goalSelector.getAvailableGoals().size()
                + " | targetSelector: " + mob.targetSelector.getAvailableGoals().size());
    }

    private void injectGenericPetGoals(Mob mob) {
        if (mob instanceof PathfinderMob pathfinder) {
            mob.goalSelector.addGoal(1, new MeleeAttackGoal(pathfinder, 1.0D, true));
        }
        mob.goalSelector.addGoal(2, new FollowOwnerGoal(mob, 1.2, 4.0f, 20.0f,
                maxWanderDistance, teleportToOwner));
        mob.targetSelector.addGoal(1, new OwnerDefendGoal(mob));
        mob.targetSelector.addGoal(2, new OwnerHurtTargetGoal(mob));
    }

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