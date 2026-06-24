scoreboard players set dummy lobby_placed 1
scoreboard objectives add world_surface dummy
scoreboard objectives add water_level dummy
scoreboard objectives add random_index dummy
forceload add -50 -50 50 50
execute positioned over world_surface run summon area_effect_cloud 0 ~ 0 {Tags:["world_surface"]}
scoreboard players set dummy water_level 62
execute positioned 0 0 0 store result score dummy world_surface run data get entity @e[x=0,y=0,z=0,dx=10,dy=350,dz=10,type=area_effect_cloud,limit=1,tag=world_surface] Pos[1]
function bingo_setup:world_start/lobby/place_lobby_in_world
kill @e[x=0,y=0,z=0,dx=10,dy=350,dz=10,type=area_effect_cloud,limit=1,tag=world_surface]
scoreboard objectives remove water_level
scoreboard objectives remove world_surface
schedule function bingo_setup:world_start/util/stop_forceload 5s