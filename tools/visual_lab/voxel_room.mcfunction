# Neutral room: warm and blue sources, cube and slab blockers, no sky fill.
gamerule doMobSpawning false
gamerule doMobLoot false
fill 562 200 502 574 210 514 minecraft:white_concrete hollow
setblock 563 201 503 minecraft:torch
setblock 572 201 503 minecraft:soul_torch
setblock 563 201 506 minecraft:stone
setblock 572 201 506 minecraft:stone
setblock 570 201 506 minecraft:stone_slab[type=bottom]
setblock 565 201 507 minecraft:stone_slab[type=top]
weather clear
time set 18000
tp @s 568.5 202.0 509.5 180 24
