package net.hekopdcre.cursedenergy.entity.custom;

import net.hekopdcre.cursedenergy.entity.ModEntities;
import net.hekopdcre.cursedenergy.entity.custom.ai.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
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

    private static final EntityDataAccessor<Boolean> FURY_MODE = SynchedEntityData.defineId(RikaEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(RikaEntity.class,
            EntityDataSerializers.BOOLEAN);

    private UUID ownerUUID;
    private LivingEntity cachedOwner;
    private final RikaFuryManager furyManager = new RikaFuryManager(this);
    private int dashCooldown = 0;
    private int projectileInterceptCooldown = 0;

    // -----------------------------------------------------------------------
    // THREAT MEMORY — Map<UUID, lastSeenTick> para expiração automática
    // -----------------------------------------------------------------------
    // Ameaças expiram após 6000 ticks (5 min) sem reativação
    private static final int THREAT_EXPIRY_TICKS = 6000;
    private static final int MAX_MEMORY = 5;
    private final Map<UUID, Integer> threatMemory = new LinkedHashMap<>(MAX_MEMORY + 1, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Integer> eldest) {
            return size() > MAX_MEMORY;
        }
    };

    // -----------------------------------------------------------------------
    // VOO
    // -----------------------------------------------------------------------
    private static final double FLY_FOLLOW_MIN_DIST = 2.0;
    private static final double FLY_HORIZONTAL_SPEED = 0.38;

    private LivingEntity lastFlyTarget = null;
    private LivingEntity pendingFlyTarget = null;
    private int pendingFlyTargetTicks = 0;
    private static final int FLY_TARGET_CONFIRM_TICKS = 10;

    // -----------------------------------------------------------------------
    // STUCK DETECTION
    // -----------------------------------------------------------------------
    private Vec3 lastPos = Vec3.ZERO;
    private int stuckTicks = 0;
    private boolean pathfindingBlocked = false;
    private static final int STUCK_TICKS_THRESHOLD = 15;
    private static final double STUCK_MOVE_MIN = 0.05;

    // -----------------------------------------------------------------------
    // SCHEDULERS — evita rodar sistemas pesados todo tick
    // -----------------------------------------------------------------------
    // Projectile check: todo tick (1) — reação máxima
    private static final int PROJECTILE_CHECK_INTERVAL = 1;
    // Threat cleanup: a cada 200 ticks (10s)
    private static final int THREAT_CLEANUP_INTERVAL = 200;
    // Owner health check: a cada 20 ticks (1s)
    private static final int HEALTH_CHECK_INTERVAL = 20;

    public RikaEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        return nav;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FURY_MODE, false);
        builder.define(FLYING, false);
    }

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

    @Override
    public void tick() {
        super.tick();
        this.clearFire();
        this.fallDistance = 0;

        if (!this.level().isClientSide()) {
            if (dashCooldown > 0)
                dashCooldown--;
            if (projectileInterceptCooldown > 0)
                projectileInterceptCooldown--;

            furyManager.tick();

            // Voo e stuck rodam todo tick (são leves e críticos para responsividade)
            tickStuckDetection();
            tickFlight();

            // Sistemas pesados: agendados por intervalo
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
    // VOO — setNoGravity + setDeltaMovement + move(SELF): física real, sem clip
    // -----------------------------------------------------------------------

    private void tickFlight() {
        LivingEntity owner = getOwner();
        LivingEntity target = this.getTarget();
        boolean shouldFly = shouldBeFlying(owner, target);

        entityData.set(FLYING, shouldFly);

        if (!shouldFly) {
            this.setNoGravity(false);
            // Suaviza pouso — evita freada seca
            Vec3 motion = this.getDeltaMovement();
            this.setDeltaMovement(motion.x * 0.4, 0, motion.z * 0.4);
            lastFlyTarget = null;
            pendingFlyTarget = null;
            pendingFlyTargetTicks = 0;
            if (owner != null && this.distanceTo(owner) < 4.0) {
                pathfindingBlocked = false;
                stuckTicks = 0;
            }
            return;
        }

        // Sem gravidade — sem noPhysics, colisão permanece ativa
        this.setNoGravity(true);
        this.fallDistance = 0;

        // Debounce do alvo de voo — sempre segue o owner durante voo
        LivingEntity candidate = owner;
        if (candidate != lastFlyTarget) {
            if (candidate == pendingFlyTarget) {
                pendingFlyTargetTicks++;
                if (pendingFlyTargetTicks >= FLY_TARGET_CONFIRM_TICKS) {
                    lastFlyTarget = candidate;
                    pendingFlyTarget = null;
                    pendingFlyTargetTicks = 0;
                }
            } else {
                pendingFlyTarget = candidate;
                pendingFlyTargetTicks = 1;
            }
        }

        LivingEntity flyTarget = lastFlyTarget != null ? lastFlyTarget : candidate;
        if (flyTarget == null)
            return;

        Vec3 toTarget = flyTarget.position().subtract(this.position());
        double hDist = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
        double fullDist = this.distanceTo(flyTarget);

        // Velocidade horizontal suavizada
        double vx = 0, vz = 0;
        if (fullDist > FLY_FOLLOW_MIN_DIST) {
            Vec3 hDir = new Vec3(toTarget.x, 0, toTarget.z).normalize();
            double speed = Math.min(FLY_HORIZONTAL_SPEED, hDist * 0.15);
            vx = hDir.x * speed;
            vz = hDir.z * speed;
        }

        // Velocidade vertical com deadzone — evita micro-oscilação quando próxima do
        // alvo
        double targetY = getTargetFlyY(owner, flyTarget);
        double dy = targetY - this.getY();
        double vy = 0;
        if (Math.abs(dy) > 0.15) {
            vy = Mth.clamp(dy * 0.25, -0.6, 0.6);
        }

        // Anti-wall-stuck: colisão horizontal pode suprimir movimento vertical no
        // FlyingMoveControl.
        // Se está colidindo de lado E precisa subir, força impulso mínimo de 0.35 pra
        // escalar a parede.
        if (this.horizontalCollision && dy > 1.0) {
            vy = Math.max(vy, 0.35);
            // Reduz avanço horizontal durante subida — evita raspar parede em diagonal
            vx *= 0.35;
            vz *= 0.35;
        }

        // setDeltaMovement: engine move sozinho no tick — sem move() manual (evita
        // duplo movimento)
        this.setDeltaMovement(vx, vy, vz);

        // Rotação
        if (fullDist > FLY_FOLLOW_MIN_DIST) {
            float yaw = (float) (Math.toDegrees(Math.atan2(
                    flyTarget.getZ() - this.getZ(),
                    flyTarget.getX() - this.getX())) - 90);
            this.setYRot(yaw);
            this.yRotO = yaw;
            this.setYHeadRot(yaw);
        }
    }

    private void tickStuckDetection() {
        LivingEntity owner = getOwner();

        if (entityData.get(FLYING)) {
            stuckTicks = 0;
            lastPos = this.position();
            if (owner != null && this.distanceTo(owner) < 4.0)
                pathfindingBlocked = false;
            return;
        }

        if (owner == null || this.getTarget() != null) {
            stuckTicks = 0;
            lastPos = this.position();
            return;
        }

        double distToOwner = this.distanceTo(owner);
        if (distToOwner < 4.0) {
            stuckTicks = 0;
            pathfindingBlocked = false;
            lastPos = this.position();
            return;
        }

        double moved = this.position().distanceTo(lastPos);
        lastPos = this.position();

        if (distToOwner > 6.0 && moved < STUCK_MOVE_MIN) {
            stuckTicks++;
            if (stuckTicks >= STUCK_TICKS_THRESHOLD)
                pathfindingBlocked = true;
        } else {
            stuckTicks = Math.max(0, stuckTicks - 2);
            if (stuckTicks == 0)
                pathfindingBlocked = false;
        }
    }

    /**
     * Rika só voa quando andando/subindo bloco não é suficiente:
     * 1. pathfindingBlocked — travou tentando chegar no owner (stuck detection)
     * 2. owner está sobre hazard (lava, fogo) — ela precisa flutuar pra não cair
     * 3. caminho entre ela e o owner passa por fluido perigoso (água, lava)
     * 4. owner está num lugar sem blocos pisáveis abaixo (void, buraco, plataforma
     * alta)
     * detectado por: owner no ar E altura > threshold E Rika não consegue chegar
     */
    private boolean shouldBeFlying(LivingEntity owner, LivingEntity target) {
        if (owner == null)
            return false;

        // 1. Pathfinding terrestre travou — única saída é voar
        if (pathfindingBlocked)
            return true;

        // 2. Owner (ou Rika) está sobre hazard direto
        if (isOwnerOverHazard(owner))
            return true;
        if (isRikaOverHazard())
            return true;

        // 3. Caminho entre Rika e owner passa por fluido perigoso
        if (hasFluidBetween(owner))
            return true;

        // 4. Owner está numa posição inacessível por bloco:
        // owner no ar (sem chão sob ele) E diferença de altura grande o suficiente
        // pra que step-up não alcance
        if (isOwnerOnUnreachablePlatform(owner))
            return true;

        return false;
    }

    /** Owner está em cima de lava/fogo ou na beira imediata */
    private boolean isOwnerOverHazard(LivingEntity owner) {
        if (owner == null)
            return false;
        Level lvl = this.level();
        BlockPos pos = owner.blockPosition();

        if (lvl.getFluidState(pos).is(FluidTags.LAVA))
            return true;
        if (lvl.getFluidState(pos.below()).is(FluidTags.LAVA))
            return true;
        if (lvl.getBlockState(pos.below()).is(Blocks.FIRE))
            return true;

        // Verifica 2 blocos à frente na direção do owner
        for (int d = 1; d <= 2; d++) {
            BlockPos ahead = pos.relative(owner.getDirection(), d);
            if (lvl.getFluidState(ahead).is(FluidTags.LAVA))
                return true;
            if (lvl.getFluidState(ahead.below()).is(FluidTags.LAVA))
                return true;
            if (lvl.getBlockState(ahead.below()).is(Blocks.FIRE))
                return true;
        }
        return false;
    }

    /** Rika está sobre lava/fogo — precisa voar pra sobreviver */
    private boolean isRikaOverHazard() {
        Level lvl = this.level();
        BlockPos pos = this.blockPosition();
        if (lvl.getFluidState(pos).is(FluidTags.LAVA))
            return true;
        if (lvl.getFluidState(pos.below()).is(FluidTags.LAVA))
            return true;
        if (lvl.getBlockState(pos.below()).is(Blocks.FIRE))
            return true;
        return false;
    }

    /**
     * Verifica se a linha reta entre Rika e o owner passa por água ou lava.
     * Amostrado em 5 pontos ao longo do caminho — barato o suficiente pra rodar
     * todo tick.
     */
    private boolean hasFluidBetween(LivingEntity owner) {
        Vec3 from = this.position();
        Vec3 to = owner.position();
        Level lvl = this.level();

        for (int i = 1; i <= 4; i++) {
            double t = i / 5.0;
            BlockPos mid = BlockPos.containing(
                    from.x + (to.x - from.x) * t,
                    from.y + (to.y - from.y) * t,
                    from.z + (to.z - from.z) * t);
            var fluid = lvl.getFluidState(mid);
            if (fluid.is(FluidTags.WATER) || fluid.is(FluidTags.LAVA))
                return true;
        }
        return false;
    }

    /**
     * Owner está numa plataforma que Rika não alcança andando:
     * owner não está no chão E diferença de Y supera o que step-up cobre (>2.5
     * blocos).
     * Não ativa se owner só pulou — pulou e voltou ao chão em menos de 10 ticks.
     */
    private static final int AIRBORNE_CONFIRM_TICKS = 10;
    private int ownerAirborneTicks = 0;

    private boolean isOwnerOnUnreachablePlatform(LivingEntity owner) {
        boolean ownerNotOnGround = !owner.onGround() && !owner.isInWater() && !owner.isInLava();
        double heightDiff = owner.getY() - this.getY();

        if (ownerNotOnGround && heightDiff > 2.5) {
            ownerAirborneTicks++;
        } else {
            ownerAirborneTicks = Math.max(0, ownerAirborneTicks - 2);
        }

        // Só confirma voo depois de AIRBORNE_CONFIRM_TICKS seguidos
        // — evita ativar por pulo simples do player
        return ownerAirborneTicks >= AIRBORNE_CONFIRM_TICKS;
    }

    /** Retorna o Y alvo para o voo */
    private double getTargetFlyY(LivingEntity owner, LivingEntity flyTarget) {
        if (owner != null && isOwnerOverHazard(owner))
            return findHazardSurfaceY(owner) + 1.0;
        if (owner != null) {
            if (pathfindingBlocked)
                return owner.getY() + 2.0;
            return owner.getY();
        }
        return this.getY();
    }

    private double findHazardSurfaceY(LivingEntity owner) {
        Level lvl = this.level();
        BlockPos base = owner.blockPosition();
        for (int i = 0; i <= 8; i++) {
            BlockPos check = base.below(i);
            if (lvl.getFluidState(check).is(FluidTags.LAVA)
                    || lvl.getBlockState(check).is(Blocks.FIRE))
                return check.getY() + 1.0;
        }
        return owner.getY();
    }

    // -----------------------------------------------------------------------
    // SISTEMAS AGENDADOS
    // -----------------------------------------------------------------------

    private void updateBehaviorByOwnerHealth() {
        LivingEntity owner = getOwner();
        if (owner == null)
            return;
        float healthPct = owner.getHealth() / owner.getMaxHealth();
        if (healthPct < 0.3f && !furyManager.isActive() && !furyManager.isOnCooldown())
            furyManager.triggerFury();
    }

    /**
     * Roda todo tick.
     *
     * Lógica:
     * 1. Filtra apenas projéteis PERIGOSOS (AbstractArrow, Fireball, ThrownTrident,
     * AbstractHurtingProjectile) indo em direção ao owner.
     * 2. Ordena por urgência: dist / speed — qual chega primeiro.
     * 3. Cooldown de 4 ticks entre interceptações — evita snap/flicker.
     * 4. Intercept PREDITIVO: posiciona Rika onde o projétil vai estar.
     * 5. Dash adaptativo com proteção contra NaN.
     * 6. Registra atirador de TODOS os projéteis detectados na threat memory.
     */
    private void checkIncomingProjectiles() {
        LivingEntity owner = getOwner();
        if (owner == null)
            return;

        // Cooldown entre interceptações — evita snap spam e jitter
        if (projectileInterceptCooldown > 0)
            return;

        List<Projectile> projectiles = this.level().getEntitiesOfClass(
                Projectile.class,
                owner.getBoundingBox().inflate(32),
                p -> {
                    // Filtra projéteis perigosos por nome de classe — robusto entre versões
                    // sem depender de imports de subclasses específicas
                    {
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
                    }

                    Vec3 motion = p.getDeltaMovement();
                    if (motion.lengthSqr() < 0.0001)
                        return false;
                    Vec3 toOwner = owner.position().subtract(p.position());
                    if (toOwner.lengthSqr() < 0.0001)
                        return false;
                    // Ignora projétil do próprio owner
                    if (p.getOwner() == owner)
                        return false;
                    double dot = motion.normalize().dot(toOwner.normalize());
                    return dot > 0.85
                            && motion.dot(toOwner) > 0
                            && toOwner.length() < 32;
                });

        if (projectiles.isEmpty())
            return;

        // Ordena por urgência: menor (distância / velocidade) = chega primeiro
        projectiles.sort(Comparator.comparingDouble(p -> {
            double dist = p.distanceTo(owner);
            double spd = p.getDeltaMovement().length();
            return spd > 0.001 ? dist / spd : Double.MAX_VALUE;
        }));

        // Registra TODOS os atiradores na threat memory
        for (Projectile p : projectiles) {
            if (p.getOwner() instanceof LivingEntity shooter) {
                rememberThreat(shooter.getUUID());
                if (this.getTarget() == null) {
                    this.setTarget(shooter);
                    furyManager.triggerFury();
                }
            }
        }

        // Intercepta o mais urgente
        Projectile proj = projectiles.get(0);
        Vec3 projPos = proj.position();
        Vec3 projMotion = proj.getDeltaMovement();
        Vec3 ownerPos = owner.position();

        // Posição futura do projétil (50% do caminho, máx 8 ticks)
        double distToOwner = projPos.distanceTo(ownerPos);
        double speed = projMotion.length();
        double ticksToImpact = speed > 0.001 ? distToOwner / speed : 1.0;
        double t = Math.min(ticksToImpact * 0.5, 8.0);
        Vec3 futurePos = projPos.add(projMotion.scale(t));

        // Intercept: 1.0 bloco na frente do owner na linha projétil→owner
        Vec3 interceptDir = ownerPos.subtract(futurePos).normalize();
        Vec3 intercept = ownerPos.subtract(interceptDir.scale(1.0));

        // teleportTo: sync correto, sem desync multiplayer
        this.teleportTo(intercept.x, intercept.y, intercept.z);

        // Vira pra encarar o projétil — sem isso ela aparece de costas
        float yaw = (float) (Math.toDegrees(Math.atan2(
                futurePos.z - this.getZ(),
                futurePos.x - this.getX())) - 90F);
        this.setYRot(yaw);
        this.yRotO = yaw;
        this.setYHeadRot(yaw);

        // Dash adaptativo em direção à posição futura — proteção contra NaN
        Vec3 dashVec = futurePos.subtract(this.position());
        if (dashVec.lengthSqr() > 0.0001) {
            double dashSpeed = Math.min(0.8, dashVec.length() * 0.2);
            this.setDeltaMovement(dashVec.normalize().scale(dashSpeed));
        }

        // Bloqueia nova interceptação por 4 ticks (0.2s) — sem snap spam
        projectileInterceptCooldown = 4;
    }

    /**
     * Remove ameaças de entidades mortas ou cujo tick expirou.
     * Roda a cada 200 ticks — zero custo perceptível.
     */
    private void cleanExpiredThreats() {
        if (!(this.level() instanceof ServerLevel serverLevel))
            return;
        int now = this.tickCount;
        threatMemory.entrySet().removeIf(entry -> {
            // Expirou por tempo
            if (now - entry.getValue() > THREAT_EXPIRY_TICKS)
                return true;
            // Entidade morta ou sumida
            Entity e = serverLevel.getEntity(entry.getKey());
            return e == null || !e.isAlive();
        });
    }

    // -----------------------------------------------------------------------
    // THREAT MEMORY API
    // -----------------------------------------------------------------------

    public void rememberThreat(UUID uuid) {
        threatMemory.put(uuid, this.tickCount);
    }

    public boolean isThreatRemembered(UUID uuid) {
        return threatMemory.containsKey(uuid);
    }

    /** Retorna view somente-leitura para goals externos consultarem */
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

    public boolean isFlying() {
        return entityData.get(FLYING);
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
    }

    // -----------------------------------------------------------------------
    // ATTRIBUTES / GECKOLIB
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

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("main_controller", 0,
                test -> test.setAndContinue(IDLE_ANIM)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}