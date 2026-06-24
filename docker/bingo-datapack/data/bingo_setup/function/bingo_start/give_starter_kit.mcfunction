execute as @a[gamemode=adventure] run function bingo_setup:elytra/give_elytra
schedule function bingo_setup:elytra/check_slot 1t replace
execute as @a[gamemode=adventure] run function bingo_setup:bingo_start/give_shovel