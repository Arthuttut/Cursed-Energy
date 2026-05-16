tag @a[tag=swap_caster,limit=1] add swap_done

summon marker ~ ~ ~ {Tags:["swap_target_pos"]}

execute as @a[tag=swap_caster,limit=1] at @s run summon marker ~ ~ ~ {Tags:["swap_player_pos"]}

# Teleporta o player para a posição do alvo, mas sem copiar a rotação do alvo
execute at @e[tag=swap_target_pos,type=marker,limit=1] run tp @a[tag=swap_caster,limit=1] ~ ~ ~

# Vira o player para o lado oposto da direção original
execute as @a[tag=swap_caster,limit=1] at @s run tp @s ~ ~ ~ ~180 ~

# Teleporta o alvo para a posição antiga do player, mas sem copiar rotação
execute at @e[tag=swap_player_pos,type=marker,limit=1] run tp @s ~ ~ ~

kill @e[tag=swap_target_pos,type=marker]
kill @e[tag=swap_player_pos,type=marker]