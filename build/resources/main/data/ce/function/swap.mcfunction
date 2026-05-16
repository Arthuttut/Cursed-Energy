execute unless entity @s[tag=six_eyes] run scoreboard players remove @s cursed_energy 80
execute if entity @s[tag=six_eyes] run scoreboard players remove @s cursed_energy 30
scoreboard objectives add swap_range dummy "Swap Range"
playsound ce:swap master @a[distance=..20] ~ ~ ~ 1 1
tag @s add swap_caster
tag @s remove swap_done
scoreboard players set @s swap_range 0
execute anchored eyes positioned ^ ^ ^0.3 run function ce:ray
tag @s remove swap_caster
tag @s remove swap_done