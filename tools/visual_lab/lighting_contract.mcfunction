# DESTRUCTIVE FIXTURE: use only in a disposable Aurum Lighting Audit save.
# Never install/run this in the natural scenery or user save.
# Closed neutral receiver room; nearby snapshot candidates outside it may still be counted.
fill -1257 -44 4113 -1243 -36 4127 minecraft:stone
fill -1256 -43 4114 -1244 -37 4126 minecraft:air
setblock -1251 -43 4117 minecraft:furnace[facing=south,lit=true]
setblock -1250 -43 4117 minecraft:stone
setblock -1250 -42 4117 minecraft:zombie_head[rotation=8]
setblock -1249 -43 4117 minecraft:chest[facing=south]
setblock -1254 -43 4118 minecraft:torch
gamemode spectator @s
tp @s -1250.5 -41 4124.5 180 8
time set 1350000
weather clear
