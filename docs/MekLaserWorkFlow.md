# Mek激光器工作流程（服务端）

激光器的所有运行逻辑均位于类`TileEntityBasicLaser.java`的`onUpdateServer()`方法内，本文档会重点强调与命中坐标、命中判定和渲染相关内容。

## 1、变量初始化
`onUpdateServer()`方法内会初始化三个记录变量：
- `Direction direction`，用于记录激光器的指向。
- `Pos3d from`，用于记录激光器发射激光的位置，通过自身位置初始化，并在同一行末尾加上一个向量，该向量由自身朝向的坐标乘上0.5得到。
- `Pos3d to`，由from转化而来，记录激光器命中的事物的位置（也可以是未命中任何事物时的光束末尾位置）。
注意：`to`的初始化方式为：`from.translate(direction, MekanismConfig.general.laserRange.get() - 0.002)`。
即在`from`向量上的某个由`direction`确定的方向上加上激光器的最大射程。

## 2、方块击中情况判定
在`onUpdateServer()`初始化完毕上述三个变量后，会先进行一次击中方块目标的判定：
- 如果击中某个方块，获取返回结果，**并将`to`的值修改为被击中方块的坐标**。
- 如果未击中任何方块，`to`保持原值不变。

## 3、生物击中情况初步判定
随后会进行生物扫描范围的初始化，扫描范围为一个AABB，即12条边均平行于对应坐标轴的立方体。
该立方体由方法`getLaserBox(Direction, Pos3d, Pos3d, float)`确定。
原版Mek在确定扫描范围时会将此前的`direction`、`from`、`to`参数传入。注意此处的`to`可能经由步骤2修改。
最后一个`float`值用于确定扩散范围（相当于激光束杀伤直径）
确定`AABB`（匿名）后，通过获取该`AABB`内的实体确定初步的范围。注意返回值是一个列表`List<Entity> hitEntities`，该列表未经过排序。

## 4、伤害生物判定
初始化`AABB adjustedAABB`，初值为空值。
在对`hitEntities`进行排序后（根据与from的距离由近到远排序），会遍历`hitEntities`元素。
遍历中会有如下修改值的情况：
- 如果目标为正在被激光伤害的实体，修改`to`值，**值由`from`经方法`adjustPosition(Direction, Entity)`变换得来**， 并结束遍历。
- 如果目标能被激光穿透，将`from`**修改为`from.adjustPostion(direction, entity)`的值**，即加上以`direction`为方向，受伤的实体与`from`的距离为模长的向量。

## 5、发送激光器数据至服务端，以便对所有玩家渲染
初始化匿名的`LaserParticleData(direction, to.distance(from), float)`。
其中第一个参数是激光的方向，第二个参数`to.distance(from)`是`to`到`from`的距离。
注意，此过程会在如下情况出现：
- 遍历结束
- 遍历未结束，但出现步骤4中的`from`被修改的情况。
匿名的Data会经由`sendLaserDataToPlayers(new LaserParticleData, from)`发送至服务器。
重点修改的工作流至此结束。



# 客户端激光粒子渲染流程

激光束的视觉效果实际上是一种粒子效果，由类`LaserParticle.java`实现。
该类采用工厂模式，其工厂类会根据服务端的`LaserParticleData`对象来创建粒子效果并经`render()`方法自动渲染。
注意每有新的`LaserParticleData`对象出现时，工厂就会通过`createParticle()`（参数略）方法构造一次`LaserParticle`的实例。

该类的构造器重点参数为：
- `Vec3 start`
- `Vec3 end`
- `Direction dir`
构造器只会被工厂中的`createParticle()`方法调用。
该方法实际上会在服务端的`TileEntityBasicLaser.sendLaserDataToPlayers(LaserParticleData data, Vec3 from)`被调用，传入的参数为服务端工作流程的步骤5所述。

注意，在该类的`render()`方法中有三个参数：
- `float newX`
- `float newY`
- `float newZ`
这三个参数为渲染位置参数，会影响到光束的渲染位置。如果添加了本模组，需要对这三个参数进行注入，以确保激光器运动过程中光束的平滑变换。