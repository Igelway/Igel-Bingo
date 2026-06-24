schedule clear bingo_setup:world_start/player_check/player_check
recipe give @a[gamemode=adventure] *
clear @a[gamemode=adventure]
experience set @a[gamemode=adventure] 0
execute in minecraft:overworld run difficulty easy
execute in minecraft:the_nether run difficulty easy
execute in minecraft:the_end run difficulty easy
gamerule spawn_monsters true
gamerule pvp false
function bingo_setup:bingo_start/util/op_scoreboard_list
schedule function bingo_setup:bingo_start/util/adventure_mode 1t
scoreboard players set dummy bingo_countdown_running 1
schedule function bingo_setup:bingo_start/give_starter_kit 30s
schedule function bingo_setup:elytra/kill_fireworks 31s
schedule function bingo_setup:bingo_start/destroy_lobby 30s
schedule function bingo_setup:bingo_start/util/gamemode_start 31s
