package net.hekopdcre.cursedenergy.entity.custom.ai;

import net.hekopdcre.cursedenergy.entity.custom.RikaEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;

import java.util.List;
import java.util.UUID;

public class RikaProtectOwnerGoal extends TargetGoal {

    private final RikaEntity rika;
    private LivingEntity threat;

    public RikaProtectOwnerGoal(RikaEntity rika) {
        super(rika, false);
        this.rika = rika;
    }

    @Override
    public boolean canUse() {
        LivingEntity owner = rika.getOwner();
        if (owner == null)
            return false;

        threat = null;

        // Prioridade 1: quem acabou de atacar o dono agora
        LivingEntity attacker = owner.getLastHurtByMob();
        if (attacker != null && attacker != rika && attacker.isAlive()
                && attacker.distanceTo(owner) < 32) {
            threat = attacker;
            rika.rememberThreat(attacker.getUUID());
            rika.getFuryManager().triggerFury();
            return true;
        }

        // Prioridade 2: ameaças memorizadas que ainda estão ATIVAMENTE atacando o dono
        // (só reativa se o mob ainda tem target no owner — não persegue quem foi
        // embora)
        if (rika.level() instanceof ServerLevel serverLevel) {
            for (UUID uuid : rika.getThreatMemory()) {
                Entity e = serverLevel.getEntity(uuid);
                if (e instanceof LivingEntity living && living.isAlive()
                        && living.distanceTo(owner) < 20
                        && living instanceof Mob mob && mob.getTarget() == owner) {
                    threat = living;
                    return true;
                }
            }
        }

        // Prioridade 3: alguém usando arco/besta apontado para o dono, por perto
        List<LivingEntity> nearby = owner.level().getEntitiesOfClass(
                LivingEntity.class,
                owner.getBoundingBox().inflate(16),
                e -> e != rika && e != owner && e.isUsingItem());

        for (LivingEntity e : nearby) {
            String name = e.getUseItem().getItem().getClass().getSimpleName().toLowerCase();
            if (name.contains("bow") || name.contains("crossbow")) {
                threat = e;
                rika.rememberThreat(e.getUUID());
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean canContinueToUse() {
        // Para de usar se o alvo morreu ou sumiu — não fica presa no goal
        if (threat == null || !threat.isAlive())
            return false;
        LivingEntity owner = rika.getOwner();
        if (owner == null)
            return false;
        return threat.distanceTo(owner) < 40;
    }

    @Override
    public void start() {
        rika.setTarget(threat);
        super.start();
    }
}