package net.hekopdcre.cursedenergy.entity.custom.ai.rika;

import net.hekopdcre.cursedenergy.entity.custom.RikaEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * RikaThreatDetectionGoal — versão final com OwnerScanCache.
 *
 * O scan de Mobs hostis é agora servido pelo OwnerScanCache compartilhado.
 * Se RikaEntity.checkIncomingProjectiles() já rodou neste goal tick (ou no tick
 * anterior dentro do TTL), getEntitiesOfClass não é chamado aqui — custo zero.
 *
 * A lógica de prioridade é idêntica à versão anterior:
 * 1. lastHurtMob do owner (player atacou alguém)
 * 2. Creeper ignitado (retorno imediato)
 * 3. Mob com target no owner, mais próximo (O(n) sem stream)
 * 4. Ameaça memorizada
 */
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

        // Prioridade 1: alvo que o owner atacou recentemente
        if (owner instanceof Player player) {
            LivingEntity lastHurt = player.getLastHurtMob();
            if (lastHurt != null && lastHurt != rika && lastHurt.isAlive()
                    && lastHurt.distanceToSqr(owner) < 1024) {
                threat = lastHurt;
                return true;
            }
        }

        // Scan compartilhado — zero custo se já populado neste tick
        OwnerScanCache.ScanResult scan = OwnerScanCache.get(owner, rika.tickCount, rika);
        List<Mob> nearby = scan.hostileMobs;

        LivingEntity closestTargetingOwner = null;
        double closestDistSq = Double.MAX_VALUE;

        for (Mob mob : nearby) {
            // Prioridade 2: Creeper ignitado
            if (mob instanceof Creeper creeper && creeper.isIgnited()) {
                threat = creeper;
                return true;
            }

            // Prioridade 3: mob atacando owner — raio 20 (400 distSq)
            double distSq = mob.distanceToSqr(owner);
            if (distSq < 400 && mob.getTarget() == owner && distSq < closestDistSq) {
                closestDistSq = distSq;
                closestTargetingOwner = mob;
            }
        }

        if (closestTargetingOwner != null) {
            threat = closestTargetingOwner;
            return true;
        }

        // Prioridade 4: ameaças memorizadas dentro do raio 48 (já filtrado no cache)
        for (Mob mob : nearby) {
            if (rika.isThreatRemembered(mob.getUUID())) {
                threat = mob;
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
        return threat.distanceToSqr(owner) < 4096; // 64²
    }

    @Override
    public void start() {
        rika.setTarget(threat);
        super.start();
    }
}