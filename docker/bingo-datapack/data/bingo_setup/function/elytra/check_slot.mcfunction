execute as @a[gamemode=!spectator] if items entity @s armor.chest minecraft:elytra[custom_data={bingo_elytra:1b}] unless items entity @s hotbar.4 * run function bingo_setup:elytra/replenish_fireworks
execute as @a[gamemode=!spectator] if items entity @s armor.chest minecraft:elytra[custom_data={bingo_elytra:1b}] if items entity @s hotbar.4 minecraft:firework_rocket[!count=64,custom_data={bingo_elytra:1b}] run function bingo_setup:elytra/replenish_fireworks
schedule function bingo_setup:elytra/check_slot 8s replace

