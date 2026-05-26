package net.hekopdcre.cursedenergy.entity.custom;

import net.hekopdcre.cursedenergy.entity.ModEntities;
import net.hekopdcre.cursedenergy.entity.custom.ai.rika.OwnerScanCache;
import net.hekopdcre.cursedenergy.entity.custom.ai.rika.RikaCombatGoal;
import net.hekopdcre.cursedenergy.entity.custom.ai.rika.RikaFollowOwnerGoal;
import net.hekopdcre.cursedenergy.entity.custom.ai.rika.RikaFuryManager;
import net.hekopdcre.cursedenergy.entity.custom.ai.rika.RikaProtectOwnerGoal;
import net.hekopdcre.cursedenergy.entity.custom.ai.rika.RikaThreatDetectionGoal;
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

    // -----------------------------------------------------------------------
    // ANIMATIONS
    // -----------------------------------------------------------------------

    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.rika.idle");
    private static final RawAnimation MOVE_ANIM = RawAnimation.begin().thenLoop("animation.rika.move");
    private static final RawAnimation ATTACK_ANIM = RawAnimation.begin().thenLoop("animation.rika.attack");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // -----------------------------------------------------------------------
    // SYNCHED DATA
    // -----------------------------------------------------------------------

    private static final EntityDataAccessor<Boolean> FURY_MODE = SynchedEntityData.defineId(RikaEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_ATTACKING = SynchedEntityData.defineId(RikaEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_MOVING = SynchedEntityData.defineId(RikaEntity.class,
            EntityDataSerializers.BOOLEAN);

    // -----------------------------------------------------------------------
    // FIELDS
    // -----------------------------------------------------------------------

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
    // SCHEDULER INTERVALS
    // -----------------------------------------------------------------------

    private static final int PROJECTILE_CHECK_INTERVAL = 2;
    private static final int THREAT_CLEANUP_INTERVAL = 400;
    private static final int HEALTH_CHECK_INTERVAL = 40;
    private static final int AERIAL_PURSUIT_INTERVAL = 2;
    private static final int SCAN_CACHE_PURGE_INTERVAL = 400;

    // Guarded sync flags — evita entityData.set quando valor não mudou
    private boolean lastAttackingFlag = false;
    private boolean lastMovingFlag = false;

    // -----------------------------------------------------------------------
    // CONSTRUCTOR
    // -----------------------------------------------------------------------

    public RikaEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    // -----------------------------------------------------------------------
    // SYNCHED DATA SETUP
    // -----------------------------------------------------------------------

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FURY_MODE, false);
        builder.define(IS_ATTACKING, false);
        builder.define(IS_MOVING, false);
    }

    // -----------------------------------------------------------------------
    // BASIC
    // -----------------------------------------------------------------------

    @Override
    public boolean fireImmune() {
        return true;
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

        if (this.level().isClientSide())
            return;

        applySuperpowerOnce();

        if (dashCooldown > 0)
            dashCooldown--;
        if (projectileInterceptCooldown > 0)
            projectileInterceptCooldown--;

        furyManager.tick();

        LivingEntity target = this.getTarget();
        int tc = this.tickCount;

        if (tc % AERIAL_PURSUIT_INTERVAL == 0) {
            tickAerialPursuit(target);
        }

        if (target == null || target.onGround()) {
            this.setNoGravity(false);
        }

        // Guarded animation sync — zero packet quando estado não muda
        boolean wantsAttacking = target != null
                && this.distanceToSqr(target) < 9.0
                && this.isAggressive();
        boolean wantsMoving = this.getNavigation().isInProgress();

        if (wantsAttacking != lastAttackingFlag) {
            entityData.set(IS_ATTACKING, wantsAttacking);
            lastAttackingFlag = wantsAttacking;
        }
        if (wantsMoving != lastMovingFlag) {
            entityData.set(IS_MOVING, wantsMoving);
            lastMovingFlag = wantsMoving;
        }

        if (tc % PROJECTILE_CHECK_INTERVAL == 0)
            checkIncomingProjectiles();
        if (tc % HEALTH_CHECK_INTERVAL == 0)
            updateBehaviorByOwnerHealth();
        if (tc % THREAT_CLEANUP_INTERVAL == 0)
            cleanExpiredThreats();
        if (tc % SCAN_CACHE_PURGE_INTERVAL == 0)
            OwnerScanCache.purgeStale(tc);
    }

    // -----------------------------------------------------------------------
    // AERIAL PURSUIT
    // -----------------------------------------------------------------------

    private void tickAerialPursuit(LivingEntity target) {
        if (target == null || target.onGround())
            return;

        LivingEntity owner = getOwner();
        if (owner != null) {
            boolean targetingOwner = (target instanceof Mob mob && mob.getTarget() == owner)
                    || target.getLastHurtMob() == owner;
            if (!targetingOwner)
                return;
        }

        double distSq = this.distanceToSqr(target);
        if (distSq < 6.25)
            return; // < 2.5²

        double dx = target.getX() - this.getX();
        double dy = target.getY() - this.getY();
        double dz = target.getZ() - this.getZ();
        double inv = 1.0 / Math.sqrt(distSq);

        double nx = dx * inv;
        double ny = dy * inv;
        double nz = dz * inv;

        double step = this.isFuryMode() ? 1.8 : 1.2;

        // setPos em vez de teleportTo — menos nuclear, sem packet de teleporte,
        // sem chunk resend, sem rubberband client/server
        this.setPos(
                this.getX() + nx * step,
                this.getY() + ny * step,
                this.getZ() + nz * step);

        float yaw = (float) (Math.toDegrees(Math.atan2(nz, nx)) - 90F);
        float pitch = (float) -Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, ny))));

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

            var key = ResourceKey.create(
                    PalladiumRegistryKeys.POWER,
                    Identifier.fromNamespaceAndPath("ce", "rika"));

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
        if (healthPct < 0.3f && !furyManager.isActive() && !furyManager.isOnCooldown()) {
            furyManager.triggerFury();
        }
    }

    // -----------------------------------------------------------------------
    // PROJECTILE PROTECTION
    // -----------------------------------------------------------------------

    private void checkIncomingProjectiles() {
        LivingEntity owner = getOwner();
        if (owner == null || projectileInterceptCooldown > 0)
            return;

        OwnerScanCache.ScanResult scan = OwnerScanCache.get(owner, this.tickCount, this);
        List<Projectile> projectiles = scan.dangerousProjectiles;

        if (projectiles.isEmpty())
            return;

        // Seleção O(n) do projétil mais urgente — sem sort
        Projectile mostUrgent = null;
        double minArrival = Double.MAX_VALUE;

        for (Projectile p : projectiles) {
            double dist = p.distanceTo(owner);
            double spd = p.getDeltaMovement().length();
            double arrival = spd > 0.001 ? dist / spd : Double.MAX_VALUE;
            if (arrival < minArrival) {
                minArrival = arrival;
                mostUrgent = p;
            }
        }

        // Reagir a atiradores
        for (Projectile p : projectiles) {
            if (!(p.getOwner() instanceof LivingEntity shooter))
                continue;

            rememberThreat(shooter.getUUID());

            LivingEntity current = this.getTarget();
            if (current == null || !current.isAlive() || current != shooter) {
                this.setTarget(shooter);

                if (dashCooldown == 0) {
                    double dx = shooter.getX() - this.getX();
                    double dz = shooter.getZ() - this.getZ();
                    double len = Math.sqrt(dx * dx + dz * dz);
                    if (len > 0.001) {
                        double dashPower = isFuryMode() ? 3.5 : 3.0;
                        double inv = dashPower / len;
                        this.setDeltaMovement(dx * inv, 0.3, dz * inv);
                        this.setDashCooldown(40);
                    }
                }

                furyManager.triggerFury();
                break;
            }
        }

        // Interceptar o projétil mais urgente
        if (mostUrgent != null) {
            interceptProjectile(mostUrgent, owner);
            projectileInterceptCooldown = 2;
        }
    }

    private void interceptProjectile(Projectile proj, LivingEntity owner) {
        // Posições inline — sem Vec3 intermediário
        double projX = proj.getX();
        double projY = proj.getY();
        double projZ = proj.getZ();

        double motX = proj.getDeltaMovement().x;
        double motY = proj.getDeltaMovement().y;
        double motZ = proj.getDeltaMovement().z;

        double ownerX = owner.getX();
        double ownerY = owner.getY();
        double ownerZ = owner.getZ();

        // Distância projétil → owner
        double dox = ownerX - projX;
        double doy = ownerY - projY;
        double doz = ownerZ - projZ;
        double dist = Math.sqrt(dox * dox + doy * doy + doz * doz);

        double speed = Math.sqrt(motX * motX + motY * motY + motZ * motZ);
        double t = Math.min((speed > 0.001 ? dist / speed : 1.0) * 0.5, 8.0);

        // Posição futura do projétil
        double futX = projX + motX * t;
        double futY = projY + motY * t;
        double futZ = projZ + motZ * t;

        // Direção owner → futurePos (interceptDir inverso)
        double idX = ownerX - futX;
        double idY = ownerY - futY;
        double idZ = ownerZ - futZ;
        double idLen = Math.sqrt(idX * idX + idY * idY + idZ * idZ);
        if (idLen > 0.001) {
            idX /= idLen;
            idY /= idLen;
            idZ /= idLen;
        }

        // Ponto de intercepção
        double icX = ownerX - idX;
        double icY = ownerY - idY;
        double icZ = ownerZ - idZ;

        // setPos — mesma razão do aerial pursuit
        this.setPos(icX, icY, icZ);

        // Face the incoming projectile
        double fx = (speed > 0.001) ? -motX / speed : 0;
        double fy = (speed > 0.001) ? -motY / speed : 0;
        double fz = (speed > 0.001) ? -motZ / speed : 0;

        float yaw = (float) (Math.toDegrees(Math.atan2(fz, fx)) - 90F);
        float pitch = (float) -Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, fy))));

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
    // DANGER CHECK
    // -----------------------------------------------------------------------

    public boolean isDangerNearOwner() {
        LivingEntity owner = getOwner();
        if (owner == null)
            return false;

        OwnerScanCache.ScanResult scan = OwnerScanCache.get(owner, this.tickCount, this);

        for (Mob mob : scan.hostileMobs) {
            if (mob.getTarget() == owner && mob.distanceToSqr(owner) < 256) {
                return true;
            }
        }

        return !scan.dangerousProjectiles.isEmpty();
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
        if (uuid != null)
            OwnerScanCache.invalidate(uuid);
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    @Override
    public boolean hurtServer(ServerLevel level,
            net.minecraft.world.damagesource.DamageSource source,
            float amount) {
        boolean result = super.hurtServer(level, source, amount);

        if (result
                && source.getEntity() instanceof LivingEntity attacker
                && attacker != this
                && attacker != getOwner()) {
            rememberThreat(attacker.getUUID());
            LivingEntity current = this.getTarget();
            if (current == null || !current.isAlive()) {
                this.setTarget(attacker);
                getFuryManager().triggerFury();
            }
        }

        return result;
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
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.73);
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(80.0);
            getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        } else {
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.64);
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(45.0);
            getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.6);
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
    // ANIMATION FLAGS
    // -----------------------------------------------------------------------

    public boolean isAttackingAnim() {
        return entityData.get(IS_ATTACKING);
    }

    public boolean isMovingAnim() {
        return entityData.get(IS_MOVING);
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
                "main_controller", 2,
                state -> {
                    if (isAttackingAnim())
                        return state.setAndContinue(ATTACK_ANIM);
                    if (isMovingAnim())
                        return state.setAndContinue(MOVE_ANIM);
                    return state.setAndContinue(IDLE_ANIM);
                }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}