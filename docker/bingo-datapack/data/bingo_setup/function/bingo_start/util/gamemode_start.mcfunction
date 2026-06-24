gamemode survival @a[gamemode=adventure]
execute in minecraft:overworld run gamerule advance_time true
execute in minecraft:the_nether run gamerule advance_time true
execute in minecraft:the_end run gamerule advance_time true
execute in minecraft:overworld run gamerule advance_weather true
execute in minecraft:the_nether run gamerule advance_weather true
execute in minecraft:the_end run gamerule advance_weather true
execute in minecraft:overworld run gamerule pvp true
execute in minecraft:the_nether run gamerule pvp true
execute in minecraft:the_end run gamerule pvp true
scoreboard players set dummy bingo_running 1