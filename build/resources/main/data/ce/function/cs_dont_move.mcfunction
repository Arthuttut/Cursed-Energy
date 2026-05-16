execute as @s[tag=!six_eyes] if score @s cursed_energy matches 350.. run scoreboard players remove @s cursed_energy 350
execute as @s[tag=six_eyes] if score @s cursed_energy matches 120.. run scoreboard players remove @s cursed_energy 120
tellraw @a [{"text":"<"},{"selector":"@s"},{"text":"> "},{"text":"DON'T MOVE!","color":"aqua"}]
playsound minecraft:entity.warden.sonic_boom master @a[distance=..24] ~ ~ ~ 1.2 1.35
playsound minecraft:entity.evoker.prepare_attack master @a[distance=..24] ~ ~ ~ 0.8 0.75
playsound minecraft:block.amethyst_block.chime master @a[distance=..16] ~ ~ ~ 0.35 0.6

tag @e[tag=ce_speech_tmp_target] remove ce_speech_tmp_target
tag @e[tag=ce_speech_caster] remove ce_speech_caster
tag @s add ce_speech_caster

execute as @s at @s anchored eyes positioned ^ ^ ^1 unless entity @e[tag=ce_speech_tmp_target] run tag @e[distance=..1.75,limit=1,sort=nearest,tag=!ce_speech_caster,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:marker,type=!minecraft:armor_stand] add ce_speech_tmp_target
execute as @s at @s anchored eyes positioned ^ ^ ^2 unless entity @e[tag=ce_speech_tmp_target] run tag @e[distance=..1.75,limit=1,sort=nearest,tag=!ce_speech_caster,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:marker,type=!minecraft:armor_stand] add ce_speech_tmp_target
execute as @s at @s anchored eyes positioned ^ ^ ^3 unless entity @e[tag=ce_speech_tmp_target] run tag @e[distance=..1.75,limit=1,sort=nearest,tag=!ce_speech_caster,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:marker,type=!minecraft:armor_stand] add ce_speech_tmp_target
execute as @s at @s anchored eyes positioned ^ ^ ^4 unless entity @e[tag=ce_speech_tmp_target] run tag @e[distance=..1.75,limit=1,sort=nearest,tag=!ce_speech_caster,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:marker,type=!minecraft:armor_stand] add ce_speech_tmp_target
execute as @s at @s anchored eyes positioned ^ ^ ^5 unless entity @e[tag=ce_speech_tmp_target] run tag @e[distance=..1.75,limit=1,sort=nearest,tag=!ce_speech_caster,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:marker,type=!minecraft:armor_stand] add ce_speech_tmp_target
execute as @s at @s anchored eyes positioned ^ ^ ^6 unless entity @e[tag=ce_speech_tmp_target] run tag @e[distance=..1.75,limit=1,sort=nearest,tag=!ce_speech_caster,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:marker,type=!minecraft:armor_stand] add ce_speech_tmp_target
execute as @s at @s anchored eyes positioned ^ ^ ^7 unless entity @e[tag=ce_speech_tmp_target] run tag @e[distance=..1.75,limit=1,sort=nearest,tag=!ce_speech_caster,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:marker,type=!minecraft:armor_stand] add ce_speech_tmp_target
execute as @s at @s anchored eyes positioned ^ ^ ^8 unless entity @e[tag=ce_speech_tmp_target] run tag @e[distance=..1.75,limit=1,sort=nearest,tag=!ce_speech_caster,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:marker,type=!minecraft:armor_stand] add ce_speech_tmp_target
execute as @s at @s anchored eyes positioned ^ ^ ^9 unless entity @e[tag=ce_speech_tmp_target] run tag @e[distance=..1.75,limit=1,sort=nearest,tag=!ce_speech_caster,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:marker,type=!minecraft:armor_stand] add ce_speech_tmp_target
execute as @s at @s anchored eyes positioned ^ ^ ^10 unless entity @e[tag=ce_speech_tmp_target] run tag @e[distance=..1.75,limit=1,sort=nearest,tag=!ce_speech_caster,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:marker,type=!minecraft:armor_stand] add ce_speech_tmp_target

execute as @e[tag=ce_speech_tmp_target,limit=1,sort=nearest] run palladium superpower add ce:paralised @s

# recoil da fala amaldiçoada
# quanto menor a cursed_energy, mais dano o usuário toma

execute if entity @e[tag=ce_speech_tmp_target,limit=1] if score @s cursed_energy matches 1001..1300 run damage @s 1 minecraft:magic
execute if entity @e[tag=ce_speech_tmp_target,limit=1] if score @s cursed_energy matches 701..1000 run damage @s 2 minecraft:magic
execute if entity @e[tag=ce_speech_tmp_target,limit=1] if score @s cursed_energy matches 401..700 run damage @s 4 minecraft:magic
execute if entity @e[tag=ce_speech_tmp_target,limit=1] if score @s cursed_energy matches 101..400 run damage @s 6 minecraft:magic
execute if entity @e[tag=ce_speech_tmp_target,limit=1] if score @s cursed_energy matches ..100 run damage @s 8 minecraft:magic

execute if entity @e[tag=ce_speech_tmp_target,limit=1] if score @s cursed_energy matches ..400 run effect give @s minecraft:weakness 5 0 true
execute if entity @e[tag=ce_speech_tmp_target,limit=1] if score @s cursed_energy matches ..250 run effect give @s minecraft:slowness 4 1 true
execute if entity @e[tag=ce_speech_tmp_target,limit=1] if score @s cursed_energy matches ..100 run effect give @s minecraft:nausea 6 0 true

tag @e[tag=ce_speech_tmp_target] remove ce_speech_tmp_target
tag @e[tag=ce_speech_caster] remove ce_speech_caster

# small
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^0.8 run particle minecraft:end_rod ^0 ^0.65 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^0.8 run particle minecraft:end_rod ^0.35 ^0.55 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^0.8 run particle minecraft:end_rod ^0.55 ^0.35 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^0.8 run particle minecraft:end_rod ^0.65 ^0 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^0.8 run particle minecraft:end_rod ^0.55 ^-0.35 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^0.8 run particle minecraft:end_rod ^0.35 ^-0.55 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^0.8 run particle minecraft:end_rod ^0 ^-0.65 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^0.8 run particle minecraft:end_rod ^-0.35 ^-0.55 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^0.8 run particle minecraft:end_rod ^-0.55 ^-0.35 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^0.8 run particle minecraft:end_rod ^-0.65 ^0 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^0.8 run particle minecraft:end_rod ^-0.55 ^0.35 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^0.8 run particle minecraft:end_rod ^-0.35 ^0.55 ^0 0 0 0 0 1 force

# medium
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^1.5 run particle minecraft:end_rod ^0 ^0.95 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^1.5 run particle minecraft:end_rod ^0.50 ^0.80 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^1.5 run particle minecraft:end_rod ^0.80 ^0.50 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^1.5 run particle minecraft:end_rod ^0.95 ^0 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^1.5 run particle minecraft:end_rod ^0.80 ^-0.50 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^1.5 run particle minecraft:end_rod ^0.50 ^-0.80 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^1.5 run particle minecraft:end_rod ^0 ^-0.95 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^1.5 run particle minecraft:end_rod ^-0.50 ^-0.80 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^1.5 run particle minecraft:end_rod ^-0.80 ^-0.50 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^1.5 run particle minecraft:end_rod ^-0.95 ^0 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^1.5 run particle minecraft:end_rod ^-0.80 ^0.50 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^1.5 run particle minecraft:end_rod ^-0.50 ^0.80 ^0 0 0 0 0 1 force

# big
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^2.3 run particle minecraft:end_rod ^0 ^1.25 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^2.3 run particle minecraft:end_rod ^0.65 ^1.05 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^2.3 run particle minecraft:end_rod ^1.05 ^0.65 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^2.3 run particle minecraft:end_rod ^1.25 ^0 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^2.3 run particle minecraft:end_rod ^1.05 ^-0.65 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^2.3 run particle minecraft:end_rod ^0.65 ^-1.05 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^2.3 run particle minecraft:end_rod ^0 ^-1.25 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^2.3 run particle minecraft:end_rod ^-0.65 ^-1.05 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^2.3 run particle minecraft:end_rod ^-1.05 ^-0.65 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^2.3 run particle minecraft:end_rod ^-1.25 ^0 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^2.3 run particle minecraft:end_rod ^-1.05 ^0.65 ^0 0 0 0 0 1 force
execute as @s at @s anchored eyes positioned ^ ^-0.15 ^2.3 run particle minecraft:end_rod ^-0.65 ^1.05 ^0 0 0 0 0 1 force