forceload add -50 -50 50 50
execute positioned over world_surface run summon area_effect_cloud 0 ~ 0 {Tags:["spawnpoint"]}
tp @a[gamemode=adventure] @e[x=0,y=0,z=0,dx=10,dy=350,dz=10,type=area_effect_cloud,limit=1,tag=spawnpoint]
kill @e[x=0,y=0,z=0,dx=10,dy=350,dz=10,type=area_effect_cloud,limit=1,tag=spawnpoint]
schedule function bingo_setup:world_start/util/stop_forceload 5s replace