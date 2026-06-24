scoreboard objectives add after_first_start dummy
scoreboard objectives add bingo_running dummy
scoreboard objectives add lobby_placed dummy
scoreboard objectives add bingo_countdown_running dummy
scoreboard objectives add isOpped dummy
execute unless score dummy after_first_start matches 1 run function bingo_setup:world_start/first_start/first_start
execute unless score dummy bingo_running matches 1 run function bingo_setup:world_start/lobby/lobby_reset

# Keine Portale im Nether außerhalb des Quadrats: X1 Z1:-625 -625; X2 Z2: 625 625 (Welt geht bis 2000 2000)
# Mods: Bingo, coordshud deaktivieren; mod coordinates display und boxlib downloaden, coordsdisplay mode line oder ähnlich
#/function blazeandcave:config/msg_settings