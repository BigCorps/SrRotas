package com.srrotas.app

/** Estado efêmero da Activity; nenhuma View, preferência ou abertura automática. */
internal class RadarSurfaceCoordinatorV1 {
    sealed class Request(val source:String) {
        class Real(val spec:RadarDestinationSpecV1,val opportunityId:String?,source:String):Request(source)
        class Demo(source:String):Request(source)
    }
    var resumed=false; private set
    var pending:Request?=null; private set
    var generation=0L; private set
    fun request(value:Request) { generation++; pending=value }
    fun pause() { resumed=false; generation++ }
    fun resume() { resumed=true }
    fun readyRequest():Request?=pending.takeIf { resumed }
    fun consume(value:Request):Boolean {
        if(!resumed || pending !== value) return false
        pending=null
        return true
    }
    fun cancel() { generation++; pending=null }
    data class Metrics(val stageAttached:Boolean,val panelAttached:Boolean,val shown:Boolean,
        val stageWidth:Int,val stageHeight:Int,val panelWidth:Int,val panelHeight:Int) {
        val ready:Boolean get()=stageAttached && panelAttached && shown &&
            stageWidth>0 && stageHeight>0 && panelWidth>0 && panelHeight>0
    }
    companion object {
        const val MAX_ATTEMPTS=6
        fun mayRetry(attempt:Int)=attempt<MAX_ATTEMPTS
    }
}
