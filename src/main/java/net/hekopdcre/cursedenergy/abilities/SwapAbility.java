package net.hekopdcre.cursedenergy.abilities;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityProperties;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.power.ability.AbilityStateManager;
import net.threetag.palladium.power.energybar.EnergyBarUsage;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class SwapAbility extends Ability {

    public static final MapCodec<SwapAbility> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec(),
                    Codec.INT.fieldOf("range")
                            .orElse(10)
                            .forGetter(a -> a.range),
                    Codec.BOOL.fieldOf("invert_view")
                            .orElse(true)
                            .forGetter(a -> a.invertView),
                    Codec.STRING.fieldOf("teleport_sound")
                            .orElse("minecraft:entity.enderman.teleport")
                            .forGetter(a -> a.teleportSoundId),
                    Codec.BOOL.fieldOf("play_sound_without_target")
                            .orElse(false)
                            .forGetter(a -> a.playSoundWithoutTarget))
            .apply(instance, SwapAbility::new));

    public final int range;
    public final boolean invertView;
    public final String teleportSoundId;
    public final boolean playSoundWithoutTarget;

    public SwapAbility(
            AbilityProperties properties,
            AbilityStateManager stateManager,
            List<EnergyBarUsage> energyBarUsages,
            Integer range,
            Boolean invertView,
            String teleportSoundId,
            Boolean playSoundWithoutTarget) {
        super(properties, stateManager, energyBarUsages);
        this.range = range;
        this.invertView = invertView;
        this.teleportSoundId = teleportSoundId;
        this.playSoundWithoutTarget = playSoundWithoutTarget;
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return CursedEnergyAbilities.SWAP.get();
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> instance, boolean enabled) {
        if (!enabled)
            return false;
        if (!(entity instanceof ServerPlayer player))
            return false;
        if (!(player.level() instanceof ServerLevel level))
            return false;

        Optional<LivingEntity> targetOpt = findTargetInSight(player, level);

        if (targetOpt.isEmpty()) {
            if (this.playSoundWithoutTarget) {
                SoundEvent sound = resolveSoundEvent(this.teleportSoundId);
                level.playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
            return false;
        }

        LivingEntity target = targetOpt.get();

        // ── Salva posições e rotações antes do swap ──────────────────────────
        double playerX = player.getX();
        double playerY = player.getY();
        double playerZ = player.getZ();
        float playerYRot = player.getYRot();
        float playerXRot = player.getXRot();

        double targetX = target.getX();
        double targetY = target.getY();
        double targetZ = target.getZ();
        float targetYRot = target.getYRot();
        float targetXRot = target.getXRot();

        // ── Calcula rotação final do jogador ──────────────────────────────────
        float finalPlayerYRot;
        float finalPlayerXRot;
        if (invertView) {
            double dx = playerX - targetX;
            double dy = playerY - targetY;
            double dz = playerZ - targetZ;
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);
            finalPlayerYRot = (float) Math.toDegrees(Math.atan2(-dx, dz));
            finalPlayerXRot = (float) -Math.toDegrees(Math.atan2(dy, horizontalDist));
        } else {
            finalPlayerYRot = playerYRot;
            finalPlayerXRot = playerXRot;
        }

        // ── Teleporta o jogador ───────────────────────────────────────────────
        player.connection.teleport(targetX, targetY, targetZ, finalPlayerYRot, finalPlayerXRot);

        // ── Teleporta o alvo para onde estava o jogador ───────────────────────
        if (target instanceof ServerPlayer targetPlayer) {
            targetPlayer.connection.teleport(playerX, playerY, playerZ, targetYRot, targetXRot);
        } else {
            target.teleportTo(playerX, playerY, playerZ);
            target.setYRot(targetYRot);
            target.setXRot(targetXRot);
            target.hurtMarked = true;
        }

        // ── Toca o som nos dois pontos de teleporte ───────────────────────────
        SoundEvent sound = resolveSoundEvent(this.teleportSoundId);
        level.playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 1.0f, 1.0f);
        level.playSound(null, target.blockPosition(), sound, SoundSource.PLAYERS, 1.0f, 1.0f);

        return false;
    }

    private SoundEvent resolveSoundEvent(String soundId) {
        try {
            Identifier loc = Identifier.parse(soundId);
            return SoundEvent.createVariableRangeEvent(loc);
        } catch (Exception e) {
            return SoundEvents.ENDERMAN_TELEPORT;
        }
    }

    private Optional<LivingEntity> findTargetInSight(ServerPlayer player, ServerLevel level) {
        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(this.range));

        AABB searchBox = new AABB(eyePos, endPos).inflate(1.5 + this.range * 0.1);

        List<LivingEntity> candidates = level.getEntitiesOfClass(
                LivingEntity.class,
                searchBox,
                e -> e != player && e.isAlive());

        return candidates.stream()
                .filter(e -> isOnSightLine(eyePos, endPos, e, 1.5))
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
    }

    private boolean isOnSightLine(Vec3 eyePos, Vec3 endPos, LivingEntity entity, double tolerance) {
        Vec3 entityCenter = entity.position().add(0, entity.getBbHeight() / 2.0, 0);
        Vec3 lineDir = endPos.subtract(eyePos);
        double lineLen = lineDir.length();

        if (lineLen < 0.0001)
            return false;

        Vec3 lineDirN = lineDir.scale(1.0 / lineLen);
        double projection = entityCenter.subtract(eyePos).dot(lineDirN);
        if (projection < 0 || projection > lineLen)
            return false;

        Vec3 closest = eyePos.add(lineDirN.scale(projection));
        return closest.distanceTo(entityCenter) <= tolerance;
    }
}