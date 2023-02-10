package com.example.sylviadanmu

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.sylviadanmu.databinding.ActivityMainBinding
import com.example.sylviadanmu.sylvia_view.DanmuController
import com.example.sylviadanmu.utils.DensityUtils
import kotlinx.coroutines.*


class MainActivity : AppCompatActivity() {
    private lateinit var mBinding: ActivityMainBinding
    private var mDataList: ArrayList<String> = arrayListOf()
    private var mainScope: CoroutineScope? = null
    private var index = 0
    private var isStart = false

    private val mDanmuController: DanmuController<String> by lazy {
        DanmuController()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(mBinding.root)
    }

    override fun onResume() {
        super.onResume()
        bindListener()
        initViewData()
    }

    private fun bindListener() {
        mBinding.btnStart.setOnClickListener {
            startPlayDanmu()
        }
        mBinding.btnStop.setOnClickListener {
            stopPlayDanmu()
        }
    }

    private fun initViewData() {
        initDataList()
        danmuPrepare()
    }

    private fun danmuPrepare() {
        mBinding.layoutContainer.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                mDanmuController.splitTracks(
                    mBinding.layoutContainer,
                    DensityUtils.dp2px(this@MainActivity, 50f)
                )
                mBinding.layoutContainer.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }

        })

        mDanmuController.renderLogic = { data ->
            val textView = TextView(this)
            textView.text = data
            textView.gravity = Gravity.CENTER
            textView.textSize = 15f
            textView.setTextColor(ActivityCompat.getColor(this, R.color.black))
            textView.layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.MarginLayoutParams.WRAP_CONTENT,
                DensityUtils.dp2px(this, 50f)
            )
            textView
        }
    }

    private fun startPlayDanmu() {
        if (!isStart) {
            mainScope = MainScope()
            mainScope?.launch(Dispatchers.IO) {
                while (isActive) {
                    if (index >= mDataList.size) {
                        index = 0
                    }
                    val data = mDataList[index]
                    mDanmuController.putData(data)
                    index++
                    delay(1000)
                }
            }
            mDanmuController.startRender()
            isStart = true
        }
    }

    private fun stopPlayDanmu() {
        mDanmuController.stopRender()
        isStart = false
        mainScope?.cancel()
        mainScope = null
        MainScope().launch {
            delay(1000L)
            mBinding.layoutContainer.removeAllViews()
        }
    }

    private fun initDataList() {
        val dataList: ArrayList<String> = arrayListOf()
        dataList.add("1.滚滚长江东逝水")
        dataList.add("2.浪花淘尽英雄")
        dataList.add("3.是非成败转头空")
        dataList.add("4.青山依旧在，几度夕阳红")
        dataList.add("5.白发渔樵江渚上")
        dataList.add("6.惯看秋月春风")
        dataList.add("7.一壶浊酒喜相逢")
        dataList.add("8.古今多少事，都付笑谈中")
        mDataList = dataList
    }


}