package net.hekopdcre.cursedenergy;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.threetag.palladium.power.ability.AbilityUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DomainTestHandler {

    // 30 blocos de largura = raio 15
    private static final int RADIUS = 15;

    // Altura proporcional ao raio
    private static final int HEIGHT = 15;

    private static final double EXPAND_SPEED = 0.75;
    private static final double RESTORE_SPEED = 0.85;

    // Parede mais grossa = menos buraco no domo
    private static final double DOME_THICKNESS = 1.65;

    private static final Identifier TEN_SHADOWS_POWER =
            Identifier.parse("ce:ten_shadows");

    private static final String EXPANSION_ABILITY = "expansion";

    private static final Map<UUID, ActiveDomain> ACTIVE_DOMAINS = new HashMap<>();
    private static final Map<UUID, ActiveDomain> RESTORING_DOMAINS = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        UUID uuid = player.getUUID();

        boolean expansionActive = hasDomainExpansionEnabled(player);

        if (expansionActive) {
            ActiveDomain restoring = RESTORING_DOMAINS.remove(uuid);

            if (restoring != null) {
                restoring.restoreAll(level);
            }

            ActiveDomain domain = ACTIVE_DOMAINS.get(uuid);

            if (domain == null) {
                domain = new ActiveDomain(player.blockPosition());
                ACTIVE_DOMAINS.put(uuid, domain);
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

    private static boolean hasDomainExpansionEnabled(ServerPlayer player) {
        return AbilityUtil.isEnabled(
                player,
                TEN_SHADOWS_POWER,
                EXPANSION_ABILITY
        );
    }

    private static class ActiveDomain {

        private final BlockPos center;

        private double currentRadius = 0;
        private double restoreRadius = RADIUS + 3;

        private boolean restoring = false;

        private final Map<BlockPos, BlockState> originalBlocks = new HashMap<>();
        private final Set<BlockPos> changedBlocks = new HashSet<>();
        private final Set<BlockPos> restoredBlocks = new HashSet<>();

        private ActiveDomain(BlockPos center) {
            this.center = center.immutable();
        }

        private void tickExpand(ServerLevel level) {
            if (currentRadius < RADIUS) {
                currentRadius += EXPAND_SPEED;
            }

            createDome(level);
            createFloor(level);
            fillInteriorWithLight(level);
        }

        private void createDome(ServerLevel level) {
            int r = RADIUS;
            double revealRadius = Math.min(currentRadius, RADIUS);

            for (int x = -r; x <= r; x++) {
                for (int y = 0; y <= HEIGHT; y++) {
                    for (int z = -r; z <= r; z++) {

                        double horizontalDistance = Math.sqrt(x * x + z * z);

                        // Expansão horizontal do domo
                        if (horizontalDistance > revealRadius) continue;

                        double sphereDistance = Math.sqrt(x * x + y * y + z * z);

                        // Casca final do domo
                        if (sphereDistance < RADIUS - DOME_THICKNESS) continue;
                        if (sphereDistance > RADIUS + DOME_THICKNESS) continue;

                        BlockPos pos = center.offset(x, y, z);

                        BlockState domeBlock = getPrettyDomeBlock(x, y, z);

                        setTemporaryBlock(level, pos, domeBlock);
                    }
                }
            }
        }

        private BlockState getPrettyDomeBlock(int x, int y, int z) {
            int pattern = Math.abs((x * 31) + (y * 17) + (z * 13)) % 100;

            // Rachaduras roxas espalhadas
            if (pattern < 9) {
                return Blocks.CRYING_OBSIDIAN.defaultBlockState();
            }

            // Manchas escuras pra não ficar liso demais
            if (pattern < 20) {
                return Blocks.BLACKSTONE.defaultBlockState();
            }

            // Base mais rochosa
            if (y <= 2 && pattern < 42) {
                return Blocks.POLISHED_BLACKSTONE.defaultBlockState();
            }

            // Topo com mais energia/rachadura
            if (y >= HEIGHT - 3 && pattern < 28) {
                return Blocks.CRYING_OBSIDIAN.defaultBlockState();
            }

            // Linhas verticais sutis no domo
            if ((Math.abs(x) % 7 == 0 || Math.abs(z) % 7 == 0) && pattern < 35) {
                return Blocks.BLACKSTONE.defaultBlockState();
            }

            return Blocks.BLACK_CONCRETE.defaultBlockState();
        }

        private void createFloor(ServerLevel level) {
            int r = RADIUS;
            double revealRadius = Math.min(currentRadius, RADIUS);

            int floorY = -1;

            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {

                    double distance = Math.sqrt(x * x + z * z);

                    // Interpolação do chão
                    if (distance > revealRadius) continue;

                    BlockPos pos = center.offset(x, floorY, z);

                    BlockState floorBlock = getPrettyFloorBlock(x, z);

                    setTemporaryBlock(level, pos, floorBlock);
                }
            }
        }

        private BlockState getPrettyFloorBlock(int x, int z) {
            int pattern = Math.abs((x * 19) + (z * 23)) % 100;

            if (pattern < 8) {
                return Blocks.CRYING_OBSIDIAN.defaultBlockState();
            }

            if (pattern < 35) {
                return Blocks.POLISHED_BLACKSTONE.defaultBlockState();
            }

            if (pattern < 50) {
                return Blocks.BLACKSTONE.defaultBlockState();
            }

            return Blocks.BLACK_CONCRETE.defaultBlockState();
        }

        private void fillInteriorWithLight(ServerLevel level) {
            // Interior do domínio = bloco de luz invisível nível 15
            BlockState lightBlock = Blocks.LIGHT.defaultBlockState()
                    .setValue(BlockStateProperties.LEVEL, 15);

            int r = RADIUS;
            double revealRadius = Math.min(currentRadius, RADIUS);

            for (int x = -r + 1; x <= r - 1; x++) {
                for (int y = 0; y <= HEIGHT - 1; y++) {
                    for (int z = -r + 1; z <= r - 1; z++) {

                        double horizontalDistance = Math.sqrt(x * x + z * z);

                        // Interior acompanha a expansão
                        if (horizontalDistance > revealRadius) continue;

                        double sphereDistance = Math.sqrt(x * x + y * y + z * z);

                        // Não encosta na casca grossa do domo
                        if (sphereDistance >= RADIUS - DOME_THICKNESS) continue;

                        BlockPos pos = center.offset(x, y, z);

                        // Não substitui blocos sólidos do domo/chão
                        BlockState currentState = level.getBlockState(pos);

                        if (currentState.is(Blocks.BLACK_CONCRETE)) continue;
                        if (currentState.is(Blocks.CRYING_OBSIDIAN)) continue;
                        if (currentState.is(Blocks.BLACKSTONE)) continue;
                        if (currentState.is(Blocks.POLISHED_BLACKSTONE)) continue;

                        setTemporaryBlock(level, pos, lightBlock);
                    }
                }
            }
        }

        private void setTemporaryBlock(ServerLevel level, BlockPos pos, BlockState newState) {
            BlockPos immutablePos = pos.immutable();

            // Salva o bloco original só uma vez
            if (!changedBlocks.contains(immutablePos)) {
                BlockState oldState = level.getBlockState(immutablePos);

                originalBlocks.put(immutablePos, oldState);
                changedBlocks.add(immutablePos);
            }

            level.setBlock(immutablePos, newState, 3);
        }

        private void startRestore() {
            restoring = true;
            restoreRadius = RADIUS + 3;
            restoredBlocks.clear();
        }

        private void tickRestore(ServerLevel level) {
            if (!restoring) return;

            restoreRadius -= RESTORE_SPEED;

            for (Map.Entry<BlockPos, BlockState> entry : originalBlocks.entrySet()) {
                BlockPos pos = entry.getKey();

                if (restoredBlocks.contains(pos)) continue;

                double distance = getRestoreDistance(pos);

                // Restaura de fora para dentro
                if (distance >= restoreRadius || restoreRadius <= 0) {
                    level.setBlock(pos, entry.getValue(), 3);
                    restoredBlocks.add(pos);
                }
            }
        }

        private double getRestoreDistance(BlockPos pos) {
            int x = pos.getX() - center.getX();
            int y = pos.getY() - center.getY();
            int z = pos.getZ() - center.getZ();

            // Chão restaura como círculo horizontal
            if (y < 0) {
                return Math.sqrt(x * x + z * z);
            }

            // Domo/interior restaura como esfera
            return Math.sqrt(x * x + y * y + z * z);
        }

        private boolean isRestoreFinished() {
            return restoredBlocks.size() >= originalBlocks.size();
        }

        private void restoreAll(ServerLevel level) {
            for (Map.Entry<BlockPos, BlockState> entry : originalBlocks.entrySet()) {
                level.setBlock(entry.getKey(), entry.getValue(), 3);
            }

            originalBlocks.clear();
            changedBlocks.clear();
            restoredBlocks.clear();
            restoring = false;
        }
    }
}