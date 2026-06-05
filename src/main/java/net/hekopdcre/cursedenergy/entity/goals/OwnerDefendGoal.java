package net.hekopdcre.cursedenergy.entity.goals;

import net.hekopdcre.cursedenergy.abilities.SummonAbility;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class OwnerDefendGoal extends TargetGoal {

    private final Mob mob;
    private static final double SCAN_RADIUS = 24.0;
    private static final long THREAT_EXPIRY_TICKS = 60L;
    private LivingEntity threat;

    public OwnerDefendGoal(Mob mob) {
        super(mob, true);
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        LivingEntity existing = mob.getTarget();

        // FIX: limpa target morto para não travar a busca por novas ameaças
        if (existing != null && !existing.isAlive()) {
            mob.setTarget(null);
            existing = null;
        }

        if (existing != null) {
            threat = existing;
            return true;
        }

        Optional<UUID> ownerUUID = SummonAbility.readOwnerUUID(mob.getPersistentData());
        if (ownerUUID.isEmpty())
            return false;

        Player owner = mob.level().getPlayerByUUID(ownerUUID.get());
        if (owner == null)
            return false;

        threat = findThreat(owner, ownerUUID.get());
        if (threat == null)
            return false;

        mob.setTarget(threat);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void start() {
        mob.setTarget(threat);
        super.start();
    }

    @Override
    public void stop() {
        mob.setTarget(null);
        threat = null;
    }

    private LivingEntity findThreat(Player owner, UUID ownerUUID) {
        long currentTick = mob.level().getGameTime();

        LivingEntity attacker = owner.getLastHurtByMob();

        // FIX: descarta dado residual de morte verificando o timestamp
        if (attacker != null
                && attacker != mob
                && attacker.isAlive()
                && attacker.distanceToSqr(owner) < 1024
                && isValidThreat(attacker, ownerUUID)
                && (currentTick - owner.getLastHurtByMobTimestamp()) < THREAT_EXPIRY_TICKS) {
            return attacker;
        }

        // FIX: só usa lastDamageSource se o owner está realmente machucado
        // (após respawn o HP está cheio mas a source ainda existe — é residual)
        var lastSource = owner.getLastDamageSource();
        if (lastSource != null && owner.getHealth() < owner.getMaxHealth()) {
            if (lastSource.getEntity() instanceof LivingEntity shooter
                    && isValidThreat(shooter, ownerUUID))
                return shooter;
            if (lastSource.getDirectEntity() instanceof LivingEntity direct
                    && isValidThreat(direct, ownerUUID))
                return direct;
        }

        // Scan ativo: mobs na área com o owner como target
        AABB box = owner.getBoundingBox().inflate(SCAN_RADIUS);
        List<Mob> nearby = mob.level().getEntitiesOfClass(Mob.class, box,
                m -> m != mob && m.isAlive() && m.getTarget() == owner);

        return nearby.stream()
                .min(Comparator.comparingDouble(m -> m.distanceToSqr(owner)))
                .orElse(null);
    }

    private boolean isValidThreat(LivingEntity entity, UUID ownerUUID) {
        if (entity == null || !entity.isAlive())
            return false;
        if (entity == mob)
            return false;
        if (entity instanceof Player p && p.getUUID().equals(ownerUUID))
            return false;
        return true;
    }
}