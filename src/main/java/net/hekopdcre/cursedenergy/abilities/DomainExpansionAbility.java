package net.hekopdcre.cursedenergy.abilities;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
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

    public record BlockConfig(
            String domeMain,
            String domeAccent1,
            String domeAccent2,
            String domeBaseAccent,
            String floorMain,
            String floorAccent1,
            String floorAccent2
    ) {
        public static final MapCodec<BlockConfig> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("dome_main_block").orElse("minecraft:black_concrete").forGetter(BlockConfig::domeMain),
                Codec.STRING.fieldOf("dome_accent_block_1").orElse("minecraft:crying_obsidian").forGetter(BlockConfig::domeAccent1),
                Codec.STRING.fieldOf("dome_accent_block_2").orElse("minecraft:blackstone").forGetter(BlockConfig::domeAccent2),
                Codec.STRING.fieldOf("dome_base_accent_block").orElse("minecraft:polished_blackstone").forGetter(BlockConfig::domeBaseAccent),
                Codec.STRING.fieldOf("floor_main_block").orElse("minecraft:black_concrete").forGetter(BlockConfig::floorMain),
                Codec.STRING.fieldOf("floor_accent_block_1").orElse("minecraft:crying_obsidian").forGetter(BlockConfig::floorAccent1),
                Codec.STRING.fieldOf("floor_accent_block_2").orElse("minecraft:polished_blackstone").forGetter(BlockConfig::floorAccent2)
        ).apply(i, BlockConfig::new));

        public static final BlockConfig DEFAULT = new BlockConfig(
                "minecraft:black_concrete",
                "minecraft:crying_obsidian",
                "minecraft:blackstone",
                "minecraft:polished_blackstone",
                "minecraft:black_concrete",
                "minecraft:crying_obsidian",
                "minecraft:polished_blackstone"
        );
    }

    public static final MapCodec<DomainExpansionAbility> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
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
                    BlockConfig.CODEC.forGetter(a -> a.blockConfig)
            ).apply(instance, DomainExpansionAbility::new)
    );

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

    private static final Map<UUID, ActiveDomain> ACTIVE_DOMAINS    = new HashMap<>();
    private static final Map<UUID, ActiveDomain> RESTORING_DOMAINS = new HashMap<>();

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
            BlockConfig blockConfig
    ) {
        super(properties, stateManager, energyBarUsages);
        this.radius                = radius;
        this.height                = height;
        this.expandSpeed           = expandSpeed;
        this.restoreSpeed          = restoreSpeed;
        this.domeThickness         = domeThickness;
        this.restoreBatchSize      = restoreBatchSize;
        this.spawnParticlesOnWater = spawnParticlesOnWater;
        this.blockConfig           = blockConfig;
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return CursedEnergyAbilities.DOMAIN_EXPANSION.get();
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> instance, boolean enabled) {
        if (!(entity instanceof ServerPlayer player)) return false;
        if (!(player.level() instanceof ServerLevel level)) return false;

        UUID uuid = player.getUUID();

        if (enabled) {
            ActiveDomain restoring = RESTORING_DOMAINS.remove(uuid);
            if (restoring != null) restoring.restoreAll(level);

            ActiveDomain domain = ACTIVE_DOMAINS.get(uuid);
            if (domain == null) {
                domain = new ActiveDomain(player.blockPosition(), this);
                ACTIVE_DOMAINS.put(uuid, domain);
                if (spawnParticlesOnWater && player.isInWater())
                    spawnActivationParticles(level, player);
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
                if (restoring.isRestoreFinished())
                    RESTORING_DOMAINS.remove(uuid);
            }
        }
        return false;
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance<?> instance) {
        if (!(entity instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        UUID uuid = player.getUUID();
        ActiveDomain active = ACTIVE_DOMAINS.remove(uuid);
        if (active != null) active.restoreAll(level);
        ActiveDomain restoring = RESTORING_DOMAINS.remove(uuid);
        if (restoring != null) restoring.restoreAll(level);
    }

    private static void spawnActivationParticles(ServerLevel level, ServerPlayer player) {
        double x = player.getX(), y = player.getY() + player.getBbHeight() * 0.5, z = player.getZ();
        level.sendParticles(ParticleTypes.SQUID_INK,        x, y, z, 40, 1.5, 1.5, 1.5, 0.1);
        level.sendParticles(ParticleTypes.LARGE_SMOKE,      x, y, z, 20, 2.0, 1.0, 2.0, 0.05);
        level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, x, y, z, 30, 1.0, 0.5, 1.0, 0.2);
    }

    static class ActiveDomain {

        private final BlockPos center;
        private final DomainExpansionAbility cfg;
        private final int[] squares;

        private final BlockState bsMain, bsAccent1, bsAccent2, bsBaseAccent;
        private final BlockState bsFloorMain, bsFloorAccent1, bsFloorAccent2;
        private static final BlockState BS_AIR = Blocks.AIR.defaultBlockState();

        private double currentRadius = 0, restoreRadius;
        private boolean restoring = false;
        private int lastTickRadius = -1;

        private final Map<BlockPos, BlockState> originalBlocks = new HashMap<>();
        private List<BlockPos> restoreQueue = new ArrayList<>();
        private int restoreIndex = 0;

        ActiveDomain(BlockPos center, DomainExpansionAbility cfg) {
            this.center        = center.immutable();
            this.cfg           = cfg;
            this.restoreRadius = cfg.radius + 3;

            squares = new int[cfg.radius + 4];
            for (int i = 0; i < squares.length; i++) squares[i] = i * i;

            BlockConfig bc = cfg.blockConfig;
            bsMain         = resolve(bc.domeMain(),       Blocks.BLACK_CONCRETE.defaultBlockState());
            bsAccent1      = resolve(bc.domeAccent1(),    Blocks.CRYING_OBSIDIAN.defaultBlockState());
            bsAccent2      = resolve(bc.domeAccent2(),    Blocks.BLACKSTONE.defaultBlockState());
            bsBaseAccent   = resolve(bc.domeBaseAccent(), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            bsFloorMain    = resolve(bc.floorMain(),      Blocks.BLACK_CONCRETE.defaultBlockState());
            bsFloorAccent1 = resolve(bc.floorAccent1(),   Blocks.CRYING_OBSIDIAN.defaultBlockState());
            bsFloorAccent2 = resolve(bc.floorAccent2(),   Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        }

        // Sem nenhum import de ResourceLocation/Identifier —
        // usa BuiltInRegistries.BLOCK diretamente com a chave String via stream/filter.
        private static BlockState resolve(String id, BlockState fallback) {
            try {
                return BuiltInRegistries.BLOCK.stream()
                        .filter(b -> b != Blocks.AIR &&
                                BuiltInRegistries.BLOCK.getKey(b).toString().equals(id))
                        .findFirst()
                        .map(Block::defaultBlockState)
                        .orElse(fallback);
            } catch (Exception e) {
                return fallback;
            }
        }

        void tickExpand(ServerLevel level) {
            if (currentRadius < cfg.radius) currentRadius += cfg.expandSpeed;
            int ir = (int) currentRadius;
            if (ir == lastTickRadius) return;
            lastTickRadius = ir;
            createDome(level);
            createFloor(level);
            clearInterior(level);
        }

        private void createDome(ServerLevel level) {
            int r = cfg.radius;
            double revSq = Math.min(currentRadius, r); revSq *= revSq;
            double innerSq = (r - cfg.domeThickness) * (r - cfg.domeThickness);
            double outerSq = (r + cfg.domeThickness) * (r + cfg.domeThickness);
            BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
            for (int x = -r; x <= r; x++) { int xx = sq(x);
                for (int y = 0; y <= cfg.height; y++) { int yy = sq(y);
                    for (int z = -r; z <= r; z++) { int zz = sq(z);
                        if (xx + zz > revSq) continue;
                        double d = xx + yy + zz;
                        if (d < innerSq || d > outerSq) continue;
                        mut.set(center.getX()+x, center.getY()+y, center.getZ()+z);
                        setBlock(level, mut, getDomeBlock(x, y, z), 18);
                    }
                }
            }
        }

        private BlockState getDomeBlock(int x, int y, int z) {
            int p = Math.abs((x*31)+(y*17)+(z*13)) % 100;
            if (p < 9)  return bsAccent1;
            if (p < 20) return bsAccent2;
            if (y <= 2              && p < 42) return bsBaseAccent;
            if (y >= cfg.height - 3 && p < 28) return bsAccent1;
            if ((Math.abs(x)%7==0 || Math.abs(z)%7==0) && p < 35) return bsAccent2;
            return bsMain;
        }

        private void createFloor(ServerLevel level) {
            int r = cfg.radius;
            double revSq = Math.min(currentRadius, r); revSq *= revSq;
            BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
            for (int x = -r; x <= r; x++) { int xx = sq(x);
                for (int z = -r; z <= r; z++) {
                    if (xx + sq(z) > revSq) continue;
                    mut.set(center.getX()+x, center.getY()-1, center.getZ()+z);
                    setBlock(level, mut, getFloorBlock(x, z), 18);
                }
            }
        }

        private BlockState getFloorBlock(int x, int z) {
            int p = Math.abs((x*19)+(z*23)) % 100;
            if (p < 8)  return bsFloorAccent1;
            if (p < 35) return bsFloorAccent2;
            if (p < 50) return bsAccent2;
            return bsFloorMain;
        }

        private void clearInterior(ServerLevel level) {
            int r = cfg.radius;
            double revSq    = Math.min(currentRadius, r); revSq *= revSq;
            double innerSq  = (r - cfg.domeThickness) * (r - cfg.domeThickness);
            double frontMin = Math.max(0, currentRadius - 1.5); frontMin *= frontMin;
            BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
            for (int x = -r+1; x <= r-1; x++) { int xx = sq(x);
                for (int y = 0; y <= cfg.height-1; y++) { int yy = sq(y);
                    for (int z = -r+1; z <= r-1; z++) { int zz = sq(z);
                        if (xx+zz > revSq) continue;
                        double d = xx+yy+zz;
                        if (d >= innerSq || d < frontMin) continue;
                        mut.set(center.getX()+x, center.getY()+y, center.getZ()+z);
                        BlockState cur = level.getBlockState(mut);
                        if (cur == BS_AIR || cur == bsMain || cur == bsAccent1 || cur == bsAccent2 ||
                                cur == bsBaseAccent || cur == bsFloorMain || cur == bsFloorAccent1 || cur == bsFloorAccent2) continue;
                        setBlock(level, mut, BS_AIR, cur.getFluidState().isEmpty() ? 2 : 18);
                    }
                }
            }
        }

        void startRestore() {
            restoring = true;
            restoreRadius = cfg.radius + 3;
            restoreQueue = new ArrayList<>(originalBlocks.keySet());
            restoreQueue.sort((a, b) -> Double.compare(distSq(b), distSq(a)));
            restoreIndex = 0;
        }

        void tickRestore(ServerLevel level) {
            if (!restoring) return;
            restoreRadius -= cfg.restoreSpeed;
            double rSq = restoreRadius * restoreRadius;
            int processed = 0;
            while (restoreIndex < restoreQueue.size() && processed < cfg.restoreBatchSize) {
                BlockPos pos = restoreQueue.get(restoreIndex);
                if (distSq(pos) >= rSq || restoreRadius <= 0) {
                    BlockState orig = originalBlocks.get(pos);
                    if (orig != null && level.isLoaded(pos)) level.setBlock(pos, orig, 3);
                    restoreIndex++;
                    processed++;
                } else break;
            }
        }

        boolean isRestoreFinished() { return restoreIndex >= restoreQueue.size(); }

        void restoreAll(ServerLevel level) {
            for (Map.Entry<BlockPos, BlockState> e : originalBlocks.entrySet())
                if (level.isLoaded(e.getKey())) level.setBlock(e.getKey(), e.getValue(), 3);
            originalBlocks.clear();
            restoreQueue.clear();
            restoreIndex = 0;
            restoring = false;
        }

        private void setBlock(ServerLevel level, BlockPos pos, BlockState state, int flags) {
            BlockPos ip = pos.immutable();
            BlockState ex = level.getBlockState(ip);
            if (ex.is(Blocks.BEDROCK) || ex.is(Blocks.BARRIER) || ex.is(Blocks.COMMAND_BLOCK) ||
                    ex.is(Blocks.NETHER_PORTAL) || ex.is(Blocks.END_PORTAL)) return;
            if (!originalBlocks.containsKey(ip)) {
                if (level.getBlockEntity(ip) != null) return;
                originalBlocks.put(ip, ex);
            }
            if (ex == state) return;
            level.setBlock(ip, state, flags);
        }

        private double distSq(BlockPos pos) {
            int x = pos.getX()-center.getX(), y = pos.getY()-center.getY(), z = pos.getZ()-center.getZ();
            return (y < 0) ? x*x+z*z : x*x+y*y+z*z;
        }

        private int sq(int v) { int a = Math.abs(v); return (a < squares.length) ? squares[a] : a*a; }
    }
}