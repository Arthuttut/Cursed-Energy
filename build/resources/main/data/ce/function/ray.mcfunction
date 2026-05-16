scoreboard players add @a[tag=swap_caster,limit=1] swap_range 1

execute as @e[tag=!swap_caster,type=!item,type=!experience_orb,type=!marker,distance=..1.5,limit=1,sort=nearest] at @s unless entity @a[tag=swap_caster,tag=swap_done,limit=1] run function ce:do_swap

execute if score @a[tag=swap_caster,limit=1] swap_range matches ..400 unless entity @a[tag=swap_caster,tag=swap_done,limit=1] positioned ^ ^ ^0.5 run function ce:ray