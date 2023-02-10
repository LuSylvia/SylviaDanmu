# android 简单实现弹幕功能

# 1.前言
如今大多视频网站都有弹幕功能，相较于以前的留言板，弹幕具有更好的交互性和实时性，也更符合用户的观看习惯.
本项目参考https://juejin.cn/post/7120931977556951047#heading-12实现，在此感谢原作者。

# 2.实现方式
要实现弹幕，简单的方法是定期往viewGroup中添加view，然后使用（translateAnimation/layout()）等方法让其动起来，并不断的回收；
复杂的方法则涉及surfaceView，以及onDraw的重新，实现起来难度较高。
由于bilibili弹幕库已经多年停止更新，作者技术水平有限，所以此处只介绍简单的方式。

# 3.目标
弹幕不重叠，不碰撞，并且样式能够自定义，不与业务实体强绑定


# 4.必要成员
要实现播放弹幕的功能，需要以下3个角色：


## （1）轨道
弹幕滚动所使用的区域，通常是划分一个固定区域用来展示弹幕，然后再根据弹幕的宽度与竖直间距划分出一条条轨道。
弹幕被推送到一条条轨道中，然后开始运动。

 ``` kotlin
class Track {
    var danmus: ArrayList<Danmu> = arrayListOf()    //轨道当前存在的弹幕
    var occupiedWidth: Float = 0f                   //轨道被占据的宽度
    ...
}
 ```

这里简单说明一下occupiedWidth，首先轨道的宽度是等于展示区的宽度，这是无可争议的。

如果要实现弹幕从展示区以外进入，并逐渐移出展示区的效果，那么弹幕的位移就一定会大于轨道宽度。
所以，当一个弹幕刚被添加到轨道中，这个轨道至少被占用了 **弹幕宽度 + 轨道宽度** 这么多距离。

实际会比这个值大一点，用于实现弹幕在水平方向上的间距。


## （2）弹幕
不仅包含了实际运动的view，也包含了一些用于计算弹幕位置的属性。

``` kotlin
class Danmu(
    var danmuView: View,            //真正滚动的弹幕view
) {
    var speed: Float = 0f           //速度
    var width: Int = 0              //弹幕宽度
    var height: Int = 0             //弹幕高度
    var offset: Float = 0f          //弹幕右侧与轨道左侧之间的距离
    var animator: ObjectAnimator? = null

    ...
}
```


## （3）控制器
负责定期从等待队列取出数据，找到合适的轨道，创建弹幕并推送到轨道中，弹幕超出边界后移除弹幕。

```kotlin
class DanmuController<T : Any> {
    //存储了展示区里的所有轨道
    private var mTrackList: ArrayList<Track> = arrayListOf()

    //等待队列，里面按序存放着用户发送的弹幕数据
    private var mWaitingQueue: LinkedList<T> = LinkedList<T>()

    private var mDanmuPool: ArrayList<Danmu> = arrayListOf()

    private var mTrackWidth: Int = 0

    private var mTrackHeight: Int = 0

    //最大轨道数
    private var maxTrackCount: Int = 2

    //弹幕在轨道中垂直居中后，其顶部距离轨道顶部的距离
    private var danmuVerticalGap: Int = 10

    private var mDefaultSpeed = 250f//随便写的

    private lateinit var mDisplayArea: ViewGroup

    //由调用层自己实现弹幕渲染逻辑
    lateinit var renderLogic: ((T) -> View)

    //...
}
```


# 5.核心方法
## （1）splitTrack()
将给定的展示区划分为多条轨道，为后续填充弹幕打好基础。

``` kotlin
/**
 * 划分轨道
 * @param displayArea 展示区
 * @param danmuHeight 每条弹幕的高度，这里暂且当成所有弹幕一样高
*/
fun splitTracks(displayArea: ViewGroup, danmuHeight: Int) {
    mDisplayArea = displayArea
    mTrackWidth = displayArea.measuredWidth
    //一条轨道的高度，由弹幕高度+ 2倍竖直方向上间距组成
    mTrackHeight = danmuHeight + 2 * danmuVerticalGap
    val trackCount: Int =
        (displayArea.measuredHeight / mTrackHeight).coerceAtMost(maxTrackCount)
    for (i in 0 until trackCount) {
        mTrackList.add(Track())
    }
}
```

一条轨道应该长这样：

***************** 轨道顶部 ****************

***************** 竖直间距 ****************

弹幕1       弹幕2      弹幕3... 

***************** 竖直间距 ****************

***************** 轨道底部 ****************


## （2）findAvailableTrack()
当轨道被占据宽度 < 轨道宽度时，我们认为这条轨道是空闲的（因为弹幕起始是在轨道外侧）

遍历所有轨道，找到被占据宽度最小的首条轨道，然后返回其索引； 如果不存在，返回-1.


```kotlin
//找到最空闲的一条轨道
private fun findAvailableTrack(): Int {
    var trackPos = -1
    var minTrackOffset = Float.MAX_VALUE
    for (pos in mTrackList.indices) {
        val track = mTrackList[pos]
        if (track.occupiedWidth <= mTrackWidth && track.occupiedWidth < minTrackOffset) {
            trackPos = pos
            minTrackOffset = track.occupiedWidth
        }
    }

    return trackPos
}
```

## （3）startRender()
绘制的核心逻辑被放在了pushToTrack()，这个方法剩下的代码主要是在处理删除弹幕。

首先，遍历所有轨道的每一个弹幕，依次执行以下逻辑：

i.减少弹幕offset，因为弹幕从右往左移，它在轨道中所占据的宽度是越来越小的。

计算公式为：弹幕offset = 弹幕offset - 定期更新的时间 * 弹幕速度

ii.弹幕所走的全程为 弹幕宽度 + 轨道宽度


当弹幕尾部超过轨道左侧一个弹幕宽度的距离后，我们认为这个弹幕已经走到了终点，应该被删除了。
将标志位标记为true，记录index，等会遍历完此轨道其他弹幕后再删除。

当一条轨道遍历完成后，更新轨道被占用的宽度，并删除刚才记录的弹幕。


```kotlin
fun startRender() {
    mScope = MainScope()
    mScope?.launch(Dispatchers.Main) {
        while (isActive) {
            pushToTrack()
            mTrackList.forEach {
                var isRemove = false
                var removeIndex: Int = -1
                for (index in it.danmus.indices) {
                    val danmu = it.danmus[index]

                    danmu.offset = danmu.offset - UpdateTime * danmu.speed / 1000f
                    //当弹幕尾部超过轨道左侧一个danmuWidth的宽度后，就代表弹幕该删除了
                    if (danmu.offset <= 0 && abs(danmu.offset) > danmu.width + 5) {
                        isRemove = true
                        removeIndex = index
                        mDanmuPool.remove(danmu)
                        removeDanmuView(danmu.danmuView)
                        Log.d("Sylvia-qyh", "发现废弃弹幕，删除完成")
                    }
                }
                //必须在这里统一更新，并且轨道的occupiedWidth仅与最后一个弹幕有关
                it.updateTrackOccupiedWidth(UpdateTime)
                //不能在遍历轨道的弹幕时，删除弹幕。会抛出ConcurrentModifyException
                if (isRemove) {
                    it.removeDanmuWithIndex(removeIndex)
                }
            }
            //每0.16s遍历一次
            delay(UpdateTime.toLong())
        }
    }
}
```

## （4）pushToTrack()
绘制的核心方法，分为几个部分来讲解。

i. 寻找空闲轨道，找到后判断等待队列是否存在数据，存在则继续构造弹幕，否则方法结束。
```kotlin
val trackPos = findAvailableTrack()
if (trackPos == -1 || mDanmuPool.isFull()) {
    Log.d("Sylvia-qyh", "找不到空轨道，退出")
    return false
}
//每次都从数据队列取首位元素
if (mWaitingQueue.isEmpty()) {
    Log.d("Sylvia-qyh", "没数据你造个锤子弹幕")
    return false
}

val danmuView = renderLogic.invoke(mWaitingQueue.pop())
val danmu = Danmu(danmuView)
```

ii.计算弹幕速度。
```kotlin
//计算弹幕速度
val track = mTrackList[trackPos]
var speed: Float = if (track.danmus.isEmpty()) {
    mDefaultSpeed * danmu.getSpeedCoefficient()
} else {
    val preDanmuSpeed = track.danmus[track.danmus.size - 1].speed
    (mTrackWidth * preDanmuSpeed * 1.0f) / track.occupiedWidth
}
//防止弹幕速度太快了，一闪而过，观感很差
speed = speed.coerceAtMost(mDefaultSpeed * 2.0f)
danmu.speed = speed
```
当轨道中不存在弹幕时，弹幕的速度为默认速度 乘 随机系数（范围是1.1~2.1）
当轨道中存在弹幕时，就涉及到了追及问题。

**追及问题**

假设轨道长为L，弹幕A 速度为V1，A已经走了距离S，

B刚被添加进轨道，要想A与B同时到达终点，B的速度至少为多少？

t = (L-S)/ V1,  t = L/ V2 => V2 = L * V1 / (L-S) = trackWidth * danmuA.speed / trackOffset


当A快到终点时，算出来的B的速度可能会特别快，所以要限制最大速度为默认速度的2倍，防止一闪而过，体验很差。

iii. 将弹幕添加到视图
```kotlin
//真正添加到视图中的代码
val danmuHeight = mTrackHeight - 2 * danmuVerticalGap
val layoutParams = ViewGroup.MarginLayoutParams(
    ViewGroup.MarginLayoutParams.WRAP_CONTENT,
    danmuHeight
).apply {
    topMargin = (trackPos) * mTrackHeight + danmuVerticalGap
    marginStart = 0
}
mDisplayArea.addView(danmuView, layoutParams)

//手动调用measure，提前算出danmuView的宽高，后面计算轨道被占用的距离要用
danmu.danmuView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
danmu.width = danmuView.measuredWidth
danmu.height = danmuHeight
```

iv.计算数据
```kotlin
//轨道被占据的宽度，为轨道宽度 + 1.1倍弹幕宽度 + 随机数，其实也就比弹幕的总位移多一点
track.occupiedWidth = mTrackWidth + danmu.width * 1.1f + Random().nextInt(20) * 1.0f
//弹幕左侧距离轨道左侧的距离，当然是轨道宽度
danmu.offset = mTrackWidth

track.addDanmu(danmu)
//添加到弹幕池中，统一管理
mDanmuPool.add(danmu)
startDanmuTranslateAnimation(danmu, track)
```

## （5）startDanmuTranslateAnimation（）
给每个弹幕设置位移动画。
```kotlin
private fun startDanmuTranslateAnimation(danmu: Danmu, track: Track) {
    val startValue = mTrackWidth.toFloat()
    val endValue = -danmu.width.toFloat()
    val totalTime = (track.occupiedWidth / danmu.speed * 1000f).toLong() //乘1000是为了转换为ms
    //总位移其实就是mTrackWidth + danmu.width, 值从正到负
    val animator = ObjectAnimator.ofFloat(
        danmu.danmuView, "translationX", startValue, endValue
    )
    animator.interpolator = LinearInterpolator()
    animator.duration = totalTime
    danmu.animator = animator
    animator.start()
}
```