execute in minecraft:overworld as @a[gamemode=survival] run gamemode adventure @s
#execute as @a[gamemode=adventure] run attribute @s minecraft:scale base set 0.0625
#execute as @a[gamemode=adventure] run attribute @s jump_strength base set 0.2
#execute as @a[gamemode=adventure] run attribute @s movement_speed base set 0.05
#execute as @a[gamemode=adventure] run attribute @s minecraft:gravity base set 0.03
schedule function bingo_setup:world_start/player_check/player_check 5t replace