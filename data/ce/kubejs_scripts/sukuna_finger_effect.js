ItemEvents.foodEaten('kubejs:sukuna_finger', event => {
    const player = event.player
    player.runCommandSilent('execute unless score @s sukuna_fingers matches 0.. run scoreboard players set @s sukuna_fingers 0')
    player.runCommandSilent('execute if score @s sukuna_fingers matches ..19 run scoreboard players add @s sukuna_fingers 1')
})