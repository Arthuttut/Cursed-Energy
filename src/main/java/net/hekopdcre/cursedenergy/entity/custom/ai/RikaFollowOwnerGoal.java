package net.hekopdcre.cursedenergy.entity.custom.ai;

import net.hekopdcre.cursedenergy.entity.custom.RikaEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class RikaFollowOwnerGoal extends Goal {

    private final RikaEntity rika;
    private int teleportCooldown = 0;

    public RikaFollowOwnerGoal(RikaEntity rika) {
        this.rika = rika;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity owner = rika.getOwner();
        if (owner == null)
            return false;
        // Se está longe demais, precisa rodar para teleportar — ignora target
        if (rika.distanceTo(owner) > 40)
            return true;
        // Caso contrário, só segue se não tem alvo
        if (rika.getTarget() != null)
            return false;
        double minDist = isOwnerLowHealth(owner) ? 3 : 5;
        return rika.distanceTo(owner) > minDist;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity owner = rika.getOwner();
        if (owner == null)
            return false;
        // Mantém rodando enquanto estiver longe demais (para garantir teleporte)
        if (rika.distanceTo(owner) > 40)
            return true;
        if (rika.getTarget() != null)
            return false;
        double minDist = isOwnerLowHealth(owner) ? 3 : 5;
        return rika.distanceTo(owner) > minDist;
    }

    @Override
    public void tick() {
        LivingEntity owner = rika.getOwner();
        if (owner == null)
            return;

        if (teleportCooldown > 0)
            teleportCooldown--;

        double dist = rika.distanceTo(owner);

        // Teleporte tem prioridade absoluta se muito longe
        if (dist > 40 && teleportCooldown == 0) {
            rika.teleportTo(
                    owner.getX() + (rika.getRandom().nextDouble() - 0.5) * 3,
                    owner.getY(),
                    owner.getZ() + (rika.getRandom().nextDouble() - 0.5) * 3);
            teleportCooldown = 100;
            return;
        }

        // Segue normalmente se não tem alvo
        if (rika.getTarget() == null) {
            double speed = rika.isFuryMode() ? 1.5 : (isOwnerLowHealth(owner) ? 1.4 : 1.0);
            rika.getNavigation().moveTo(owner, speed);
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