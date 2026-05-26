package net.hekopdcre.cursedenergy.entity.custom.ai.rika;

import net.hekopdcre.cursedenergy.entity.custom.RikaEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.phys.AABB;

/**
 * RikaCombatGoal
 *
 * CORREÇÃO 1.21 — getAttackReachSqr REMOVIDO desde NeoForge 1.20.2:
 * O MeleeAttackGoal não expõe mais getAttackReachSqr para override.
 * O reach agora é controlado via Mob#getAttackBoundingBox na entidade.
 *
 * A solução correta para NeoForge 1.21 é:
 * - Override de MeleeAttackGoal#canPerformAttack no goal (verifica AABB), OU
 * - Override de Mob#getAttackBoundingBox na entidade (afeta
 * isWithinMeleeAttackRange)
 *
 * Como a Rika usa MeleeAttackGoal, o caminho limpo é sobrescrever
 * canPerformAttack para aplicar o reach customizado via inflate do AABB.
 * Isso mantém compatibilidade 1.21 sem remover funcionalidade.
 *
 * REACH CUSTOMIZADO:
 * O comportamento original era: (bbWidth * 2.5)² + target.bbWidth
 * Traduzimos para inflate do attackBB no canPerformAttack.
 * inflate(1.5) em cada eixo simula o reach extra sem getAttackReachSqr.
 */
public class RikaCombatGoal extends MeleeAttackGoal {

    private final RikaEntity rika;

    // Reach extra em blocos além do AABB padrão de MeleeAttackGoal
    // Equivale aproximadamente ao bbWidth * 2.5 do código original
    private static final double EXTRA_REACH = 1.5;

    public RikaCombatGoal(RikaEntity rika) {
        super(rika, 1.2, true);
        this.rika = rika;
    }

    // -----------------------------------------------------------------------
    // REACH CUSTOMIZADO — NeoForge 1.21 API correta
    // canPerformAttack é chamado por checkAndPerformAttack a cada tick de ataque.
    // Inflamos o AABB da Rika para simular o reach extra antes de testar
    // interseção.
    // -----------------------------------------------------------------------

    @Override
    protected boolean canPerformAttack(LivingEntity target) {
        // AABB da Rika inflado com reach extra, testado contra hitbox do alvo
        AABB attackBB = this.mob.getBoundingBox().inflate(EXTRA_REACH);
        return attackBB.intersects(target.getBoundingBox());
    }

    // -----------------------------------------------------------------------
    // LIFECYCLE
    // -----------------------------------------------------------------------

    @Override
    public void start() {
        super.start();
        rika.setAggressive(true);
    }

    @Override
    public void stop() {
        super.stop();
        rika.setAggressive(false);
    }

    // -----------------------------------------------------------------------
    // TICK — dash, threat memory, target switch
    // -----------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();

        LivingEntity target = rika.getTarget();
        if (target == null)
            return;

        if (!target.isAlive()) {
            rika.getFuryManager().endFuryEarly();
            return;
        }

        rika.rememberThreat(target.getUUID());

        // Dash: distanceToSqr evita sqrt por tick
        double distSq = rika.distanceToSqr(target);
        if (distSq > 64.0 && rika.getDashCooldown() == 0) { // 8² = 64
            double dx = target.getX() - rika.getX();
            double dz = target.getZ() - rika.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0.001) {
                double dashPower = rika.isFuryMode() ? 3.0 : 2.5;
                double inv = dashPower / len;
                rika.setDeltaMovement(dx * inv, 0.25, dz * inv);
                rika.setDashCooldown(60);
            }
        }

        // Troca de alvo se o owner for atacado por outra entidade
        LivingEntity owner = rika.getOwner();
        if (owner != null) {
            LivingEntity attacker = owner.getLastHurtByMob();
            if (attacker != null && attacker != target && attacker != rika
                    && attacker.isAlive()
                    && attacker.distanceToSqr(owner) < 1024) { // 32²
                rika.setTarget(attacker);
                rika.rememberThreat(attacker.getUUID());
                rika.getFuryManager().triggerFury();
            }
        }
    }
}