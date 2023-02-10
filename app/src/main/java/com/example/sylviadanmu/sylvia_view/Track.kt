package com.example.sylviadanmu.sylvia_view

class Track {
    var danmus: ArrayList<Danmu> = arrayListOf()    //轨道当前存在的弹幕
    var occupiedWidth: Float = 0f                   //轨道被占据的宽度

    fun addDanmu(danmu: Danmu) {
        danmus.add(danmu)
    }

    fun reset() {
        this.danmus.clear()
        occupiedWidth = 0f
    }

    fun removeDanmuWithIndex(index: Int) {
        if (index < 0 || index >= danmus.size) {
            return
        }
        danmus.removeAt(index)
    }

    /**
     * @param updateTimeMilliseconds 定期更新offset时间，单位是ms
     */
    fun updateTrackOccupiedWidth(updateTimeMilliseconds: Int) {
        if (danmus.size <= 0) return
        val lastDanmu = danmus[danmus.size - 1]
        occupiedWidth -= lastDanmu.speed * updateTimeMilliseconds / 1000f
    }
}