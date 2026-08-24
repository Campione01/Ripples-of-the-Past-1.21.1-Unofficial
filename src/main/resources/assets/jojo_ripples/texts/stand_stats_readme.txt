Ripples of the Past - editable Stand stats
==========================================

The /jojoconfig stand_stats command generates this world data pack.
Each Stand has a file at:
data/<namespace>/jojostandpowers/<stand_id>/stats.json

Editable fields:
- power / powerMax: starting and fully developed attack power.
- speed / speedMax: starting and fully developed attack and movement speed.
- rangeEffective: distance in blocks at which manual-control melee strength is still complete.
- rangeMax: maximum manual-control distance.
- durability / durabilityMax: starting and fully developed stamina efficiency and resistance.
- precision / precisionMax: starting and fully developed accuracy.
- randomWeight: relative chance of receiving this Stand from random acquisition.

After editing and saving a file, run the vanilla /reload command. The server
loads the values and synchronizes them to connected clients. Normal generation
keeps existing files. Use /jojoconfig stand_stats force only when you intend to
restore every default template, or append a Stand ID to force only that file.

Ripples of the Past - 可编辑替身数值
==================================

/jojoconfig stand_stats 会在当前存档中生成这个数据包。
每个替身的数值文件位于：
data/<命名空间>/jojostandpowers/<替身ID>/stats.json

可修改字段：
- power / powerMax：初始与完全成长后的攻击力量。
- speed / speedMax：初始与完全成长后的攻击、移动速度。
- rangeEffective：手动控制近战保持完整力量的距离（方块）。
- rangeMax：手动控制的最大距离。
- durability / durabilityMax：初始与完全成长后的体力效率和抗性。
- precision / precisionMax：初始与完全成长后的精准度。
- randomWeight：随机获得该替身时使用的相对权重。

修改并保存文件后执行原版 /reload 命令。服务端会加载数值并同步给
已连接客户端。普通生成会保留已有文件；只有需要恢复全部默认模板时才执行
/jojoconfig stand_stats force，也可在 force 后追加替身 ID 仅覆盖该文件。
