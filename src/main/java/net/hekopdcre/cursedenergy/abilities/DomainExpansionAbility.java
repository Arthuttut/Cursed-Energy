package net.hekopdcre.cursedenergy.abilities;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityProperties;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.power.ability.AbilityStateManager;
import net.threetag.palladium.power.energybar.EnergyBarUsage;

import java.util.*;

public class DomainExpansionAbility extends Ability {

    // =========================================================================
    // BlockConfig
    // =========================================================================

    public record BlockConfig(
            String domeMain,
            String domeAccent1,
            String domeAccent2,
            String domeBaseAccent,
            String floorMain,
            String floorAccent1,
            String floorAccent2) {

        public static final MapCodec<BlockConfig> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("dome_main_block").orElse("minecraft:black_concrete")
                        .forGetter(BlockConfig::domeMain),
                Codec.STRING.fieldOf("dome_accent_block_1").orElse("minecraft:crying_obsidian")
                        .forGetter(BlockConfig::domeAccent1),
                Codec.STRING.fieldOf("dome_accent_block_2").orElse("minecraft:blackstone")
                        .forGetter(BlockConfig::domeAccent2),
                Codec.STRING.fieldOf("dome_base_accent_block").orElse("minecraft:polished_blackstone")
                        .forGetter(BlockConfig::domeBaseAccent),
                Codec.STRING.fieldOf("floor_main_block").orElse("minecraft:black_concrete")
                        .forGetter(BlockConfig::floorMain),
                Codec.STRING.fieldOf("floor_accent_block_1").orElse("minecraft:crying_obsidian")
                        .forGetter(BlockConfig::floorAccent1),
                Codec.STRING.fieldOf("floor_accent_block_2").orElse("minecraft:polished_blackstone")
                        .forGetter(BlockConfig::floorAccent2))
                .apply(i, BlockConfig::new));

        public static final BlockConfig DEFAULT = new BlockConfig(
                "minecraft:black_concrete",
                "minecraft:crying_obsidian",
                "minecraft:blackstone",
                "minecraft:polished_blackstone",
                "minecraft:black_concrete",
                "minecraft:crying_obsidian",
                "minecraft:polished_blackstone");
    }

    // =========================================================================
    // CODEC
    // =========================================================================

    public static final MapCodec<DomainExpansionAbility> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(),
            stateCodec(),
            energyBarUsagesCodec(),
            Codec.INT.fieldOf("radius").orElse(15).forGetter(a -> a.radius),
            Codec.INT.fieldOf("height").orElse(15).forGetter(a -> a.height),
            Codec.DOUBLE.fieldOf("expand_speed").orElse(0.75).forGetter(a -> a.expandSpeed),
            Codec.DOUBLE.fieldOf("restore_speed").orElse(0.85).forGetter(a -> a.restoreSpeed),
            Codec.DOUBLE.fieldOf("dome_thickness").orElse(1.65).forGetter(a -> a.domeThickness),
            Codec.INT.fieldOf("restore_batch_size").orElse(200).forGetter(a -> a.restoreBatchSize),
            Codec.BOOL.fieldOf("spawn_particles_on_water").orElse(true).forGetter(a -> a.spawnParticlesOnWater),
            BlockConfig.CODEC.forGetter(a -> a.blockConfig)).apply(instance, DomainExpansionAbility::new));

    // =========================================================================
    // Fields
    // =========================================================================

    public final int radius;
    public final int height;
    public final double expandSpeed;
    public final double restoreSpeed;
    public final double domeThickness;
    public final int restoreBatchSize;
    public final boolean spawnParticlesOnWater;
    public final BlockConfig blockConfig;

    public String domeMainBlock()       { return blockConfig.domeMain(); }
    public String domeAccentBlock1()    { return blockConfig.domeAccent1(); }
    public String domeAccentBlock2()    { return blockConfig.domeAccent2(); }
    public String domeBaseAccentBlock() { return blockConfig.domeBaseAccent(); }
    public String floorMainBlock()      { return blockConfig.floorMain(); }
    public String floorAccentBlock1()   { return blockConfig.floorAccent1(); }
    public String floorAccentBlock2()   { return blockConfig.floorAccent2(); }

    // =========================================================================
    // Domain maps — separados por dimensão
    // =========================================================================

    private static final Map<ResourceKey<Level>, Map<UUID, ActiveDomain>> ACTIVE_DOMAINS    = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<UUID, ActiveDomain>> RESTORING_DOMAINS = new HashMap<>();

    private static Map<UUID, ActiveDomain> activeMap(ResourceKey<Level> dim) {
        return ACTIVE_DOMAINS.computeIfAbsent(dim, d -> new HashMap<>());
    }

    private static Map<UUID, ActiveDomain> restoringMap(ResourceKey<Level> dim) {
        return RESTORING_DOMAINS.computeIfAbsent(dim, d -> new HashMap<>());
    }

    // =========================================================================
    // BlockLedgerSystem
    // =========================================================================

    /**
     * Fonte da verdade global para todos os blocos modificados por qualquer domain.
     *
     * Estrutura:
     *   dim -> (packedPos -> BlockLedger)          — estado original + stack de owners
     *   dim -> (uuid     -> LongSet de packedPos)  — índice inverso para restore rápido
     *
     * Invariante: um bloco só tem `originalState` registrado uma vez, mesmo que N
     * domains o sobrescrevam. O restore do último owner devolve o estado original.
     */
    static final class BlockLedgerSystem {

        // Estado original + stack de quem modificou, por dimensão e posição
        private static final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<BlockLedger>>
                LEDGERS = new HashMap<>();

        // Índice inverso: owner -> set de packed positions que ele owns, por dimensão
        // Permite encontrar todos os blocos de um domain em O(1) sem varrer o ledger.
        private static final Map<ResourceKey<Level>, Map<UUID, LongSet>>
                OWNER_INDEX = new HashMap<>();

        // -----------------------------------------------------------------------
        // Tipos internos
        // -----------------------------------------------------------------------

        static final class BlockLedger {
            final BlockState originalState;
            // ArrayDeque: topo = peek/pollLast, base = first — insert no topo (addLast)
            final Deque<OwnershipEntry> ownerStack = new ArrayDeque<>();

            BlockLedger(BlockState original) {
                this.originalState = original;
            }
        }

        record OwnershipEntry(UUID owner, BlockState placedState) {}

        // -----------------------------------------------------------------------
        // Helpers de acesso
        // -----------------------------------------------------------------------

        private static Long2ObjectOpenHashMap<BlockLedger> ledgerMap(ResourceKey<Level> dim) {
            return LEDGERS.computeIfAbsent(dim, d -> new Long2ObjectOpenHashMap<>(4096));
        }

        private static LongSet ownerSet(ResourceKey<Level> dim, UUID owner) {
            return OWNER_INDEX
                    .computeIfAbsent(dim, d -> new HashMap<>())
                    .computeIfAbsent(owner, u -> new LongOpenHashSet());
        }

        // -----------------------------------------------------------------------
        // API pública
        // -----------------------------------------------------------------------

        /**
         * Registra a intenção de um domain de colocar `newState` em `pos`.
         *
         * - Se for o primeiro owner nesta posição: salva o estado original do mundo.
         * - Empilha a entry do owner.
         * - Aplica o bloco no mundo.
         * - Blocos protegidos (bedrock, barrier, etc.) são ignorados silenciosamente.
         * - Block entities são ignorados (não sabemos serializar/restaurar NBT aqui).
         *
         * @return true se o bloco foi efetivamente colocado.
         */
        static boolean place(ResourceKey<Level> dim, BlockPos pos, UUID owner,
                             BlockState newState, ServerLevel level, int flags) {
            if (!level.isLoaded(pos)) return false;

            BlockState current = level.getBlockState(pos);

            // Blocos que nunca devem ser tocados
            if (current.is(Blocks.BEDROCK) || current.is(Blocks.BARRIER)
                    || current.is(Blocks.COMMAND_BLOCK)
                    || current.is(Blocks.NETHER_PORTAL) || current.is(Blocks.END_PORTAL))
                return false;

            long packed = pos.asLong();
            Long2ObjectOpenHashMap<BlockLedger> lmap = ledgerMap(dim);

            BlockLedger ledger = lmap.get(packed);
            if (ledger == null) {
                // Primeira modificação nesta posição: bloco com BlockEntity não entra
                if (level.getBlockEntity(pos) != null) return false;
                ledger = new BlockLedger(current);
                lmap.put(packed, ledger);
            }

            // Garante no máximo 1 entry por owner nesta posição.
            // Remove a entry anterior do owner (se existir) antes de empilhar a nova —
            // evita que a stack cresça indefinidamente em ticks repetidos.
            ledger.ownerStack.removeIf(e -> e.owner().equals(owner));
            ledger.ownerStack.addLast(new OwnershipEntry(owner, newState));
            ownerSet(dim, owner).add(packed);

            if (newState.equals(current)) return false; // estado igual, não precisa setar
            level.setBlock(pos, newState, flags);
            return true;
        }

        /**
         * Remove a participação de `owner` em `packed` e decide o que restaurar.
         *
         * - Remove TODAS as entries desse owner na stack deste bloco.
         * - Se ainda houver outro owner: aplica o estado do novo topo.
         * - Se a stack ficar vazia: restaura o originalState e remove o ledger.
         */
        static void release(ResourceKey<Level> dim, long packed, UUID owner, ServerLevel level) {
            Long2ObjectOpenHashMap<BlockLedger> lmap = LEDGERS.get(dim);
            if (lmap == null) return;

            BlockLedger ledger = lmap.get(packed);
            if (ledger == null) return;

            // Remove todas as entries do owner na stack deste bloco
            ledger.ownerStack.removeIf(e -> e.owner().equals(owner));

            // Remove packed do índice secundário deste owner — evita memory leak
            Map<UUID, LongSet> dimIndex = OWNER_INDEX.get(dim);
            if (dimIndex != null) {
                LongSet ownerPositions = dimIndex.get(owner);
                if (ownerPositions != null) {
                    ownerPositions.remove(packed);
                    if (ownerPositions.isEmpty()) dimIndex.remove(owner);
                }
                if (dimIndex.isEmpty()) OWNER_INDEX.remove(dim);
            }

            BlockPos pos = BlockPos.of(packed);
            if (!level.isLoaded(pos)) {
                // Chunk não carregado: limpa ledger mesmo assim para não vazar memória.
                // O bloco não será restaurado — trade-off aceitável.
                if (ledger.ownerStack.isEmpty()) lmap.remove(packed);
                return;
            }

            if (ledger.ownerStack.isEmpty()) {
                // Ninguém mais neste bloco — volta ao estado original
                level.setBlock(pos, ledger.originalState, 3);
                lmap.remove(packed);
                if (lmap.isEmpty()) LEDGERS.remove(dim);
            } else {
                // Ainda tem outro domain ativo — aplica o topo da stack
                BlockState topState = ledger.ownerStack.peekLast().placedState();
                if (!level.getBlockState(pos).equals(topState))
                    level.setBlock(pos, topState, 3);
            }
        }

        /**
         * Retorna o set de packed positions que pertencem a este owner nesta dimensão.
         * O ActiveDomain usa isso para montar a restoreQueue.
         * O set retornado é uma CÓPIA — o caller pode iterar sem se preocupar com
         * modificações concorrentes durante o restore.
         */
        static LongSet getOwnedPositions(ResourceKey<Level> dim, UUID owner) {
            Map<UUID, LongSet> dimIndex = OWNER_INDEX.get(dim);
            if (dimIndex == null) return new LongOpenHashSet();
            LongSet set = dimIndex.get(owner);
            return set != null ? new LongOpenHashSet(set) : new LongOpenHashSet();
        }

        /**
         * Remove o owner do índice secundário de uma dimensão.
         * Deve ser chamado após o restore completo de um domain.
         */
        static void clearOwnerIndex(ResourceKey<Level> dim, UUID owner) {
            Map<UUID, LongSet> dimIndex = OWNER_INDEX.get(dim);
            if (dimIndex == null) return;
            dimIndex.remove(owner);
            if (dimIndex.isEmpty()) OWNER_INDEX.remove(dim);
        }

        /**
         * Força restore imediato de todos os blocos de um owner numa dimensão.
         * Usado em morte, disconnect, shutdown, etc.
         */
        static void forceReleaseAll(ResourceKey<Level> dim, UUID owner, ServerLevel level) {
            LongSet positions = getOwnedPositions(dim, owner);
            for (long packed : positions) {
                release(dim, packed, owner, level);
            }
            clearOwnerIndex(dim, owner);
        }
    }

    // =========================================================================
    // Constructor
    // =========================================================================

    public DomainExpansionAbility(
            AbilityProperties properties,
            AbilityStateManager stateManager,
            List<EnergyBarUsage> energyBarUsages,
            Integer radius,
            Integer height,
            Double expandSpeed,
            Double restoreSpeed,
            Double domeThickness,
            Integer restoreBatchSize,
            Boolean spawnParticlesOnWater,
            BlockConfig blockConfig) {
        super(properties, stateManager, energyBarUsages);
        this.radius = radius;
        this.height = height;
        this.expandSpeed = expandSpeed;
        this.restoreSpeed = restoreSpeed;
        this.domeThickness = domeThickness;
        this.restoreBatchSize = restoreBatchSize;
        this.spawnParticlesOnWater = spawnParticlesOnWater;
        this.blockConfig = blockConfig;
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return CursedEnergyAbilities.DOMAIN_EXPANSION.get();
    }

    // =========================================================================
    // Ability lifecycle
    // =========================================================================

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> instance, boolean enabled) {
        if (!(entity instanceof ServerPlayer player)) return false;
        if (!(player.level() instanceof ServerLevel level)) return false;

        UUID uuid = player.getUUID();
        ResourceKey<Level> dim = level.dimension();

        if (enabled) {
            // Se estava em restore, cancela e força restore imediato antes de reexpandir
            ActiveDomain restoring = restoringMap(dim).remove(uuid);
            if (restoring != null) restoring.forceRestoreAll(dim, level);

            Map<UUID, ActiveDomain> active = activeMap(dim);
            ActiveDomain domain = active.get(uuid);
            if (domain == null) {
                domain = new ActiveDomain(player.blockPosition(), this, uuid, dim);
                active.put(uuid, domain);
                if (spawnParticlesOnWater && player.isInWater())
                    spawnActivationParticles(level, player);
            }
            domain.tickExpand(level);

        } else {
            // Migra para restore na primeira vez que enabled=false
            ActiveDomain active = activeMap(dim).remove(uuid);
            if (active != null) {
                active.startRestore(dim);
                restoringMap(dim).put(uuid, active);
            }

            // Avança restore a cada tick
            ActiveDomain restoring = restoringMap(dim).get(uuid);
            if (restoring != null) {
                restoring.tickRestore(dim, level);
                if (restoring.isRestoreFinished()) {
                    restoringMap(dim).remove(uuid);
                    BlockLedgerSystem.clearOwnerIndex(dim, uuid);
                }
            }
        }
        return false;
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance<?> instance) {
        if (!(entity instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        UUID uuid = player.getUUID();
        ResourceKey<Level> dim = level.dimension();

        ActiveDomain active = activeMap(dim).remove(uuid);
        if (active != null) {
            active.startRestore(dim);
            restoringMap(dim).put(uuid, active);
        }
        // globalTick() cuida do restore gradual.
    }

    // =========================================================================
    // Static utilities
    // =========================================================================

    public static void globalTick(ServerLevel level) {
        ResourceKey<Level> dim = level.dimension();
        Map<UUID, ActiveDomain> map = restoringMap(dim);

        Iterator<Map.Entry<UUID, ActiveDomain>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ActiveDomain> entry = it.next();
            UUID uuid = entry.getKey();
            entry.getValue().tickRestore(dim, level);
            if (entry.getValue().isRestoreFinished()) {
                it.remove();
                BlockLedgerSystem.clearOwnerIndex(dim, uuid);
            }
        }

        if (map.isEmpty()) RESTORING_DOMAINS.remove(dim);
    }

    /**
     * Força restore imediato em todas as dimensões para o UUID.
     * Use em morte, disconnect, etc.
     */
    public static void forceRestoreAll(UUID uuid, net.minecraft.server.MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            ResourceKey<Level> dim = level.dimension();

            ActiveDomain active = activeMap(dim).remove(uuid);
            if (active != null) active.forceRestoreAll(dim, level);

            ActiveDomain restoring = restoringMap(dim).remove(uuid);
            if (restoring != null) restoring.forceRestoreAll(dim, level);
        }
    }

    private static void spawnActivationParticles(ServerLevel level, ServerPlayer player) {
        double x = player.getX(), y = player.getY() + player.getBbHeight() * 0.5, z = player.getZ();
        level.sendParticles(ParticleTypes.SQUID_INK,        x, y, z, 40, 1.5, 1.5, 1.5, 0.1);
        level.sendParticles(ParticleTypes.LARGE_SMOKE,      x, y, z, 20, 2.0, 1.0, 2.0, 0.05);
        level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, x, y, z, 30, 1.0, 0.5, 1.0, 0.2);
    }

    // =========================================================================
    // ActiveDomain
    // =========================================================================

    static class ActiveDomain {

        record Offset(int x, int y, int z) {}

        private final BlockPos center;
        private final DomainExpansionAbility cfg;
        private final UUID owner;
        private final ResourceKey<Level> dim;

        // BlockStates e Blocks para seleção visual e filtragem de clearInterior
        private final BlockState bsMain, bsAccent1, bsAccent2, bsBaseAccent;
        private final BlockState bsFloorMain, bsFloorAccent1, bsFloorAccent2;
        private final Block blMain, blAccent1, blAccent2, blBaseAccent;
        private final Block blFloorMain, blFloorAccent1, blFloorAccent2;

        // Offsets pré-calculados uma única vez no construtor
        private final List<Offset> domeOffsets;
        private final List<Offset> floorOffsets;
        private final List<Offset> clearOffsets;

        // Estado da expansão
        private double currentRadius = 0;
        private int lastTickRadius = -1;

        // Estado do restore gradual
        // restoreQueue armazena [packedPos, distSq bits] — ordenado de mais distante
        // para mais próximo para o efeito visual de colapso de fora para dentro.
        private double restoreRadius;
        private boolean restoring = false;
        private List<long[]> restoreQueue = new ArrayList<>();
        private int restoreIndex = 0;

        ActiveDomain(BlockPos center, DomainExpansionAbility cfg, UUID owner, ResourceKey<Level> dim) {
            this.center = center.immutable();
            this.cfg    = cfg;
            this.owner  = owner;
            this.dim    = dim;
            this.restoreRadius = cfg.radius + 3;

            BlockConfig bc = cfg.blockConfig;

            bsMain         = resolveState(bc.domeMain(),       Blocks.BLACK_CONCRETE);
            bsAccent1      = resolveState(bc.domeAccent1(),    Blocks.CRYING_OBSIDIAN);
            bsAccent2      = resolveState(bc.domeAccent2(),    Blocks.BLACKSTONE);
            bsBaseAccent   = resolveState(bc.domeBaseAccent(), Blocks.POLISHED_BLACKSTONE);
            bsFloorMain    = resolveState(bc.floorMain(),      Blocks.BLACK_CONCRETE);
            bsFloorAccent1 = resolveState(bc.floorAccent1(),   Blocks.CRYING_OBSIDIAN);
            bsFloorAccent2 = resolveState(bc.floorAccent2(),   Blocks.POLISHED_BLACKSTONE);

            blMain         = bsMain.getBlock();
            blAccent1      = bsAccent1.getBlock();
            blAccent2      = bsAccent2.getBlock();
            blBaseAccent   = bsBaseAccent.getBlock();
            blFloorMain    = bsFloorMain.getBlock();
            blFloorAccent1 = bsFloorAccent1.getBlock();
            blFloorAccent2 = bsFloorAccent2.getBlock();

            int r = cfg.radius;
            double innerSq = (r - cfg.domeThickness) * (r - cfg.domeThickness);
            double outerSq = (r + cfg.domeThickness) * (r + cfg.domeThickness);
            double rSq     = (double) (r * r);

            domeOffsets  = new ArrayList<>();
            floorOffsets = new ArrayList<>();
            clearOffsets = new ArrayList<>();

            for (int x = -r; x <= r; x++) {
                int xx = x * x;
                for (int z = -r; z <= r; z++) {
                    if (xx + z * z <= rSq)
                        floorOffsets.add(new Offset(x, -1, z));
                }
                for (int y = 0; y <= cfg.height; y++) {
                    int yy = y * y;
                    for (int z = -r; z <= r; z++) {
                        int zz = z * z;
                        double dh = xx + zz;
                        double d  = xx + yy + zz;
                        if (dh <= rSq && d >= innerSq && d <= outerSq)
                            domeOffsets.add(new Offset(x, y, z));
                        if (Math.abs(x) < r && Math.abs(z) < r && y < cfg.height
                                && dh <= rSq && d < innerSq)
                            clearOffsets.add(new Offset(x, y, z));
                    }
                }
            }
        }

        private static BlockState resolveState(String id, Block fallback) {
            try {
                Identifier rl = Identifier.tryParse(id);
                return BuiltInRegistries.BLOCK.getOptional(rl)
                        .filter(b -> b != Blocks.AIR)
                        .map(Block::defaultBlockState)
                        .orElse(fallback.defaultBlockState());
            } catch (Exception e) {
                return fallback.defaultBlockState();
            }
        }

        // -----------------------------------------------------------------------
        // Expansão
        // -----------------------------------------------------------------------

        void tickExpand(ServerLevel level) {
            if (currentRadius < cfg.radius) currentRadius += cfg.expandSpeed;
            int ir = (int) currentRadius;
            if (ir == lastTickRadius) return;
            lastTickRadius = ir;

            double revSq = Math.min(currentRadius, cfg.radius);
            revSq *= revSq;

            placeDome(level, revSq);
            placeFloor(level, revSq);
            clearInterior(level, revSq);
        }

        private void placeDome(ServerLevel level, double revSq) {
            BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
            for (Offset o : domeOffsets) {
                if ((double) (o.x() * o.x() + o.z() * o.z()) > revSq) continue;
                mut.set(center.getX() + o.x(), center.getY() + o.y(), center.getZ() + o.z());
                BlockLedgerSystem.place(dim, mut, owner,
                        getDomeBlock(o.x(), o.y(), o.z()), level, 18);
            }
        }

        private BlockState getDomeBlock(int x, int y, int z) {
            int p = Math.abs((x * 31) + (y * 17) + (z * 13)) % 100;
            if (p < 9) return bsAccent1;
            if (p < 20) return bsAccent2;
            if (y <= 2 && p < 42) return bsBaseAccent;
            if (y >= cfg.height - 3 && p < 28) return bsAccent1;
            if ((Math.abs(x) % 7 == 0 || Math.abs(z) % 7 == 0) && p < 35) return bsAccent2;
            return bsMain;
        }

        private void placeFloor(ServerLevel level, double revSq) {
            BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
            for (Offset o : floorOffsets) {
                if ((double) (o.x() * o.x() + o.z() * o.z()) > revSq) continue;
                mut.set(center.getX() + o.x(), center.getY() + o.y(), center.getZ() + o.z());
                BlockLedgerSystem.place(dim, mut, owner,
                        getFloorBlock(o.x(), o.z()), level, 18);
            }
        }

        private BlockState getFloorBlock(int x, int z) {
            int p = Math.abs((x * 19) + (z * 23)) % 100;
            if (p < 8)  return bsFloorAccent1;
            if (p < 35) return bsFloorAccent2;
            if (p < 50) return bsAccent2;
            return bsFloorMain;
        }

        private void clearInterior(ServerLevel level, double revSq) {
            double frontMin = Math.max(0, currentRadius - 1.5);
            frontMin *= frontMin;

            BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
            for (Offset o : clearOffsets) {
                double dh = (double) (o.x() * o.x() + o.z() * o.z());
                if (dh > revSq) continue;
                double d = dh + (double) (o.y() * o.y());
                if (d < frontMin) continue;
                mut.set(center.getX() + o.x(), center.getY() + o.y(), center.getZ() + o.z());
                if (!level.isLoaded(mut)) continue;

                // Só limpa se não for bloco do próprio domain (qualquer owner)
                BlockState cur = level.getBlockState(mut);
                if (cur.isAir()
                        || cur.is(blMain) || cur.is(blAccent1) || cur.is(blAccent2)
                        || cur.is(blBaseAccent) || cur.is(blFloorMain)
                        || cur.is(blFloorAccent1) || cur.is(blFloorAccent2))
                    continue;

                BlockLedgerSystem.place(dim, mut, owner,
                        Blocks.AIR.defaultBlockState(), level,
                        cur.getFluidState().isEmpty() ? 2 : 18);
            }
        }

        // -----------------------------------------------------------------------
        // Restore gradual
        // -----------------------------------------------------------------------

        /**
         * Monta a restoreQueue a partir do índice secundário do ledger.
         * Ordena do mais distante para o mais próximo — efeito visual de colapso.
         */
        void startRestore(ResourceKey<Level> dim) {
            if (restoring) return;
            restoring = true;
            restoreRadius = cfg.radius + 3;

            LongSet owned = BlockLedgerSystem.getOwnedPositions(dim, owner);
            restoreQueue = new ArrayList<>(owned.size());
            for (long packed : owned) {
                double dist = distSq(BlockPos.of(packed));
                restoreQueue.add(new long[]{ packed, Double.doubleToRawLongBits(dist) });
            }
            restoreQueue.sort((a, b) -> Double.compare(
                    Double.longBitsToDouble(b[1]),
                    Double.longBitsToDouble(a[1])));
            restoreIndex = 0;
        }

        void tickRestore(ResourceKey<Level> dim, ServerLevel level) {
            if (!restoring) return;
            restoreRadius -= cfg.restoreSpeed;
            double rSq = restoreRadius * restoreRadius;

            int processed = 0;
            while (restoreIndex < restoreQueue.size() && processed < cfg.restoreBatchSize) {
                long[] entry   = restoreQueue.get(restoreIndex);
                long packed    = entry[0];
                double dist    = Double.longBitsToDouble(entry[1]);

                if (dist >= rSq || restoreRadius <= 0) {
                    BlockLedgerSystem.release(dim, packed, owner, level);
                    restoreIndex++;
                    processed++;
                } else {
                    break; // ainda dentro do raio — espera próximo tick
                }
            }
        }

        boolean isRestoreFinished() {
            return restoring && restoreIndex >= restoreQueue.size();
        }

        /** Restore imediato de todos os blocos deste domain — sem animação. */
        void forceRestoreAll(ResourceKey<Level> dim, ServerLevel level) {
            BlockLedgerSystem.forceReleaseAll(dim, owner, level);
            restoreQueue.clear();
            restoreIndex = 0;
            restoring = false;
        }

        // -----------------------------------------------------------------------
        // Helper
        // -----------------------------------------------------------------------

        private double distSq(BlockPos pos) {
            int x = pos.getX() - center.getX();
            int y = pos.getY() - center.getY();
            int z = pos.getZ() - center.getZ();
            return (y < 0) ? (double) (x * x + z * z) : (double) (x * x + y * y + z * z);
        }
    }
}