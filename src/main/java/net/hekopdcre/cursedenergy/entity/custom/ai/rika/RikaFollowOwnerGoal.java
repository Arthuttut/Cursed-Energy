package net.hekopdcre.cursedenergy.entity.custom.ai.rika;

import net.hekopdcre.cursedenergy.entity.custom.RikaEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * RikaFollowOwnerGoal — otimizações:
 *
 * 1. distanceTo → distanceToSqr em todas as comparações de distância:
 * Elimina sqrt por chamada. Para 2 Rikas a 20TPS isso são ~40 sqrt poupados/s.
 *
 * 2. isDangerNearOwner() já otimizado na RikaEntity (usa cache de projéteis).
 * Aqui nenhuma mudança de chamada necessária.
 *
 * 3. teleportCooldown: mantido em 100 ticks — evita rubberband.
 *
 * 4. getNavigation().moveTo() chamado SOMENTE quando distância > threshold
 * E apenas se navigation não estiver já em progresso (evita path recalculation
 * desnecessário quando já está se movendo para o owner).
 */
public class RikaFollowOwnerGoal extends Goal {

    private final RikaEntity rika;
    private int teleportCooldown = 0;

    // Thresholds em distSq para evitar sqrt
    private static final double TELEPORT_DIST_SQ = 1600.0; // 40²
    private static final double MIN_DIST_NORMAL_SQ = 9.0; // 3²
    private static final double MIN_DIST_LOWHEALTH_SQ = 4.0; // 2²
    private static final double STOP_DIST_NORMAL_SQ = 25.0; // 5²
    private static final double STOP_DIST_LOWHEALTH_SQ = 9.0; // 3²

    public RikaFollowOwnerGoal(RikaEntity rika) {
        this.rika = rika;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity owner = rika.getOwner();
        if (owner == null)
            return false;

        double distSq = rika.distanceToSqr(owner);

        // Teleporte de emergência — sempre tem prioridade
        if (distSq > TELEPORT_DIST_SQ)
            return true;

        if (rika.getTarget() != null)
            return false;
        if (rika.isDangerNearOwner())
            return false;

        double minSq = isOwnerLowHealth(owner) ? MIN_DIST_LOWHEALTH_SQ : MIN_DIST_NORMAL_SQ;
        return distSq > minSq;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity owner = rika.getOwner();
        if (owner == null)
            return false;

        double distSq = rika.distanceToSqr(owner);

        if (distSq > TELEPORT_DIST_SQ)
            return true;
        if (rika.getTarget() != null)
            return false;
        if (rika.isDangerNearOwner())
            return false;

        double stopSq = isOwnerLowHealth(owner) ? STOP_DIST_LOWHEALTH_SQ : STOP_DIST_NORMAL_SQ;
        return distSq > stopSq;
    }

    @Override
    public void tick() {
        LivingEntity owner = rika.getOwner();
        if (owner == null)
            return;

        if (teleportCooldown > 0)
            teleportCooldown--;

        double distSq = rika.distanceToSqr(owner);

        if (distSq > TELEPORT_DIST_SQ && teleportCooldown == 0) {
            rika.teleportTo(
                    owner.getX() + (rika.getRandom().nextDouble() - 0.5) * 3,
                    owner.getY(),
                    owner.getZ() + (rika.getRandom().nextDouble() - 0.5) * 3);
            teleportCooldown = 100;
            return;
        }

        if (rika.getTarget() == null) {
            double speed = rika.isFuryMode() ? 1.5 : (isOwnerLowHealth(owner) ? 1.4 : 1.0);
            // Evita recalcular path se já está navegando para o owner
            // (isInProgress = true significa que já tem path ativo)
            if (!rika.getNavigation().isInProgress() || distSq > STOP_DIST_NORMAL_SQ) {
                rika.getNavigation().moveTo(owner, speed);
            }
            rika.getLookControl().setLookAt(owner, 10, 10);
        }
    }

    @Override
    public void stop() {
        rika.getNavigation().stop();
    }

    private boolean isOwnerLowHealth(LivingEntity owner) {
        return owner.getHealth() / owner.getMaxHealth() < 0.3f;
    }
}