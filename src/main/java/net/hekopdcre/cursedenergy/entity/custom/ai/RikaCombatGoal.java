package net.hekopdcre.cursedenergy.entity.custom.ai;

import net.hekopdcre.cursedenergy.entity.custom.RikaEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.phys.Vec3;

public class RikaCombatGoal extends MeleeAttackGoal {

    private final RikaEntity rika;

    public RikaCombatGoal(RikaEntity rika) {
        super(rika, 1.2, true);
        this.rika = rika;
    }

    @Override
    public void tick() {
        super.tick();

        LivingEntity target = rika.getTarget();
        if (target == null)
            return;

        // Memoriza o alvo atual como ameaça
        rika.rememberThreat(target.getUUID());

        // Dash só ocorre se ela NÃO está voando — evita conflito com tickFlight()
        double dist = rika.distanceTo(target);
        if (!rika.isFlying() && dist > 8 && rika.getDashCooldown() == 0) {
            Vec3 dir = target.position().subtract(rika.position()).normalize();
            double dashPower = rika.isFuryMode() ? 3.0 : 2.5;
            rika.setDeltaMovement(dir.x * dashPower, 0.25, dir.z * dashPower);
            rika.setDashCooldown(60);
        }

        // Prioriza quem atacou o dono
        LivingEntity owner = rika.getOwner();
        if (owner != null) {
            LivingEntity attacker = owner.getLastHurtByMob();
            if (attacker != null && attacker != target && attacker != rika && attacker.isAlive()) {
                rika.setTarget(attacker);
                rika.rememberThreat(attacker.getUUID());
                rika.getFuryManager().triggerFury();
            }
        }

        // Encerra fury se matou o alvo
        if (!target.isAlive()) {
            rika.getFuryManager().endFuryEarly();
        }
    }

    protected double getAttackReachSqr(LivingEntity target) {
        return this.mob.getBbWidth() * 2.5f * this.mob.getBbWidth() * 2.5f + target.getBbWidth();
    }
}