difficulty peaceful
time set 0
weather clear
execute in minecraft:overworld run gamerule pvp true
execute in minecraft:the_nether run gamerule pvp true
execute in minecraft:the_end run gamerule pvp true
execute in minecraft:overworld run gamerule advance_time false
execute in minecraft:the_nether run gamerule advance_time false
execute in minecraft:the_end run gamerule advance_time false
execute in minecraft:overworld run gamerule advance_weather false
execute in minecraft:the_nether run gamerule advance_weather false
execute in minecraft:the_end run gamerule advance_weather false
execute as @a[gamemode=survival] run function bingo_setup:world_start/lobby/revoke_advancements
effect clear @a[gamemode=survival]
execute unless score dummy lobby_placed matches 1 run function bingo_setup:world_start/lobby/create_lobby
function bingo_setup:world_start/player_check/player_check
schedule function bingo_setup:world_start/util/tp_to_lobby 2t
scoreboard players set dummy bingo_countdown_running 0