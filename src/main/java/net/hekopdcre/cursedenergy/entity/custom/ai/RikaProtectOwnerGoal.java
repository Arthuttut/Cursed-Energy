package net.hekopdcre.cursedenergy.entity.custom.ai;

import net.hekopdcre.cursedenergy.entity.custom.RikaEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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

        // prioridade 1: quem atacou o dono agora
        LivingEntity attacker = owner.getLastHurtByMob();
        if (attacker != null && attacker != rika && attacker.isAlive()) {
            threat = attacker;
            rika.rememberThreat(attacker.getUUID());
            rika.getFuryManager().triggerFury();
            return true;
        }

        // prioridade 2: ameaças da memória que ainda estão vivas por perto
        if (rika.level() instanceof ServerLevel serverLevel) {
            for (UUID uuid : rika.getThreatMemory()) {
                Entity e = serverLevel.getEntity(uuid);
                if (e instanceof LivingEntity living && living.isAlive()
                        && living.distanceTo(owner) < 24) {
                    threat = living;
                    return true;
                }
            }
        }

        // prioridade 3: alguém usando arco apontado para o dono
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
    public void start() {
        rika.setTarget(threat);
        super.start();
    }
}