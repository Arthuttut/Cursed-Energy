package net.hekopdcre.cursedenergy.entity.custom.ai;

import net.hekopdcre.cursedenergy.entity.custom.RikaEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

import java.util.Comparator;
import java.util.List;

public class RikaThreatDetectionGoal extends TargetGoal {

    private final RikaEntity rika;
    private LivingEntity threat;

    public RikaThreatDetectionGoal(RikaEntity rika) {
        super(rika, false);
        this.rika = rika;
    }

    @Override
    public boolean canUse() {
        LivingEntity owner = rika.getOwner();
        if (owner == null)
            return false;
        threat = null;

        // 1. Ataca quem o player atacou por último
        if (owner instanceof Player player) {
            LivingEntity lastHurt = player.getLastHurtMob();
            if (lastHurt != null && lastHurt != rika && lastHurt.isAlive()) {
                threat = lastHurt;
                return true;
            }
        }

        // Apenas mobs hostis (Monster) próximos — ignora passivos completamente
        List<Mob> nearby = owner.level().getEntitiesOfClass(
                Mob.class,
                owner.getBoundingBox().inflate(20),
                e -> e != rika && e.isAlive() && e instanceof Monster);

        // 2. Creeper ignitado tem prioridade máxima
        for (Mob mob : nearby) {
            if (mob instanceof Creeper creeper && creeper.isIgnited()) {
                threat = creeper;
                return true;
            }
        }

        // 3. Ameaças memorizadas (já atacaram o dono antes)
        for (Mob mob : nearby) {
            if (rika.isThreatRemembered(mob.getUUID())) {
                threat = mob;
                return true;
            }
        }

        // 4. Mob hostil que está ativamente mirando no dono
        nearby.stream()
                .filter(e -> e.getTarget() == owner)
                .min(Comparator.comparingDouble(e -> e.distanceTo(owner)))
                .ifPresent(e -> threat = e);

        return threat != null;
    }

    @Override
    public void start() {
        rika.setTarget(threat);
        super.start();
    }
}