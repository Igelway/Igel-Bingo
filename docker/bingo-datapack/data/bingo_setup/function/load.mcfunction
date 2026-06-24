scoreboard objectives add after_first_start dummy
scoreboard objectives add bingo_running dummy
scoreboard objectives add lobby_placed dummy
scoreboard objectives add bingo_countdown_running dummy
execute unless score dummy after_first_start matches 1 run function bingo_setup:world_start/first_start/first_start
execute unless score dummy bingo_running matches 1 run function bingo_setup:world_start/lobby/lobby_reset