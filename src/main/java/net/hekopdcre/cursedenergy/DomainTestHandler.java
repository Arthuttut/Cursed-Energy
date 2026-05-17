package net.hekopdcre.cursedenergy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.threetag.palladium.power.ability.AbilityUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DomainTestHandler {

    private static final int RADIUS = 15;
    private static final int HEIGHT = 15;

    private static final double EXPAND_SPEED = 0.75;
    private static final double RESTORE_SPEED = 0.85;
    private static final double DOME_THICKNESS = 1.65;

    private static final int RESTORE_BATCH_SIZE = 200;

    private static final Identifier TEN_SHADOWS_POWER = Identifier.parse("ce:ten_shadows");

    private static final String EXPANSION_ABILITY = "expansion";

    private static final Map<UUID, ActiveDomain> ACTIVE_DOMAINS = new HashMap<>();
    private static final Map<UUID, ActiveDomain> RESTORING_DOMAINS = new HashMap<>();

    // Lookup table de quadrados pré-calculados — evita x*x, y*y, z*z nos loops
    private static final int[] SQUARES;
    static {
        SQUARES = new int[RADIUS + 1];
        for (int i = 0; i <= RADIUS; i++)
            SQUARES[i] = i * i;
    }

    // Cache de BlockState — evita chamar defaultBlockState() milhares de vezes nos
    // loops
    private static final BlockState BS_CRYING_OBSIDIAN = Blocks.CRYING_OBSIDIAN.defaultBlockState();
    private static final BlockState BS_BLACKSTONE = Blocks.BLACKSTONE.defaultBlockState();
    private static final BlockState BS_POLISHED_BLACKSTONE = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
    private static final BlockState BS_BLACK_CONCRETE = Blocks.BLACK_CONCRETE.defaultBlockState();
    private static final BlockState BS_AIR = Blocks.AIR.defaultBlockState();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player))
            return;
        if (!(player.level() instanceof ServerLevel level))
            return;

        UUID uuid = player.getUUID();
        boolean expansionActive = hasDomainExpansionEnabled(player);

        if (expansionActive) {
            ActiveDomain restoring = RESTORING_DOMAINS.remove(uuid);
            if (restoring != null)
                restoring.restoreAll(level);

            ActiveDomain domain = ACTIVE_DOMAINS.get(uuid);
            if (domain == null) {
                domain = new ActiveDomain(player.blockPosition());
                ACTIVE_DOMAINS.put(uuid, domain);

                // Partículas de ativação se o jogador estiver na água — "oceano repelido"
                if (player.isInWater()) {
                    spawnActivationParticles(level, player);
                }
            }

            domain.tickExpand(level);
        } else {
            ActiveDomain active = ACTIVE_DOMAINS.remove(uuid);
            if (active != null) {
                active.startRestore();
                RESTORING_DOMAINS.put(uuid, active);
            }

            ActiveDomain restoring = RESTORING_DOMAINS.get(uuid);
            if (restoring != null) {
                restoring.tickRestore(level);
                if (restoring.isRestoreFinished()) {
                    RESTORING_DOMAINS.remove(uuid);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player))
            return;
        if (!(player.level() instanceof ServerLevel level))
            return;

        UUID uuid = player.getUUID();

        ActiveDomain active = ACTIVE_DOMAINS.remove(uuid);
        if (active != null)
            active.restoreAll(level);

        ActiveDomain restoring = RESTORING_DOMAINS.remove(uuid);
        if (restoring != null)
            restoring.restoreAll(level);
    }

    // Partículas escuras de "energia amaldiçoada repelindo oceano"
    private static void spawnActivationParticles(ServerLevel level, ServerPlayer player) {
        double x = player.getX();
        double y = player.getY() + player.getBbHeight() * 0.5;
        double z = player.getZ();

        level.sendParticles(ParticleTypes.SQUID_INK, x, y, z, 40, 1.5, 1.5, 1.5, 0.1);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 20, 2.0, 1.0, 2.0, 0.05);
        level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, x, y, z, 30, 1.0, 0.5, 1.0, 0.2);
    }

    private static boolean hasDomainExpansionEnabled(ServerPlayer player) {
        return AbilityUtil.isEnabled(player, TEN_SHADOWS_POWER, EXPANSION_ABILITY);
    }

    private static class ActiveDomain {

        private final BlockPos center;

        private double currentRadius = 0;
        private double restoreRadius = RADIUS + 3;

        private boolean restoring = false;
        private int lastTickedRadius = -1;

        private final Map<BlockPos, BlockState> originalBlocks = new HashMap<>();

        private List<BlockPos> restoreQueue = new ArrayList<>();
        private int restoreIndex = 0;

        private ActiveDomain(BlockPos center) {
            this.center = center.immutable();
        }

        private void tickExpand(ServerLevel level) {
            if (currentRadius < RADIUS)
                currentRadius += EXPAND_SPEED;

            int intRadius = (int) currentRadius;
            if (intRadius == lastTickedRadius)
                return;
            lastTickedRadius = intRadius;

            createDome(level);
            createFloor(level);
            clearInterior(level);
        }

        private void createDome(ServerLevel level) {
            int r = RADIUS;
            double revealRadiusSq = Math.min(currentRadius, RADIUS);
            revealRadiusSq *= revealRadiusSq;

            double innerSq = (RADIUS - DOME_THICKNESS) * (RADIUS - DOME_THICKNESS);
            double outerSq = (RADIUS + DOME_THICKNESS) * (RADIUS + DOME_THICKNESS);

            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
            for (int x = -r; x <= r; x++) {
                int xx = SQUARES[Math.abs(x)];
                for (int y = 0; y <= HEIGHT; y++) {
                    int yy = SQUARES[Math.abs(y)];
                    for (int z = -r; z <= r; z++) {
                        int zz = SQUARES[Math.abs(z)];

                        if (xx + zz > revealRadiusSq)
                            continue;

                        double sphereDistSq = xx + yy + zz;
                        if (sphereDistSq < innerSq)
                            continue;
                        if (sphereDistSq > outerSq)
                            continue;

                        mutable.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                        setBlock(level, mutable, getPrettyDomeBlock(x, y, z), 18);
                    }
                }
            }
        }

        private BlockState getPrettyDomeBlock(int x, int y, int z) {
            int pattern = Math.abs((x * 31) + (y * 17) + (z * 13)) % 100;

            if (pattern < 9)
                return BS_CRYING_OBSIDIAN;
            if (pattern < 20)
                return BS_BLACKSTONE;

            if (y <= 2 && pattern < 42)
                return BS_POLISHED_BLACKSTONE;
            if (y >= HEIGHT - 3 && pattern < 28)
                return BS_CRYING_OBSIDIAN;

            if ((Math.abs(x) % 7 == 0 || Math.abs(z) % 7 == 0) && pattern < 35)
                return BS_BLACKSTONE;

            return BS_BLACK_CONCRETE;
        }

        private void createFloor(ServerLevel level) {
            int r = RADIUS;
            double revealRadiusSq = Math.min(currentRadius, RADIUS);
            revealRadiusSq *= revealRadiusSq;

            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
            for (int x = -r; x <= r; x++) {
                int xx = SQUARES[Math.abs(x)];
                for (int z = -r; z <= r; z++) {
                    if (xx + SQUARES[Math.abs(z)] > revealRadiusSq)
                        continue;
                    mutable.set(center.getX() + x, center.getY() - 1, center.getZ() + z);
                    setBlock(level, mutable, getPrettyFloorBlock(x, z), 18);
                }
            }
        }

        private BlockState getPrettyFloorBlock(int x, int z) {
            int pattern = Math.abs((x * 19) + (z * 23)) % 100;

            if (pattern < 8)
                return BS_CRYING_OBSIDIAN;
            if (pattern < 35)
                return BS_POLISHED_BLACKSTONE;
            if (pattern < 50)
                return BS_BLACKSTONE;

            return BS_BLACK_CONCRETE;
        }

        private void clearInterior(ServerLevel level) {
            int r = RADIUS;
            double revealRadiusSq = Math.min(currentRadius, RADIUS);
            revealRadiusSq *= revealRadiusSq;

            double innerSq = (RADIUS - DOME_THICKNESS) * (RADIUS - DOME_THICKNESS);

            // FIX: só limpa a "frente" da expansão — evita reprocessar interior já limpo
            // Igual Sukuna avançando: só o anel externo é processado cada tick
            double frontMinSq = Math.max(0, currentRadius - 1.5);
            frontMinSq *= frontMinSq;

            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
            for (int x = -r + 1; x <= r - 1; x++) {
                int xx = SQUARES[Math.abs(x)];
                for (int y = 0; y <= HEIGHT - 1; y++) {
                    int yy = SQUARES[Math.abs(y)];
                    for (int z = -r + 1; z <= r - 1; z++) {
                        int zz = SQUARES[Math.abs(z)];

                        double horizDistSq = xx + zz;
                        if (horizDistSq > revealRadiusSq)
                            continue;

                        double sphereDistSq = xx + yy + zz;
                        if (sphereDistSq >= innerSq)
                            continue;

                        // FIX: só processa blocos na frente da expansão, não o interior inteiro
                        if (sphereDistSq < frontMinSq)
                            continue;

                        mutable.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                        BlockState current = level.getBlockState(mutable);

                        // .is() em vez de == porque getBlockState() nem sempre retorna a mesma
                        // instância
                        if (current.is(Blocks.BLACK_CONCRETE) ||
                                current.is(Blocks.CRYING_OBSIDIAN) ||
                                current.is(Blocks.BLACKSTONE) ||
                                current.is(Blocks.POLISHED_BLACKSTONE) ||
                                current.is(Blocks.AIR))
                            continue;

                        // Fluido: limpa com flag 18 — sem physics cascade, casca já bloqueia entrada de
                        // água
                        if (!current.getFluidState().isEmpty()) {
                            setBlock(level, mutable, BS_AIR, 18);
                            continue;
                        }

                        // Interior não-fluido: limpa com flag 2 (sem physics)
                        setBlock(level, mutable, BS_AIR, 2);
                    }
                }
            }
        }

        // Método central de setBlock com suporte a flag customizada
        private void setBlock(ServerLevel level, BlockPos pos, BlockState newState, int flags) {
            BlockPos immutablePos = pos.immutable();

            BlockState existing = level.getBlockState(immutablePos);

            if (existing.is(Blocks.BEDROCK))
                return;
            if (existing.is(Blocks.BARRIER))
                return;
            if (existing.is(Blocks.COMMAND_BLOCK))
                return;
            if (existing.is(Blocks.NETHER_PORTAL))
                return;
            if (existing.is(Blocks.END_PORTAL))
                return;

            if (!originalBlocks.containsKey(immutablePos)) {
                if (level.getBlockEntity(immutablePos) != null)
                    return;
                originalBlocks.put(immutablePos, existing);
            }

            // == seguro aqui porque newState vem sempre dos nossos BS_* cacheados
            if (existing == newState)
                return;

            level.setBlock(immutablePos, newState, flags);
        }

        private void startRestore() {
            restoring = true;
            restoreRadius = RADIUS + 3;
            restoreQueue = new ArrayList<>(originalBlocks.keySet());
            restoreQueue.sort((a, b) -> Double.compare(getRestoreDistanceSq(b), getRestoreDistanceSq(a)));
            restoreIndex = 0;
        }

        private void tickRestore(ServerLevel level) {
            if (!restoring)
                return;

            restoreRadius -= RESTORE_SPEED;
            double restoreRadiusSq = restoreRadius * restoreRadius;

            int processed = 0;

            while (restoreIndex < restoreQueue.size() && processed < RESTORE_BATCH_SIZE) {
                BlockPos pos = restoreQueue.get(restoreIndex);

                if (getRestoreDistanceSq(pos) >= restoreRadiusSq || restoreRadius <= 0) {
                    BlockState original = originalBlocks.get(pos);
                    if (original != null) {
                        // flag 3 na restauração — reconstrói física e fluidos corretamente
                        if (level.isLoaded(pos))
                            level.setBlock(pos, original, 3);
                    }
                    restoreIndex++;
                    processed++;
                } else {
                    break;
                }
            }
        }

        private double getRestoreDistanceSq(BlockPos pos) {
            int x = pos.getX() - center.getX();
            int y = pos.getY() - center.getY();
            int z = pos.getZ() - center.getZ();

            if (y < 0)
                return x * x + z * z;
            return x * x + y * y + z * z;
        }

        private boolean isRestoreFinished() {
            return restoreIndex >= restoreQueue.size();
        }

        private void restoreAll(ServerLevel level) {
            for (Map.Entry<BlockPos, BlockState> entry : originalBlocks.entrySet()) {
                if (level.isLoaded(entry.getKey()))
                    level.setBlock(entry.getKey(), entry.getValue(), 3);
            }

            originalBlocks.clear();
            restoreQueue.clear();
            restoreIndex = 0;
            restoring = false;
        }
    }
}