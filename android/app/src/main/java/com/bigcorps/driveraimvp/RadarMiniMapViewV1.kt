package com.srrotas.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max

/**
 * Mini-mapa vetorial do Radar.
 *
 * Não usa tiles nem SDK de mapas. Mostra apenas relação espacial:
 * destino central, raio e POIs. O objetivo é decisão rápida sem dependência
 * nova, custo de API ou aumento relevante do APK.
 */
class RadarMiniMapViewV1(context:Context):View(context) {
    data class Marker(
        val id:String,
        val lat:Double,
        val lng:Double,
        val title:String,
        val type:String,
        val potential:String,
    )
    data class State(
        val centerLat:Double,
        val centerLng:Double,
        val radiusKm:Double,
        val markers:List<Marker>,
        val selectedId:String?=null,
    )

    private var state:State?=null
    private var hitRects=emptyMap<String,RectF>()
    var onMarkerSelected:((String)->Unit)?=null

    private val density=resources.displayMetrics.density
    private val bg=Paint(Paint.ANTI_ALIAS_FLAG)
    private val line=Paint(Paint.ANTI_ALIAS_FLAG)
    private val text=Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill=Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        minimumHeight=(230*density).toInt()
        isClickable=true
        contentDescription="Mapa contextual do destino"
    }

    fun render(value:State) {
        state=value.copy(markers=value.markers.take(8))
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val w=MeasureSpec.getSize(widthMeasureSpec)
        val desired=(240*density).toInt()
        setMeasuredDimension(w,resolveSize(desired,heightMeasureSpec))
    }

    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val s=state ?: return
        val p=SrUi023.palette(context)
        bg.color=p.surface
        canvas.drawRoundRect(0f,0f,width.toFloat(),height.toFloat(),18*density,18*density,bg)

        val cx=width/2f
        val cy=height/2f
        val radiusPx=minOf(width,height)*0.38f

        line.style=Paint.Style.STROKE
        line.strokeWidth=1*density
        line.color=withAlpha(p.muted,70)
        canvas.drawCircle(cx,cy,radiusPx,line)
        canvas.drawCircle(cx,cy,radiusPx*.55f,line)

        // linhas leves apenas para dar leitura espacial, sem fingir ruas reais
        line.color=withAlpha(p.muted,35)
        canvas.drawLine(0f,cy*.65f,width.toFloat(),cy*1.3f,line)
        canvas.drawLine(width*.18f,0f,width*.78f,height.toFloat(),line)
        canvas.drawLine(width*.82f,0f,width*.35f,height.toFloat(),line)

        // destino
        fill.color=p.blue
        canvas.drawCircle(cx,cy,8*density,fill)
        text.color=p.ink
        text.textSize=10*density
        text.textAlign=Paint.Align.CENTER
        canvas.drawText("DESTINO",cx,cy+24*density,text)

        val rects=mutableMapOf<String,RectF>()
        val kmLat=111.0
        val kmLng=max(25.0,111.0*cos(Math.toRadians(s.centerLat)))
        for(m in s.markers) {
            val dxKm=(m.lng-s.centerLng)*kmLng
            val dyKm=(m.lat-s.centerLat)*kmLat
            val scale=radiusPx/max(.5,s.radiusKm)
            var x=cx+(dxKm*scale).toFloat()
            var y=cy-(dyKm*scale).toFloat()
            val edge=14*density
            x=x.coerceIn(edge,width-edge)
            y=y.coerceIn(edge,height-edge)

            val selected=m.id==s.selectedId
            fill.color=markerColor(p,m.potential)
            canvas.drawCircle(x,y,(if(selected)10 else 7)*density,fill)
            if(selected) {
                line.style=Paint.Style.STROKE
                line.strokeWidth=2*density
                line.color=p.ink
                canvas.drawCircle(x,y,13*density,line)
            }
            rects[m.id]=RectF(x-20*density,y-20*density,x+20*density,y+20*density)
        }
        hitRects=rects

        text.textAlign=Paint.Align.LEFT
        text.textSize=9*density
        text.color=p.muted
        canvas.drawText("${String.format(java.util.Locale("pt","BR"),"%.1f",s.radiusKm)} km",10*density,height-10*density,text)
    }

    override fun onTouchEvent(event:MotionEvent):Boolean {
        if(event.action==MotionEvent.ACTION_UP) {
            val hit=hitRects.entries.minByOrNull {
                val cx=(it.value.left+it.value.right)/2
                val cy=(it.value.top+it.value.bottom)/2
                abs(event.x-cx)+abs(event.y-cy)
            }?.takeIf { it.value.contains(event.x,event.y) }
            if(hit!=null) {
                performClick()
                onMarkerSelected?.invoke(hit.key)
                return true
            }
        }
        return true
    }

    override fun performClick():Boolean {
        super.performClick()
        return true
    }

    private fun markerColor(p:SrUi023.Palette,potential:String)=when(potential) {
        "high"->p.teal
        "medium"->p.orange
        "low"->p.red
        else->p.purple
    }
    private fun withAlpha(color:Int,alpha:Int)=(color and 0x00FFFFFF) or ((alpha.coerceIn(0,255)) shl 24)
}
