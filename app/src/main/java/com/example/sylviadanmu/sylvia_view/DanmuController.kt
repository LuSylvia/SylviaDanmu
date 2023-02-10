package com.example.sylviadanmu.sylvia_view

import android.animation.ObjectAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import kotlinx.coroutines.*
import java.util.*
import kotlin.math.abs

const val UpdateTime = 64 //每64ms，即0.064s更新一次

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

    private var mDefaultSpeed = 200f//随便写的

    private lateinit var mDisplayArea: ViewGroup

    //由调用层自己实现弹幕渲染逻辑
    lateinit var renderLogic: ((T) -> View)

    private var mScope: CoroutineScope? = null

    fun putData(data: T) {
        mWaitingQueue.add(data)
//        Log.d("Sylvia-qyh", "putData, data是$data")
    }

    fun setMaxTrackCount(maxCount: Int) {
        maxTrackCount = maxCount
    }

    /**
     * 划分轨道
     * @param displayArea 展示区
     * @param danmuHeight 每条弹幕的高度，这里暂且当成所有弹幕一样高
     */
    fun splitTracks(displayArea: ViewGroup, danmuHeight: Int) {
//        Log.d(
//            "Sylvia-qyh",
//            "displayArea width是${displayArea.measuredWidth}, height是${displayArea.measuredHeight}"
//        )
        mDisplayArea = displayArea
        mTrackWidth = displayArea.measuredWidth
        mTrackHeight = danmuHeight + 2 * danmuVerticalGap
        val trackCount: Int =
            (displayArea.measuredHeight / mTrackHeight).coerceAtMost(maxTrackCount)
        for (i in 0 until trackCount) {
            mTrackList.add(Track())
        }
//        Log.d("Sylvia-qyh", "轨道划分完成，总数是${mTrackList.size}")
    }

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
//                            Log.d("Sylvia-qyh", "发现废弃弹幕，删除完成")
                        }
                    }
                    //必须在这里统一更新，并且轨道的occupiedWidth仅与最后一个弹幕有关
                    it.updateTrackOccupiedWidth(UpdateTime)
                    //不能在遍历轨道的弹幕时，删除弹幕。会抛出ConcurrentModifyException
                    if (isRemove) {
                        it.removeDanmuWithIndex(removeIndex)
                    }
                }
                delay(UpdateTime.toLong())
            }
        }
    }

    fun stopRender() {
        mScope?.cancel()
        mScope = null
        mTrackList.forEach {
            for (index in it.danmus.indices) {
                it.danmus[index].stopAnimation()
            }
            it.reset()
        }
        mDanmuPool.clear()
//        Log.d("Sylvia-qyh", "停止弹幕播放")
    }

    private fun removeDanmuView(danmuView: View) {
        val parent = danmuView.parent ?: return
        (parent as ViewGroup).removeView(danmuView)
    }

    //找到最空闲的首条轨道
    private fun findAvailableTrack(): Int {
        var trackPos = -1
        var minTrackOffset = Float.MAX_VALUE
        for (pos in mTrackList.indices) {
            val track = mTrackList[pos]
//            Log.d(
//                "Sylvia-qyh",
//                "findAvailableTrack，pos = ${pos}, occupiedWidth = ${track.occupiedWidth}, mTrackWidth = $mTrackWidth"
//            )
            if (track.occupiedWidth <= mTrackWidth && track.occupiedWidth < minTrackOffset) {
                trackPos = pos
                minTrackOffset = track.occupiedWidth
            }
        }
//        Log.d(
//            "Sylvia-qyh",
//            "findAvailableTrack, 最终返回的空闲轨道位置是${trackPos}"
//        )
        return trackPos
    }

    private fun pushToTrack(): Boolean {
        val trackPos = findAvailableTrack()
        if (trackPos == -1 || mDanmuPool.isFull()) {
//            Log.d("Sylvia-qyh", "找不到空轨道，退出")
            return false
        }
        //每次都从数据队列取首位元素
        if (mWaitingQueue.size == 0 || mWaitingQueue.peekFirst() == null) {
//            Log.d("Sylvia-qyh", "没数据你造个锤子弹幕")
            return false
        }

        val data: T = mWaitingQueue.pop() ?: return false

        val danmuView = renderLogic.invoke(data)
        val danmu = Danmu(danmuView)

        //计算弹幕速度
        val track = mTrackList[trackPos]
        var speed: Float = if (track.danmus.isEmpty()) {
            mDefaultSpeed * danmu.getSpeedCoefficient()
        } else {
            //追及问题
            val preDanmuSpeed = track.danmus[track.danmus.size - 1].speed
            (mTrackWidth * preDanmuSpeed * 1.0f) / track.occupiedWidth
        }
        //防止弹幕速度太快了，一闪而过，观感很差
        speed = speed.coerceAtMost(mDefaultSpeed * 2.0f)
        danmu.speed = speed

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

        track.addDanmu(danmu)
        //添加到弹幕池中，统一管理
        mDanmuPool.add(danmu)

//        Log.d(
//            "Sylvia-qyh",
//            "danmuView measure完成， danmu width = ${danmu.width}， height = ${danmu.height}"
//        )

        //轨道被占据的宽度，为轨道宽度 + 1.1倍弹幕宽度 + 随机数，其实也就比弹幕的总位移多一点
        track.occupiedWidth = mTrackWidth + danmu.width * 1.1f + Random().nextInt(20) * 1.0f
        danmu.offset = mTrackWidth.toFloat()
//        Log.d(
//            "Sylvia-qyh",
//            "弹幕成功添加到轨道中， danmu offset = ${danmu.offset}， 轨道 offset = ${track.occupiedWidth}"
//        )
        startDanmuTranslateAnimation(danmu, track)
        return true
    }

    private fun startDanmuTranslateAnimation(danmu: Danmu, track: Track) {
        val startValue = mTrackWidth.toFloat()
        val endValue = -danmu.width.toFloat()
        val totalTime = (track.occupiedWidth / danmu.speed * 1000f).toLong() //乘1000是为了转换为ms
        //总位移其实就是track.width + danmu.width, 值从正到负
        val animator = ObjectAnimator.ofFloat(
            danmu.danmuView, "translationX", startValue, endValue
        )
        animator.interpolator = LinearInterpolator()
        animator.duration = totalTime
        danmu.animator = animator
        animator.start()
//        Log.d(
//            "Sylvia-qyh",
//            "弹幕动画开始播放, startValue = ${startValue}, endValue = ${endValue}, " +
//                    "speed = ${danmu.speed}, duration = ${animator.duration}"
//        )
    }
}

//最多只允许20个弹幕同时存在
fun <E> ArrayList<E>.isFull(): Boolean {
    return this.size >= 20
}