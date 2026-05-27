package net.hekopdcre.cursedenergy.summon;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.*;

public final class SummonRegistry {

    private static final Map<UUID, List<SummonEntry>> REGISTRY = new HashMap<>();
    private static final Map<UUID, Integer> ACTIVATION_TICKS = new HashMap<>();

    private SummonRegistry() {
    }

    // -----------------------------------------------------------------------
    // Activation ticks (delay antes do spawn)
    // -----------------------------------------------------------------------

    public static void putActivationTick(UUID playerUUID, int tick) {
        ACTIVATION_TICKS.put(playerUUID, tick);
    }

    public static @org.jetbrains.annotations.Nullable Integer getActivationTick(UUID playerUUID) {
        return ACTIVATION_TICKS.get(playerUUID);
    }

    public static void removeActivationTick(UUID playerUUID) {
        ACTIVATION_TICKS.remove(playerUUID);
    }

    public static boolean hasActivationTick(UUID playerUUID) {
        return ACTIVATION_TICKS.containsKey(playerUUID);
    }

    // -----------------------------------------------------------------------
    // Summons
    // -----------------------------------------------------------------------

    public static void register(UUID playerUUID, UUID entityUUID, Identifier abilityId) {
        REGISTRY.computeIfAbsent(playerUUID, k -> new ArrayList<>())
                .add(new SummonEntry(entityUUID, abilityId));
    }

    public static void unregister(UUID playerUUID, UUID entityUUID) {
        List<SummonEntry> entries = REGISTRY.get(playerUUID);
        if (entries == null)
            return;
        entries.removeIf(e -> e.entityUUID().equals(entityUUID));
        if (entries.isEmpty())
            REGISTRY.remove(playerUUID);
    }

    public static boolean hasAnySummon(UUID playerUUID) {
        return REGISTRY.containsKey(playerUUID);
    }

    public static boolean hasActiveSummonFor(UUID playerUUID, Identifier abilityId, ServerLevel level) {
        List<SummonEntry> entries = REGISTRY.get(playerUUID);
        if (entries == null)
            return false;
        return entries.stream()
                .filter(e -> e.abilityId().equals(abilityId))
                .anyMatch(e -> {
                    Entity entity = level.getEntity(e.entityUUID());
                    return entity != null && entity.isAlive();
                });
    }

    public static List<SummonEntry> getEntries(UUID playerUUID) {
        return Collections.unmodifiableList(
                REGISTRY.getOrDefault(playerUUID, Collections.emptyList()));
    }

    // -----------------------------------------------------------------------
    // Cleanup
    // -----------------------------------------------------------------------

    /** Remove e despawna TODOS os summons do player. */
    public static void cleanupPlayer(UUID playerUUID, ServerLevel level) {
        ACTIVATION_TICKS.remove(playerUUID);
        List<SummonEntry> entries = REGISTRY.remove(playerUUID);
        if (entries == null)
            return;
        for (SummonEntry entry : entries) {
            discardEntity(level, entry.entityUUID());
        }
    }

    /** Remove e despawna apenas summons de uma habilidade específica do player. */
    public static void cleanupPlayer(UUID playerUUID, Identifier abilityId, ServerLevel level) {
        ACTIVATION_TICKS.remove(playerUUID);
        List<SummonEntry> entries = REGISTRY.get(playerUUID);
        if (entries == null)
            return;
        entries.removeIf(entry -> {
            if (!entry.abilityId().equals(abilityId))
                return false;
            discardEntity(level, entry.entityUUID());
            return true;
        });
        if (entries.isEmpty())
            REGISTRY.remove(playerUUID);
    }

    private static void discardEntity(ServerLevel level, UUID entityUUID) {
        Entity e = level.getEntity(entityUUID);
        if (e != null && e.isAlive()) {
            e.remove(Entity.RemovalReason.DISCARDED);
        }
    }
}