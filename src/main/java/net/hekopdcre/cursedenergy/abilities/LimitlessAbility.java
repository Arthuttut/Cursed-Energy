package net.hekopdcre.cursedenergy.abilities;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityProperties;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.power.ability.AbilityStateManager;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.energybar.EnergyBarUsage;

import java.util.*;

public class LimitlessAbility extends Ability {

    public static final MapCodec<LimitlessAbility> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(),
            stateCodec(),
            energyBarUsagesCodec(),
            com.mojang.serialization.Codec.DOUBLE
                    .optionalFieldOf("range", 5.0)
                    .forGetter(LimitlessAbility::getRange),
            com.mojang.serialization.Codec.DOUBLE
                    .optionalFieldOf("effect", 1.0)
                    .forGetter(LimitlessAbility::getEffect),
            com.mojang.serialization.Codec.BOOL
                    .optionalFieldOf("affect_passive", true)
                    .forGetter(LimitlessAbility::isAffectPassive),
            com.mojang.serialization.Codec.BOOL
                    .optionalFieldOf("affect_hostile", true)
                    .forGetter(LimitlessAbility::isAffectHostile),
            com.mojang.serialization.Codec.BOOL
                    .optionalFieldOf("affect_inanimate", true)
                    .forGetter(LimitlessAbility::isAffectInanimate))
            .apply(instance, LimitlessAbility::new));

    private final Double range;
    private final Double effect;
    private final Boolean affectPassive;
    private final Boolean affectHostile;
    private final Boolean affectInanimate;

    private final Map<UUID, Vec3> frozenProjectiles = new HashMap<>();
    private final Map<UUID, Vec3> frozenPositions = new HashMap<>();

    public LimitlessAbility(AbilityProperties properties,
            AbilityStateManager stateManager,
            List<EnergyBarUsage> energyBarUsages,
            Double range,
            Double effect,
            Boolean affectPassive,
            Boolean affectHostile,
            Boolean affectInanimate) {
        super(properties, stateManager, energyBarUsages);
        this.range = range;
        this.effect = effect;
        this.affectPassive = affectPassive;
        this.affectHostile = affectHostile;
        this.affectInanimate = affectInanimate;
    }

    public Double getRange() {
        return this.range;
    }

    public Double getEffect() {
        return this.effect;
    }

    public Boolean isAffectPassive() {
        return this.affectPassive;
    }

    public Boolean isAffectHostile() {
        return this.affectHostile;
    }

    public Boolean isAffectInanimate() {
        return this.affectInanimate;
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return CursedEnergyAbilities.LIMITLESS.get();
    }

    private double calcFator(double dist) {
        if (dist <= this.effect)
            return 0.0;
        double fator = Math.pow((dist - this.effect) / (this.range - this.effect), 2.0);
        return Math.max(fator, 0.00001);
    }

    private Vec3 calcStopPos(Entity projectile, Vec3 center) {
        Vec3 projPos = projectile.position();
        Vec3 vel = projectile.getDeltaMovement();
        double r = this.effect;
        Vec3 oc = projPos.subtract(center);

        double a = vel.dot(vel);
        double b = 2.0 * oc.dot(vel);
        double c = oc.dot(oc) - r * r;

        if (a < 1e-12 || (b * b - 4.0 * a * c) < 0 || c < 0) {
            double distFromCenter = oc.length();
            if (distFromCenter < 1e-6) {
                Vec3 pushDir = vel.lengthSqr() > 1e-12
                        ? vel.normalize().scale(-1)
                        : new Vec3(0, 0, 1);
                return center.add(pushDir.scale(r + 0.05));
            }
            return center.add(oc.normalize().scale(r + 0.05));
        }

        double sqrtD = Math.sqrt(b * b - 4.0 * a * c);
        double t1 = (-b - sqrtD) / (2.0 * a);
        double t2 = (-b + sqrtD) / (2.0 * a);
        double t = (t1 >= 0) ? t1 : (t2 >= 0 ? t2 : -1);

        if (t < 0) {
            return center.add(oc.normalize().scale(r + 0.05));
        }

        Vec3 entry = projPos.add(vel.scale(t));
        Vec3 dirFromCenter = entry.subtract(center);
        double len = dirFromCenter.length();
        return (len < 1e-6)
                ? center.add(new Vec3(r + 0.05, 0, 0))
                : center.add(dirFromCenter.normalize().scale(r + 0.05));
    }

    /**
     * Aplica o congelamento absoluto em uma entidade já registrada no mapa.
     * Usa moveTo() em vez de setPos() para melhor sincronização em projéteis
     * com lógica interna (fireball, snowball, etc).
     */
    private void applyFreeze(Entity e, Vec3 frozen) {
        e.setDeltaMovement(Vec3.ZERO);
        e.teleportTo(frozen.x, frozen.y, frozen.z);
        e.hurtMarked = true; // força sync de posição cliente/servidor
    }

    @Override
    public boolean tick(LivingEntity holder, AbilityInstance<?> instance, boolean enabled) {
        if (!enabled || holder.level().isClientSide()) {
            frozenProjectiles.clear();
            frozenPositions.clear();
            return super.tick(holder, instance, enabled);
        }

        Vec3 holderPos = holder.position().add(0, holder.getBbHeight() / 2.0, 0);
        double rangeSq = this.range * this.range;

        AABB searchBox = new AABB(
                holderPos.x - this.range, holderPos.y - this.range, holderPos.z - this.range,
                holderPos.x + this.range, holderPos.y + this.range, holderPos.z + this.range);

        // ── Entidades vivas ──────────────────────────────────────────────────
        List<LivingEntity> nearby = holder.level().getEntitiesOfClass(
                LivingEntity.class, searchBox, e -> e != holder);

        for (LivingEntity target : nearby) {
            boolean isHostile = target instanceof Monster;
            boolean isPassive = !isHostile;

            if (isHostile && !this.affectHostile)
                continue;
            if (isPassive && !this.affectPassive)
                continue;

            double distSq = holderPos.distanceToSqr(
                    target.position().add(0, target.getBbHeight() / 2.0, 0));
            if (distSq > rangeSq)
                continue;

            double dist = Math.sqrt(distSq);
            double fator = calcFator(dist);

            if (fator == 0.0) {
                target.setDeltaMovement(Vec3.ZERO);
                if (target instanceof Mob mob)
                    mob.setNoAi(true);
            } else {
                Vec3 vel = target.getDeltaMovement();
                target.setDeltaMovement(vel.x * fator, vel.y, vel.z * fator);
                if (target instanceof Mob mob && mob.isNoAi())
                    mob.setNoAi(false);
            }
        }

        // ── Projéteis e inanimados ───────────────────────────────────────────
        if (this.affectInanimate) {

            // Mantém todos os projéteis já congelados, removendo apenas os que
            // saíram do mundo. O freeze é re-aplicado aqui, antes de processar
            // novas entidades, para garantir que nenhum tick intermediário mova
            // um projétil congelado.
            frozenProjectiles.keySet().removeIf(uuid -> {
                Entity e = holder.level().getEntity(uuid);
                if (e == null)
                    return true; // sumiu do mundo → remove
                applyFreeze(e, frozenProjectiles.get(uuid));
                return false; // ainda existe → mantém
            });

            frozenPositions.keySet().removeIf(uuid -> holder.level().getEntity(uuid) == null);

            List<Entity> others = holder.level().getEntitiesOfClass(
                    Entity.class, searchBox,
                    e -> e != holder && !(e instanceof LivingEntity));

            for (Entity target : others) {

                // Ignora projéteis próprios
                if (target instanceof Projectile proj) {
                    Entity owner = proj.getOwner();
                    if (owner != null && owner.equals(holder))
                        continue;
                }

                // ── Projéteis ────────────────────────────────────────────────
                if (target instanceof Projectile) {

                    // Já congelado → o removeIf acima já re-aplicou o freeze;
                    // não há nada mais a fazer neste tick.
                    if (frozenProjectiles.containsKey(target.getUUID()))
                        continue;

                    // Projétil livre — verifica se está dentro do range
                    double distSq = holderPos.distanceToSqr(target.position());
                    if (distSq > rangeSq)
                        continue;

                    double dist = Math.sqrt(distSq);
                    double fator = calcFator(dist);

                    if (fator == 0.0 || dist <= this.effect * 1.5) {
                        // Primeiro e único congelamento
                        Vec3 stopPos = calcStopPos(target, holderPos);
                        applyFreeze(target, stopPos);
                        frozenProjectiles.put(target.getUUID(), stopPos);

                        System.out.println("[Limitless] NOVO CONGELAMENTO | uuid=" + target.getUUID()
                                + " | dist=" + String.format("%.3f", dist)
                                + " | stopPos=" + String.format("%.2f %.2f %.2f",
                                        stopPos.x, stopPos.y, stopPos.z));
                    } else {
                        // Desaceleração gradual
                        Vec3 vel = target.getDeltaMovement();
                        target.setDeltaMovement(vel.x * fator, vel.y * fator, vel.z * fator);

                        System.out.println("[Limitless] DESACELERANDO | dist=" + String.format("%.3f", dist)
                                + " | fator=" + String.format("%.5f", fator));
                    }
                    continue;
                }

                // ── Outras entidades inanimadas ──────────────────────────────
                double distSq = holderPos.distanceToSqr(target.position());

                if (distSq > rangeSq) {
                    frozenPositions.remove(target.getUUID());
                    continue;
                }

                double dist = Math.sqrt(distSq);
                double fator = calcFator(dist);

                if (fator == 0.0) {
                    Vec3 frozen = frozenPositions.computeIfAbsent(
                            target.getUUID(), k -> target.position());
                    applyFreeze(target, frozen);
                } else {
                    frozenPositions.remove(target.getUUID());
                    Vec3 vel = target.getDeltaMovement();
                    target.setDeltaMovement(vel.x * fator, vel.y * fator, vel.z * fator);
                }
            }
        }

        return super.tick(holder, instance, enabled);
    }

    @Override
    public void lastTick(LivingEntity holder, AbilityInstance<?> instance) {
        if (holder.level().isClientSide())
            return;

        frozenProjectiles.clear();
        frozenPositions.clear();

        Vec3 holderPos = holder.position().add(0, holder.getBbHeight() / 2.0, 0);
        AABB searchBox = new AABB(
                holderPos.x - this.range * 2, holderPos.y - this.range * 2,
                holderPos.z - this.range * 2,
                holderPos.x + this.range * 2, holderPos.y + this.range * 2,
                holderPos.z + this.range * 2);

        holder.level()
                .getEntitiesOfClass(LivingEntity.class, searchBox, e -> e != holder)
                .forEach(e -> {
                    if (e instanceof Mob mob && mob.isNoAi())
                        mob.setNoAi(false);
                });
    }
}