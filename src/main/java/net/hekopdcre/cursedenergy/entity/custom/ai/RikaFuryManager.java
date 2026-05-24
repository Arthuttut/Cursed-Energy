package net.hekopdcre.cursedenergy.entity.custom.ai;

import net.hekopdcre.cursedenergy.entity.custom.RikaEntity;

public class RikaFuryManager {

    private static final int FURY_DURATION = 6000; // 5 minutos
    private static final int FURY_COOLDOWN = 2400; // 2 minutos

    private final RikaEntity rika;
    private int furyTicks = 0;
    private int cooldownTicks = 0;

    public RikaFuryManager(RikaEntity rika) {
        this.rika = rika;
    }

    public void tick() {
        if (furyTicks > 0) {
            furyTicks--;
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