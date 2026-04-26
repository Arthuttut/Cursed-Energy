tag @a[tag=swap_caster,limit=1] add swap_done

summon marker ~ ~ ~ {Tags:["swap_target_pos"]}

execute as @a[tag=swap_caster,limit=1] at @s run summon marker ~ ~ ~ {Tags:["swap_player_pos"]}

tp @a[tag=swap_caster,limit=1] @e[tag=swap_target_pos,limit=1]
tp @s @e[tag=swap_player_pos,limit=1]

kill @e[tag=swap_target_pos,type=marker]
kill @e[tag=swap_player_pos,type=marker]