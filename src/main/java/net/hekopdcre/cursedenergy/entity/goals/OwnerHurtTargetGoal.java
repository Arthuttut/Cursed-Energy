package net.hekopdcre.cursedenergy.entity.goals;

import net.hekopdcre.cursedenergy.abilities.SummonAbility;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

public class OwnerHurtTargetGoal extends TargetGoal {

    private final Mob mob;
    private LivingEntity cachedTarget;
    private int targetCheckCooldown = 0;

    public OwnerHurtTargetGoal(Mob mob) {
        super(mob, true);
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        Optional<UUID> ownerUUID = SummonAbility.readOwnerUUID(mob.getPersistentData());
        if (ownerUUID.isEmpty())
            return false;

        // FIX: sempre resolve o Player pelo UUID — nunca cacheia a instância
        // (após respawn a instância muda, mas o UUID permanece o mesmo)
        Player owner = mob.level().getPlayerByUUID(ownerUUID.get());
        if (owner == null)
            return false;

        LivingEntity lastHurt = owner.getLastHurtMob();
        if (lastHurt != null
                && lastHurt.isAlive()
                && lastHurt != mob
                && lastHurt != owner
                && !isOwner(lastHurt, ownerUUID.get())
                && lastHurt.distanceToSqr(mob) < 2048) {
            cachedTarget = lastHurt;
        }

        if (cachedTarget == null || !cachedTarget.isAlive()) {
            cachedTarget = null;
            return false;
        }

        mob.setTarget(cachedTarget);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) {
            cachedTarget = null;
            return false;
        }

        if (--targetCheckCooldown <= 0) {
            targetCheckCooldown = 10;
            Optional<UUID> ownerUUID = SummonAbility.readOwnerUUID(mob.getPersistentData());
            if (ownerUUID.isEmpty())
                return false;

            Player owner = mob.level().getPlayerByUUID(ownerUUID.get());
            if (owner == null)
                return false;

            LivingEntity lastHurt = owner.getLastHurtMob();
            if (lastHurt != null
                    && lastHurt.isAlive()
                    && lastHurt != mob
                    && lastHurt != owner
                    && lastHurt != cachedTarget
                    && !isOwner(lastHurt, ownerUUID.get())) {
                cachedTarget = lastHurt;
                mob.setTarget(cachedTarget);
            }
        }
        return true;
    }

    @Override
    public void stop() {
        mob.setTarget(null);
        // FIX: limpa o cache — canUse() revalida via getLastHurtMob() no próximo tick
        cachedTarget = null;
        targetCheckCooldown = 0;
    }

    private boolean isOwner(LivingEntity entity, UUID ownerUUID) {
        return entity instanceof Player p && p.getUUID().equals(ownerUUID);
    }
}