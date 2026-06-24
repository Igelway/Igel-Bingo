gamemode survival @a[gamemode=adventure]
function bingo_setup:world_start/lobby/lobby_reset

scoreboard players set dummy bingo_countdown_running 0
schedule clear bingo_setup:bingo_start/countdown_sound/play_bingo_start
schedule clear bingo_setup:bingo_start/give_starter_kit
schedule clear bingo_setup:elytra/kill_fireworks
schedule clear bingo_setup:bingo_start/destroy_lobby
schedule clear bingo_setup:bingo_start/util/gamemode_start
