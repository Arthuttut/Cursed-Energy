// Runs once per world/server + once per player
ServerEvents.tick(event => {
    const server = event.server;
    const spp = "superpower add ce:";

    // Runs once when the world/server starts
    if (!server.persistentData.ce_scoreboards_created) {
        server.persistentData.ce_scoreboards_created = true;

        server.runCommandSilent('scoreboard objectives add sprint dummy "Sprint"');
        server.runCommandSilent('scoreboard objectives add celestial_restriction dummy "Celestial Restriction"');
        server.runCommandSilent('scoreboard objectives add cursed_energy dummy "Cursed Energy"');
        server.runCommandSilent('scoreboard objectives add sukuna_fingers dummy "Sukuna Fingers"');
    }

    const players = server.players;
    if (players.length <= 0) return;

    players.forEach(player => {
        // Runs once per player to initialize scores
        if (!player.persistentData.ce_scores_initialized) {
            player.persistentData.ce_scores_initialized = true;

            player.runCommandSilent('scoreboard players set @s sprint 0');
            player.runCommandSilent('scoreboard players set @s cursed_energy 0');
            player.runCommandSilent('scoreboard players set @s sukuna_fingers 0');
            player.runCommandSilent('scoreboard players set @s celestial_restriction 0');
        }

        // If this player already got their random power, do nothing
        if (player.persistentData.ce_random_start_power_done) return;

        // Mark this player as completed
        player.persistentData.ce_random_start_power_done = true;

        // 1% Celestial Restriction / 99% Cursed Energy Control
        if (Math.random() < 0.01) {
            player.runCommandSilent(`${spp}celestial_restriction @s`);
            player.runCommandSilent('scoreboard players set @s celestial_restriction 1');
        } else {
            player.runCommandSilent(`${spp}cursed_energy_control @s`);

            // Random extra technique after getting Cursed Energy Control
            const extraPowerChance = Math.random();

            // 80% Boogie Woogie
            if (extraPowerChance < 0.8) {
                player.runCommandSilent(`${spp}boogie_woogie @s`);
            }

            // 2% King of Curses
            else if (extraPowerChance < 0.82) {
                player.runCommandSilent(`${spp}king_of_curses @s`);
            }

            // 18% no extra technique
        }
    });
});