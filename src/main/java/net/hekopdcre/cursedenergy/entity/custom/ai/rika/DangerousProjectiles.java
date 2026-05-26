package net.hekopdcre.cursedenergy.entity.custom.ai.rika;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * DangerousProjectiles — registry estático de tipos de projéteis perigosos.
 *
 * MIGRAÇÃO 1.21.11 / 26.1:
 * O pacote net.minecraft.world.entity.projectile foi reorganizado em
 * subpackages.
 *
 * AbstractArrow → .arrow.AbstractArrow
 * AbstractHurtingProjectile → .hurtingprojectile.AbstractHurtingProjectile
 * LlamaSpit → permanece em .projectile (sem subpackage)
 * ShulkerBullet → permanece em .projectile (sem subpackage)
 *
 * CAMADA 1: instanceof hierárquico — custo vtable, zero alloc
 * AbstractArrow → Arrow, SpectralArrow, ThrownTrident (e mods)
 * AbstractHurtingProjectile → Fireball, SmallFireball, DragonFireball,
 * WitherSkull, WindCharge (e mods)
 *
 * CAMADA 2: Set<Class<?>> para tipos fora da hierarquia
 * ShulkerBullet e LlamaSpit não herdam das classes acima.
 * Class identity como hash key: contains() é O(1) sem String.
 */
public final class DangerousProjectiles {

    private static final Set<Class<?>> EXTRA_TYPES = Set.of(
            ShulkerBullet.class,
            LlamaSpit.class);

    private DangerousProjectiles() {
    }

    /**
     * Verifica se o projétil é de tipo perigoso E está se movendo em
     * direção ao owner dentro de range de 32 blocos.
     */
    public static boolean isDangerous(Projectile p, LivingEntity owner) {
        // Camada 1: instanceof hierárquico
        if (!(p instanceof AbstractArrow)
                && !(p instanceof AbstractHurtingProjectile)
                && !EXTRA_TYPES.contains(p.getClass())) {
            return false;
        }

        if (p.getOwner() == owner)
            return false;

        Vec3 motion = p.getDeltaMovement();
        double motLenSq = motion.lengthSqr();
        if (motLenSq < 0.0001)
            return false;

        // toOwner inline — sem Vec3 allocation
        double towX = owner.getX() - p.getX();
        double towY = owner.getY() - p.getY();
        double towZ = owner.getZ() - p.getZ();
        double towLenSq = towX * towX + towY * towY + towZ * towZ;
        if (towLenSq < 0.0001 || towLenSq > 1024.0)
            return false; // > 32²

        // dot product inline sem normalize() intermediário
        double dot = (motion.x * towX + motion.y * towY + motion.z * towZ)
                / (Math.sqrt(motLenSq) * Math.sqrt(towLenSq));
        return dot > 0.7;
    }

    /** Verifica apenas o tipo, sem direção. */
    public static boolean isDangerousType(Projectile p) {
        return p instanceof AbstractArrow
                || p instanceof AbstractHurtingProjectile
                || EXTRA_TYPES.contains(p.getClass());
    }
}