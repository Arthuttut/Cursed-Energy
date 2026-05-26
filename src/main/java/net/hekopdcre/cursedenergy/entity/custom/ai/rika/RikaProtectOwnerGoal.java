package net.hekopdcre.cursedenergy.entity.custom.ai.rika;

import net.hekopdcre.cursedenergy.entity.custom.RikaEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

/**
 * RikaProtectOwnerGoal
 *
 * CORREÇÃO: substituído String.contains("Bow") por instanceof BowItem /
 * CrossbowItem.
 * Elimina alocação de String, reflection metadata lookup e contains() por tick.
 * instanceof é resolvido via vtable — custo praticamente zero.
 */
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

        // Prioridade 1: quem acabou de atacar o dono
        LivingEntity attacker = owner.getLastHurtByMob();
        if (attacker != null && attacker != rika && attacker.isAlive()
                && attacker.distanceToSqr(owner) < 1024) {
            threat = attacker;
            rika.rememberThreat(attacker.getUUID());
            rika.getFuryManager().triggerFury();
            return true;
        }

        // Prioridade 2: ameaças memorizadas ativamente atacando o dono
        if (rika.level() instanceof ServerLevel serverLevel) {
            for (UUID uuid : rika.getThreatMemory()) {
                Entity e = serverLevel.getEntity(uuid);
                if (e instanceof Mob mob && mob.isAlive()
                        && mob.distanceToSqr(owner) < 400
                        && mob.getTarget() == owner) {
                    threat = mob;
                    return true;
                }
            }
        }

        // Prioridade 3: archer apontado para o dono
        // instanceof em vez de String.contains() — zero alloc, custo vtable
        AABB nearBB = owner.getBoundingBox().inflate(16);
        List<LivingEntity> nearby = owner.level().getEntitiesOfClass(
                LivingEntity.class, nearBB,
                e -> e != rika && e != owner && e.isUsingItem());

        for (LivingEntity e : nearby) {
            Item item = e.getUseItem().getItem();
            if (item instanceof BowItem || item instanceof CrossbowItem) {
                threat = e;
                rika.rememberThreat(e.getUUID());
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (threat == null || !threat.isAlive())
            return false;
        LivingEntity owner = rika.getOwner();
        if (owner == null)
            return false;
        return threat.distanceToSqr(owner) < 1600; // 40²
    }

    @Override
    public void start() {
        rika.setTarget(threat);
        super.start();
    }
}