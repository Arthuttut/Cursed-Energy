package net.hekopdcre.cursedenergy.entity.custom.ai.rika;

import net.hekopdcre.cursedenergy.entity.custom.RikaEntity;

/**
 * RikaFuryManager — sem mudanças comportamentais.
 *
 * Otimizações:
 * - Removida checagem redundante de "furyTicks > 0" duas vezes por tick
 * (era verificado tanto no bloco de decremento quanto depois)
 * - Condição de encerramento de fury integrada ao bloco de decremento
 * principal,
 * eliminando um branch extra por tick
 * - Lógica mais linear → branch predictor do JVM tem melhor taxa de acerto
 */
public class RikaFuryManager {

    private static final int FURY_DURATION = 6000;
    private static final int FURY_COOLDOWN = 2400;

    private final RikaEntity rika;
    private int furyTicks = 0;
    private int cooldownTicks = 0;

    public RikaFuryManager(RikaEntity rika) {
        this.rika = rika;
    }

    public void tick() {
        if (furyTicks > 0) {
            furyTicks--;

            var target = rika.getTarget();
            // Encerra se target morreu OU se não há mais target E owner está seguro
            if (target != null && !target.isAlive()) {
                furyTicks = 0;
                rika.setFuryMode(false);
                cooldownTicks = FURY_COOLDOWN / 2;
                rika.setTarget(null);
                return;
            }

            // Sem target e owner com vida cheia → encerra fury
            if (target == null) {
                var owner = rika.getOwner();
                if (owner == null || owner.getHealth() / owner.getMaxHealth() > 0.7f) {
                    furyTicks = 0;
                    rika.setFuryMode(false);
                    cooldownTicks = FURY_COOLDOWN;
                    return;
                }
            }

            if (furyTicks == 0) {
                rika.setFuryMode(false);
                cooldownTicks = FURY_COOLDOWN;
            }
        } else if (cooldownTicks > 0) {
            cooldownTicks--;
        }
    }

    public void triggerFury() {
        if (cooldownTicks > 0)
            return;
        furyTicks = FURY_DURATION;
        rika.setFuryMode(true);
    }

    public void endFuryEarly() {
        if (furyTicks > 0) {
            furyTicks = 0;
            rika.setFuryMode(false);
            cooldownTicks = FURY_COOLDOWN / 2;
        }
    }

    public boolean isOnCooldown() {
        return cooldownTicks > 0;
    }

    public boolean isActive() {
        return furyTicks > 0;
    }
}