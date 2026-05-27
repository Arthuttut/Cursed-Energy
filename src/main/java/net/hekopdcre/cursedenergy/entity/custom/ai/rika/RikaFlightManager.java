package net.hekopdcre.cursedenergy.entity.custom.ai.rika;

import net.hekopdcre.cursedenergy.entity.custom.RikaEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public final class RikaFlightManager {

    private static final int FLUID_CHECK_INTERVAL = 10;
    private static final int PATH_CHECK_INTERVAL = 40;
    private static final int FLIGHT_EXIT_DELAY = 60;
    private static final double FLUID_SCAN_DISTANCE = 12.0;
    private static final int FLUID_SCAN_STEPS = 8;

    private final RikaEntity rika;

    private boolean flying = false;
    private int exitDelay = 0;
    private int fluidCheckTimer = 0;
    private int pathCheckTimer = 0;

    // Cache do último resultado de ownerUnreachableByGround para uso no tickFlight
    private boolean ownerUnreachable = false;

    public RikaFlightManager(RikaEntity rika) {
        this.rika = rika;
    }

    // -----------------------------------------------------------------------
    // Tick principal — chamado em RikaEntity#tick apenas server-side
    // -----------------------------------------------------------------------

    public void tick() {
        fluidCheckTimer--;
        pathCheckTimer--;

        boolean needsFlight = false;

        if (fluidCheckTimer <= 0) {
            fluidCheckTimer = FLUID_CHECK_INTERVAL;
            needsFlight |= fluidOrSlowBlockBetween();
        }

        if (pathCheckTimer <= 0) {
            pathCheckTimer = PATH_CHECK_INTERVAL;
            ownerUnreachable = ownerUnreachableByGround(); // <-- salva cache
            needsFlight |= ownerUnreachable;
        } else {
            needsFlight |= ownerUnreachable; // reutiliza cache entre checks
        }

        if (flying) {
            if (needsFlight) {
                exitDelay = FLIGHT_EXIT_DELAY;
            } else {
                exitDelay--;
                if (exitDelay <= 0) {
                    exitFlight();
                    return;
                }
            }
            tickFlight();
        } else if (needsFlight) {
            enterFlight();
        }
    }

    public boolean isFlying() {
        return flying;
    }

    // -----------------------------------------------------------------------
    // Entra e sai do modo voo
    // -----------------------------------------------------------------------

    private void enterFlight() {
        flying = true;
        exitDelay = FLIGHT_EXIT_DELAY;
        rika.setNoGravity(true);
        rika.fallDistance = 0;
        rika.getNavigation().stop();
    }

    private void exitFlight() {
        flying = false;
        rika.setNoGravity(false);
        rika.getNavigation().stop();
    }

    // -----------------------------------------------------------------------
    // Movimento aéreo — empurra em direção ao alvo/dono pelo ar
    // -----------------------------------------------------------------------

    private void tickFlight() {
        LivingEntity destination = getDestination();
        if (destination == null)
            return;

        rika.fallDistance = 0;
        rika.setNoGravity(true); // garante que gravidade não reative enquanto voa

        Vec3 rikaPos = rika.position();
        double tx = destination.getX();
        double ty = destination.getY() + 1.0;
        double tz = destination.getZ();

        double dx = tx - rikaPos.x;
        double dy = ty - rikaPos.y;
        double dz = tz - rikaPos.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (dist < 2.5) {
            // ---------------------------------------------------------------
            // CORREÇÃO: chegou perto, mas se o dono ainda está inacessível
            // por terra, flutua ativamente acompanhando o Y dele.
            // Isso evita que zerando o movimento o exitDelay expire e a
            // Rika caia — ela mantém sustentação enquanto ownerUnreachable.
            // ---------------------------------------------------------------
            if (ownerUnreachable) {
                // Pequena correção vertical para acompanhar o Y do dono
                double verticalError = ty - rikaPos.y;
                double verticalCorrection = Math.signum(verticalError)
                        * Math.min(Math.abs(verticalError) * 0.1, 0.08);
                rika.setDeltaMovement(0, verticalCorrection, 0);
            } else {
                rika.setDeltaMovement(0, 0, 0);
            }
            return;
        }

        double speed = rika.isFuryMode() ? 0.55 : 0.38;
        double inv = speed / dist;

        rika.setDeltaMovement(dx * inv, dy * inv, dz * inv);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90f);
        float pitch = (float) -Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, dy / dist))));
        rika.setYRot(yaw);
        rika.yRotO = yaw;
        rika.setYHeadRot(yaw);
        rika.setXRot(pitch);
        rika.xRotO = pitch;
    }

    // -----------------------------------------------------------------------
    // Detecta fluidos ou blocos lentos entre Rika e destino
    // -----------------------------------------------------------------------

    private boolean fluidOrSlowBlockBetween() {
        LivingEntity destination = getDestination();
        if (destination == null)
            return false;

        if (!rika.level().getFluidState(rika.blockPosition()).isEmpty())
            return true;

        Vec3 from = rika.position();
        Vec3 to = destination.position();
        Vec3 step = to.subtract(from);
        double dist = step.length();
        if (dist < 1.0 || dist > FLUID_SCAN_DISTANCE)
            return false;

        Vec3 dir = step.normalize();
        Level level = rika.level();

        for (int i = 1; i <= FLUID_SCAN_STEPS; i++) {
            double t = (dist / FLUID_SCAN_STEPS) * i;
            BlockPos check = BlockPos.containing(from.add(dir.scale(t)));
            FluidState fluid = level.getFluidState(check);
            if (!fluid.isEmpty())
                return true;

            var blockState = level.getBlockState(check);
            if (blockState.getBlock().getSpeedFactor() < 0.8f)
                return true;
        }

        return false;
    }

    // -----------------------------------------------------------------------
    // Detecta se o dono está em local sem path terrestre válido
    // -----------------------------------------------------------------------

    private boolean ownerUnreachableByGround() {
        LivingEntity owner = rika.getOwner();
        if (owner == null)
            return false;

        double distSq = rika.distanceToSqr(owner);
        if (distSq < 25.0)
            return false;

        Path path = rika.getNavigation().createPath(owner, 0);
        if (path == null)
            return true;

        Vec3 end = path.getEndNode() != null
                ? new Vec3(path.getEndNode().x, path.getEndNode().y, path.getEndNode().z)
                : null;
        if (end == null)
            return true;

        double pathEndDistSq = end.distanceToSqr(owner.position());
        return pathEndDistSq > 36.0;
    }

    // -----------------------------------------------------------------------
    // Destino prioritário: alvo de combate > dono
    // -----------------------------------------------------------------------

    private LivingEntity getDestination() {
        LivingEntity target = rika.getTarget();
        if (target != null && target.isAlive())
            return target;
        return rika.getOwner();
    }
}