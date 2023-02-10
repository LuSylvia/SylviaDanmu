package com.example.sylviadanmu.sylvia_view

import android.animation.ObjectAnimator
import android.view.View
import java.util.*

data class Danmu(
    var danmuView: View,            //真正滚动的弹幕view
) {
    var isPause: Boolean = false     //是否暂停
    var speed: Float = 0f            //速度
    var width: Int = 0              //弹幕宽度
    var height: Int = 0             //弹幕高度
    var offset: Float = 0f          //弹幕左侧与轨道左侧之间的距离
    var animator: ObjectAnimator? = null


    /**
     * 返回的系数范围是1.1~2.1f
     */
    fun getSpeedCoefficient(): Float {
        return 1.1f + Random(System.currentTimeMillis()).nextFloat() * 1.0f
    }

    fun resumeAnimation() {
        animator?.resume()
        isPause = false
    }

    fun pauseAnimation() {
        animator?.pause()
        isPause = true
    }

    fun stopAnimation() {
        animator?.cancel()
        animator = null
    }

}