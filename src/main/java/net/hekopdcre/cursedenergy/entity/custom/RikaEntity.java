package net.hekopdcre.cursedenergy.entity.custom;

import net.hekopdcre.cursedenergy.entity.ModEntities;
import net.hekopdcre.cursedenergy.entity.custom.ai.*;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.threetag.palladium.power.SuperpowerUtil;
import net.threetag.palladium.registry.PalladiumRegistryKeys;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;

import java.util.*;

public class RikaEntity extends PathfinderMob implements GeoEntity {

    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.rika.idle");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private static final EntityDataAccessor<Boolean> FURY_MODE = SynchedEntityData.defineId(
            RikaEntity.class,
            EntityDataSerializers.BOOLEAN);

    private UUID ownerUUID;
    private LivingEntity cachedOwner;

    private final RikaFuryManager furyManager = new RikaFuryManager(this);

    private int dashCooldown = 0;
    private int projectileInterceptCooldown = 0;

    private boolean superpowerApplied = false;

    // -----------------------------------------------------------------------
    // THREAT MEMORY
    // -----------------------------------------------------------------------

    private static final int THREAT_EXPIRY_TICKS = 6000;
    private static final int MAX_MEMORY = 5;

    private final Map<UUID, Integer> threatMemory = new LinkedHashMap<>(MAX_MEMORY + 1, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Integer> eldest) {
            return size() > MAX_MEMORY;
        }
    };

    // -----------------------------------------------------------------------
    // SCHEDULERS
    // -----------------------------------------------------------------------

    private static final int PROJECTILE_CHECK_INTERVAL = 1;
    private static final int THREAT_CLEANUP_INTERVAL = 200;
    private static final int HEALTH_CHECK_INTERVAL = 20;

    // -----------------------------------------------------------------------
    // CONSTRUCTOR
    // -----------------------------------------------------------------------

    public RikaEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    // -----------------------------------------------------------------------
    // BASIC
    // -----------------------------------------------------------------------

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FURY_MODE, false);
    }

    // -----------------------------------------------------------------------
    // GOALS
    // -----------------------------------------------------------------------

    @Override
    protected void registerGoals() {

        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new RikaCombatGoal(this));
        this.goalSelector.addGoal(3, new RikaFollowOwnerGoal(this));
        this.goalSelector.addGoal(4, new RandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));

        this.targetSelector.addGoal(1, new RikaProtectOwnerGoal(this));
        this.targetSelector.addGoal(2, new RikaThreatDetectionGoal(this));
    }

    // -----------------------------------------------------------------------
    // TICK
    // -----------------------------------------------------------------------

    @Override
    public void tick() {

        super.tick();

        this.clearFire();
        this.fallDistance = 0;

        if (!this.level().isClientSide()) {

            applySuperpowerOnce();

            // reseta gravidade se não há alvo aéreo
            LivingEntity target = this.getTarget();
            if (target == null || target.onGround()) {
                this.setNoGravity(false);
            }

            if (dashCooldown > 0)
                dashCooldown--;

            if (projectileInterceptCooldown > 0)
                projectileInterceptCooldown--;

            furyManager.tick();

            tickAerialPursuit();

            int tc = this.tickCount;

            if (tc % PROJECTILE_CHECK_INTERVAL == 0)
                checkIncomingProjectiles();

            if (tc % HEALTH_CHECK_INTERVAL == 0)
                updateBehaviorByOwnerHealth();

            if (tc % THREAT_CLEANUP_INTERVAL == 0)
                cleanExpiredThreats();
        }
    }

    // -----------------------------------------------------------------------
    // AERIAL PURSUIT
    // -----------------------------------------------------------------------

    private void tickAerialPursuit() {

        LivingEntity target = this.getTarget();

        if (target == null)
            return;

        if (target.onGround())
            return;

        LivingEntity owner = getOwner();
        if (owner != null) {
            boolean targetingOwner = (target instanceof Mob mob && mob.getTarget() == owner)
                    || target.getLastHurtMob() == owner;

            if (!targetingOwner)
                return;
        }

        double dist = this.distanceTo(target);

        if (dist < 2.5)
            return;

        Vec3 toTarget = target.position().subtract(this.position()).normalize();

        double step = this.isFuryMode() ? 1.8 : 1.2;

        this.teleportTo(
                this.getX() + toTarget.x * step,
                this.getY() + toTarget.y * step,
                this.getZ() + toTarget.z * step);

        // olha para o projétil/alvo
        Vec3 dir = toTarget;
        float yaw = (float) (Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90F);
        float pitch = (float) -Math.toDegrees(Math.asin(dir.y));

        this.setYRot(yaw);
        this.yRotO = yaw;
        this.setYHeadRot(yaw);
        this.setXRot(pitch);
        this.xRotO = pitch;

        this.setNoGravity(true);
        this.fallDistance = 0;
        this.setDeltaMovement(Vec3.ZERO);
    }

    // -----------------------------------------------------------------------
    // SUPERPOWER
    // -----------------------------------------------------------------------

    private void applySuperpowerOnce() {

        if (superpowerApplied)
            return;

        if (!(this.level() instanceof ServerLevel serverLevel))
            return;

        try {
            var registry = serverLevel.registryAccess()
                    .lookup(PalladiumRegistryKeys.POWER)
                    .orElse(null);

            if (registry == null)
                return;

            var powerId = Identifier.fromNamespaceAndPath("ce", "rika");

            var key = ResourceKey.create(
                    PalladiumRegistryKeys.POWER,
                    powerId);

            registry.get(key).ifPresent(holder -> {
                SuperpowerUtil.setSuperpower(this, holder);
                superpowerApplied = true;
            });

        } catch (Exception ignored) {
        }
    }

    // -----------------------------------------------------------------------
    // OWNER HEALTH
    // -----------------------------------------------------------------------

    private void updateBehaviorByOwnerHealth() {

        LivingEntity owner = getOwner();

        if (owner == null)
            return;

        float healthPct = owner.getHealth() / owner.getMaxHealth();

        if (healthPct < 0.3f
                && !furyManager.isActive()
                && !furyManager.isOnCooldown()) {
            furyManager.triggerFury();
        }
    }

    // -----------------------------------------------------------------------
    // PROJECTILE PROTECTION
    // -----------------------------------------------------------------------

    private void checkIncomingProjectiles() {

        LivingEntity owner = getOwner();

        if (owner == null)
            return;

        if (projectileInterceptCooldown > 0)
            return;

        List<Projectile> projectiles = this.level().getEntitiesOfClass(
                Projectile.class,
                owner.getBoundingBox().inflate(32),
                p -> {

                    String cn = p.getClass().getName();

                    boolean dangerous = cn.contains("Arrow")
                            || cn.contains("Trident")
                            || cn.contains("Fireball")
                            || cn.contains("WitherSkull")
                            || cn.contains("ShulkerBullet")
                            || cn.contains("DragonFireball")
                            || cn.contains("LlamaSpit");

                    if (!dangerous)
                        return false;

                    Vec3 motion = p.getDeltaMovement();

                    if (motion.lengthSqr() < 0.0001)
                        return false;

                    Vec3 toOwner = owner.position().subtract(p.position());

                    if (toOwner.lengthSqr() < 0.0001)
                        return false;

                    if (p.getOwner() == owner)
                        return false;

                    double dot = motion.normalize().dot(toOwner.normalize());

                    return dot > 0.7 // limiar mais generoso para não deixar passar
                            && motion.dot(toOwner) > 0
                            && toOwner.length() < 32;
                });

        if (projectiles.isEmpty())
            return;

        // ordena pelo mais próximo de impactar o dono
        projectiles.sort(Comparator.comparingDouble(p -> {
            double dist = p.distanceTo(owner);
            double spd = p.getDeltaMovement().length();
            return spd > 0.001 ? dist / spd : Double.MAX_VALUE;
        }));

        for (Projectile p : projectiles) {
            if (p.getOwner() instanceof LivingEntity shooter) {

                rememberThreat(shooter.getUUID());

                if (this.getTarget() == null) {
                    this.setTarget(shooter);
                    furyManager.triggerFury();
                }
            }
        }

        // intercepta todos os projéteis detectados, não só o primeiro
        for (Projectile proj : projectiles) {

            Vec3 projPos = proj.position();
            Vec3 projMotion = proj.getDeltaMovement();
            Vec3 ownerPos = owner.position();

            double distToOwner = projPos.distanceTo(ownerPos);
            double speed = projMotion.length();
            double ticksToImpact = speed > 0.001 ? distToOwner / speed : 1.0;
            double t = Math.min(ticksToImpact * 0.5, 8.0);

            Vec3 futurePos = projPos.add(projMotion.scale(t));
            Vec3 interceptDir = ownerPos.subtract(futurePos).normalize();
            Vec3 intercept = ownerPos.subtract(interceptDir.scale(1.0));

            this.teleportTo(intercept.x, intercept.y, intercept.z);

            // olha na direção de onde o projétil vem
            Vec3 facingDir = projMotion.normalize().reverse();
            float yaw = (float) (Math.toDegrees(Math.atan2(facingDir.z, facingDir.x)) - 90F);
            float pitch = (float) -Math.toDegrees(Math.asin(
                    Math.max(-1.0, Math.min(1.0, facingDir.y))));

            this.setYRot(yaw);
            this.yRotO = yaw;
            this.setYHeadRot(yaw);
            this.setXRot(pitch);
            this.xRotO = pitch;

            // faz a Rika ficar entre o projétil e o dono
            this.setNoGravity(true);
            this.fallDistance = 0;
            this.setDeltaMovement(Vec3.ZERO);

            break; // teleporta para o mais prioritário; os outros serão pegos nos próximos ticks
        }

        projectileInterceptCooldown = 2; // cooldown menor = reação mais rápida
    }

    // -----------------------------------------------------------------------
    // THREAT CLEANUP
    // -----------------------------------------------------------------------

    private void cleanExpiredThreats() {

        if (!(this.level() instanceof ServerLevel serverLevel))
            return;

        int now = this.tickCount;

        threatMemory.entrySet().removeIf(entry -> {

            if (now - entry.getValue() > THREAT_EXPIRY_TICKS)
                return true;

            Entity e = serverLevel.getEntity(entry.getKey());

            return e == null || !e.isAlive();
        });
    }

    // -----------------------------------------------------------------------
    // THREAT API
    // -----------------------------------------------------------------------

    public void rememberThreat(UUID uuid) {
        threatMemory.put(uuid, this.tickCount);
    }

    public boolean isThreatRemembered(UUID uuid) {
        return threatMemory.containsKey(uuid);
    }

    public Set<UUID> getThreatMemory() {
        return Collections.unmodifiableSet(threatMemory.keySet());
    }

    // -----------------------------------------------------------------------
    // OWNER
    // -----------------------------------------------------------------------

    public LivingEntity getOwner() {

        if (ownerUUID == null)
            return null;

        if (cachedOwner != null && cachedOwner.isAlive())
            return cachedOwner;

        if (this.level() instanceof ServerLevel serverLevel) {

            Entity e = serverLevel.getEntity(ownerUUID);

            if (e instanceof LivingEntity living) {
                cachedOwner = living;
                return cachedOwner;
            }
        }

        return null;
    }

    public void setOwnerUUID(UUID uuid) {
        this.ownerUUID = uuid;
        this.cachedOwner = null;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    // -----------------------------------------------------------------------
    // FURY
    // -----------------------------------------------------------------------

    public boolean isFuryMode() {
        return entityData.get(FURY_MODE);
    }

    public void setFuryMode(boolean fury) {

        entityData.set(FURY_MODE, fury);

        if (fury) {
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.65);
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(80.0);
            getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        } else {
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.38);
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(45.0);
            getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.5);
        }
    }

    public RikaFuryManager getFuryManager() {
        return furyManager;
    }

    public int getDashCooldown() {
        return dashCooldown;
    }

    public void setDashCooldown(int v) {
        dashCooldown = v;
    }

    // -----------------------------------------------------------------------
    // SAVE / LOAD
    // -----------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(ValueOutput output) {

        super.addAdditionalSaveData(output);

        if (ownerUUID != null)
            output.store("OwnerUUID", UUIDUtil.CODEC, ownerUUID);
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {

        super.readAdditionalSaveData(input);

        input.read("OwnerUUID", UUIDUtil.CODEC).ifPresent(uuid -> ownerUUID = uuid);

        superpowerApplied = false;
    }

    // -----------------------------------------------------------------------
    // ATTRIBUTES
    // -----------------------------------------------------------------------

    public static AttributeSupplier.Builder createAttributes() {

        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 350.0)
                .add(Attributes.MOVEMENT_SPEED, 0.38)
                .add(Attributes.ATTACK_DAMAGE, 45.0)
                .add(Attributes.ARMOR, 15.0)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    public static void onRegisterAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.RIKA.get(), createAttributes().build());
    }

    // -----------------------------------------------------------------------
    // GECKOLIB
    // -----------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {

        controllers.add(new AnimationController<>(
                "main_controller",
                0,
                test -> test.setAndContinue(IDLE_ANIM)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}