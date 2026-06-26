# 本模组修改Mek本体相关事项及工作流

由于激光器发射的激光涉及到大量坐标判定，且所有判定均来自一个初始位置，即MekLaserWorkFlow.md所述的`from`变量
而该变量初始化需要通过激光器坐标初始化，当激光器处于Sable物理结构时，就会出现大量异常。
原因是`from`通过激光器位置初始化，而激光器在物理结构上时会具有巨大的坐标值。
并且每次涉及到Sable物理结构所在系统外的坐标影响时（如伤害生物体或击中方块时的`to`坐标变化），游戏会出现异常巨大的AABB等。
还有，这个过程中几乎所有涉及到`Direction`的运算都会因为Mek自身的优化机制受到影响。

本模组会尝试将所有被影响到的坐标转换为主世界坐标。
本模组会对如下位置进行注入：（未标明来源的均为`TileEntityBasicLaser.java`内容）
- `from`的初始化。
- `Pos3d.translate(Direction dir, double amount)`方法，此处会影响到`from`的初始化、`to`的初始化与`LaserParticle.Factory.createParticle()`中的`end`参数（等效于`to`）的初始化。
- `Pos3d.adjustPosition(Direction dir, Entity entity)`方法，会影响到`to`的伤害实体时的重新赋值。
- 其他涉及到`Direction`判定的方法。

本模组为了传递数据，会调用一部分自己实现的NeoForge事件，均位于`src/main/java/dev/herobrine34187/meklasercompat/event`下。



# 此前的思路

头疼医头脚疼医脚（
当时只对一小部分做了改动，无法修复诸如巨大的AABB一类的问题。