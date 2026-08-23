package com.srrotas.app

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

object UiKit {
    data class Palette(
        val background:Int,val surface:Int,val surfaceAlt:Int,val ink:Int,val muted:Int,val line:Int,
        val primary:Int,val primaryDark:Int,val orange:Int,val good:Int,val warn:Int,val bad:Int,
    )

    fun palette(context:Context):Palette {
        val dark=Appearance021.isDark(context)
        return if(dark) Palette(
            background=Color.rgb(5,38,47),surface=Color.rgb(7,55,70),surfaceAlt=Color.rgb(11,72,84),
            ink=Color.rgb(248,244,223),muted=Color.rgb(169,200,199),line=Color.rgb(19,101,108),
            primary=Color.rgb(14,153,152),primaryDark=Color.rgb(12,119,120),orange=Color.rgb(244,202,80),
            good=Color.rgb(16,168,134),warn=Color.rgb(230,182,49),bad=Color.rgb(217,92,82),
        ) else Palette(
            background=Color.rgb(248,244,223),surface=Color.rgb(255,253,246),surfaceAlt=Color.rgb(241,237,216),
            ink=Color.rgb(7,55,70),muted=Color.rgb(96,119,122),line=Color.rgb(218,220,199),
            primary=Color.rgb(12,135,136),primaryDark=Color.rgb(7,55,70),orange=Color.rgb(244,202,80),
            good=Color.rgb(16,168,134),warn=Color.rgb(230,182,49),bad=Color.rgb(217,92,82),
        )
    }

    @Suppress("DEPRECATION")
    fun applySystemBars(activity:Activity){val p=palette(activity);activity.window.statusBarColor=p.background;activity.window.navigationBarColor=p.surface}

    @Suppress("DEPRECATION")
    fun applySafeArea(root:View){
        val dark=Appearance021.isDark(root.context)
        val mask=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        root.systemUiVisibility=(root.systemUiVisibility and mask.inv()) or if(dark)0 else mask
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.VANILLA_ICE_CREAM)return
        val l=root.paddingLeft;val t=root.paddingTop;val r=root.paddingRight;val b=root.paddingBottom
        root.setOnApplyWindowInsetsListener{view,insets->val safe=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout());view.setPadding(l+safe.left,t+safe.top,r+safe.right,b+safe.bottom);insets};root.post{root.requestApplyInsets()}
    }

    fun title(context:Context,text:String,size:Float=26f)=TextView(context).apply{this.text=text;textSize=size;setTextColor(palette(context).ink);setTypeface(typeface,Typeface.BOLD)}
    fun sectionTitle(context:Context,text:String)=title(context,text,20f).apply{setPadding(0,dp(context,6),0,dp(context,8))}
    fun body(context:Context,text:String,size:Float=14f)=TextView(context).apply{this.text=text;textSize=size;setTextColor(palette(context).muted);setLineSpacing(0f,1.12f)}
    fun card(context:Context,padding:Int=16)=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(context,padding),dp(context,padding),dp(context,padding),dp(context,padding));background=rounded(context,palette(context).surface,20,palette(context).line,1);elevation=dp(context,2).toFloat()}
    fun primaryButton(context:Context,text:String,onClick:()->Unit)=button(context,text,true,onClick)
    fun secondaryButton(context:Context,text:String,onClick:()->Unit)=button(context,text,false,onClick)
    private fun button(context:Context,label:String,primary:Boolean,onClick:()->Unit):TextView{val p=palette(context);return TextView(context).apply{text=label;textSize=15f;gravity=Gravity.CENTER;setTypeface(typeface,Typeface.BOLD);setPadding(dp(context,14),dp(context,12),dp(context,14),dp(context,12));minHeight=dp(context,48);setTextColor(if(primary)Color.WHITE else p.ink);background=rounded(context,if(primary)p.primaryDark else p.surfaceAlt,15,if(primary)p.primaryDark else p.line,1);isClickable=true;isFocusable=true;setOnClickListener{onClick()}}}
    fun pill(context:Context,text:String,tone:String="neutral"):TextView{val p=palette(context);val bg=when(tone){"good"->p.good;"warn"->p.warn;"bad"->p.bad;"primary"->p.primary;else->p.surfaceAlt};val fg=if(tone=="neutral")p.ink else Color.WHITE;return TextView(context).apply{this.text=text;textSize=12f;gravity=Gravity.CENTER;setTypeface(typeface,Typeface.BOLD);setTextColor(fg);setPadding(dp(context,10),dp(context,5),dp(context,10),dp(context,5));background=rounded(context,bg,999)}}
    fun input(context:Context,hint:String,multiline:Boolean=false,numeric:Boolean=false)=EditText(context).apply{this.hint=hint;textSize=15f;setTextColor(palette(context).ink);setHintTextColor(palette(context).muted);setPadding(dp(context,13),dp(context,11),dp(context,13),dp(context,11));background=rounded(context,palette(context).surfaceAlt,14,palette(context).line,1);if(multiline){minLines=3;maxLines=6;gravity=Gravity.TOP}else setSingleLine(true);if(numeric)inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL}
    fun rounded(context:Context,color:Int,radiusDp:Int,strokeColor:Int?=null,strokeDp:Int=0)=GradientDrawable().apply{cornerRadius=dp(context,radiusDp).toFloat();setColor(color);if(strokeColor!=null&&strokeDp>0)setStroke(dp(context,strokeDp),strokeColor)}
    fun margin(view:View,top:Int=0,bottom:Int=0,start:Int=0,end:Int=0):View{view.layoutParams=LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT).apply{setMargins(dp(view.context,start),dp(view.context,top),dp(view.context,end),dp(view.context,bottom))};return view}
    fun dp(context:Context,value:Int)=(value*context.resources.displayMetrics.density).toInt()
}
