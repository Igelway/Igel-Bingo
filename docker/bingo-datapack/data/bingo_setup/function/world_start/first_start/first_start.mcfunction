setworldspawn 0 ~ 0
gamerule respawn_radius 4
execute in minecraft:overworld run function bingo_setup:world_start/first_start/set_gamerules
execute in minecraft:overworld run worldborder set 10000
execute in minecraft:the_nether run function bingo_setup:world_start/first_start/set_gamerules
execute in minecraft:the_nether run worldborder set 4000
execute in minecraft:the_end run function bingo_setup:world_start/first_start/set_gamerules
execute in minecraft:the_end run worldborder set 8000
scoreboard players set dummy after_first_start 1