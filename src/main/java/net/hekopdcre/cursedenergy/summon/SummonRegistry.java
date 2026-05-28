package net.hekopdcre.cursedenergy.summon;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.*;

public final class SummonRegistry {

    private static final Map<UUID, List<SummonEntry>> REGISTRY = new HashMap<>();

    // CORRIGIDO: chave composta (playerUUID + abilityId) para ticks independentes
    // por ability
    private record TickKey(UUID playerUUID, Identifier abilityId) {
    }

    private static final Map<TickKey, Integer> ACTIVATION_TICKS = new HashMap<>();

    private SummonRegistry() {
    }

    // -----------------------------------------------------------------------
    // Activation ticks — agora separados por (player, ability)
    // -----------------------------------------------------------------------

    public static void putActivationTick(UUID playerUUID, Identifier abilityId, int tick) {
        ACTIVATION_TICKS.put(new TickKey(playerUUID, abilityId), tick);
    }

    public static @org.jetbrains.annotations.Nullable Integer getActivationTick(UUID playerUUID, Identifier abilityId) {
        return ACTIVATION_TICKS.get(new TickKey(playerUUID, abilityId));
    }

    public static void removeActivationTick(UUID playerUUID, Identifier abilityId) {
        ACTIVATION_TICKS.remove(new TickKey(playerUUID, abilityId));
    }

    public static boolean hasActivationTick(UUID playerUUID, Identifier abilityId) {
        return ACTIVATION_TICKS.containsKey(new TickKey(playerUUID, abilityId));
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

    /**
     * Remove e despawna TODOS os summons e ticks do player (logout, morte, etc.).
     */
    public static void cleanupPlayer(UUID playerUUID, ServerLevel level) {
        // Remove todos os ticks deste player
        ACTIVATION_TICKS.keySet().removeIf(k -> k.playerUUID().equals(playerUUID));

        List<SummonEntry> entries = REGISTRY.remove(playerUUID);
        if (entries == null)
            return;
        for (SummonEntry entry : entries) {
            discardEntity(level, entry.entityUUID());
        }
    }

    /** Remove e despawna apenas summons de uma ability específica do player. */
    public static void cleanupPlayer(UUID playerUUID, Identifier abilityId, ServerLevel level) {
        // CORRIGIDO: remove apenas o tick desta (player, ability) — não afeta as outras
        ACTIVATION_TICKS.remove(new TickKey(playerUUID, abilityId));

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