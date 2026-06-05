package net.hekopdcre.cursedenergy.entity.goals;

import net.hekopdcre.cursedenergy.abilities.SummonAbility;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

public class FollowOwnerGoal extends Goal {

    private final Mob mob;
    private final double speed;
    private final float minDist;
    private final float teleportDist;
    private final double maxWanderDistance;
    private final boolean shouldTeleport;

    private int recalcTick;

    public FollowOwnerGoal(Mob mob, double speed, float minDist, float teleportDist,
            double maxWanderDistance, boolean shouldTeleport) {
        this.mob = mob;
        this.speed = speed;
        this.minDist = minDist;
        this.teleportDist = teleportDist;
        this.maxWanderDistance = maxWanderDistance;
        this.shouldTeleport = shouldTeleport;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    /** Backwards-compatible constructor. */
    public FollowOwnerGoal(Mob mob, double speed, float minDist, float teleportDist) {
        this(mob, speed, minDist, teleportDist, teleportDist, true);
    }

    @Override
    public boolean canUse() {
        Player owner = getOwner();
        if (owner == null)
            return false;
        return mob.distanceTo(owner) > minDist;
    }

    @Override
    public boolean canContinueToUse() {
        Player owner = getOwner();
        if (owner == null)
            return false;
        return mob.distanceTo(owner) > minDist;
    }

    @Override
    public void start() {
        recalcTick = 0;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (mob.getTarget() != null && !mob.getTarget().isAlive()) {
            mob.setTarget(null);
        }

        Player owner = getOwner();
        if (owner == null)
            return;

        double dist = mob.distanceTo(owner);

        if (shouldTeleport) {
            if (dist > maxWanderDistance || dist > teleportDist) {
                mob.teleportTo(owner.getX(), owner.getY(), owner.getZ());
                recalcTick = 0;
                return;
            }
        }

        if (--recalcTick <= 0) {
            recalcTick = 10;
            mob.getNavigation().moveTo(owner, speed);
        }
    }

    private Player getOwner() {
        return SummonAbility.readOwnerUUID(mob.getPersistentData())
                .map(uuid -> mob.level().getPlayerByUUID(uuid))
                .orElse(null);
    }
}