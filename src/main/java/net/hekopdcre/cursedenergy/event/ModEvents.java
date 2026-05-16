package net.hekopdcre.cursedenergy.event;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

public class ModEvents {
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        Scoreboard scoreboard = server.getScoreboard();

        createObjective(scoreboard, "sprint", "Sprint");
        createObjective(scoreboard, "celestial_restriction", "Celestial Restriction");
        createObjective(scoreboard, "cursed_energy", "Cursed Energy");
        createObjective(scoreboard, "sukuna_fingers", "Sukuna Fingers");

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            initializePlayerScores(server, player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            initializePlayerScores(player.level().getServer(), player);
        }
    }

    private static void createObjective(Scoreboard scoreboard, String name, String displayName) {
        if (scoreboard.getObjective(name) == null) {
            scoreboard.addObjective(
                    name,
                    ObjectiveCriteria.DUMMY,
                    Component.literal(displayName),
                    ObjectiveCriteria.RenderType.INTEGER,
                    true,
                    null
            );
        }
    }

    private static void initializePlayerScores(MinecraftServer server, ServerPlayer player) {
        CommandSourceStack source = server.createCommandSourceStack()
                .withSuppressedOutput();

        String playerName = player.getScoreboardName();

        server.getCommands().performPrefixedCommand(
                source,
                "scoreboard players add " + playerName + " sprint 0"
        );

        server.getCommands().performPrefixedCommand(
                source,
                "scoreboard players add " + playerName + " celestial_restriction 0"
        );

        server.getCommands().performPrefixedCommand(
                source,
                "scoreboard players add " + playerName + " cursed_energy 0"
        );

        server.getCommands().performPrefixedCommand(
                source,
                "scoreboard players add " + playerName + " sukuna_fingers 0"
        );
    }
}