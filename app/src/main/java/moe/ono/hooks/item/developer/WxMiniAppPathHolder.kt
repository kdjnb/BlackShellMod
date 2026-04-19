package moe.ono.hooks.item.developer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.children
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import moe.ono.R
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.reflex.ClassUtils
import moe.ono.reflex.FieldUtils
import moe.ono.ui.CommonContextWrapper
import moe.ono.util.Logger
import moe.ono.util.SyncUtils
import moe.ono.util.SystemServiceUtils


@SuppressLint("DiscouragedApi")
@HookItem(path = "开发者选项/WxMiniAppPathHolder", description = "* 在小程序页面有个绿色的悬浮窗")
class WxMiniAppPathHolder : BaseSwitchFunctionHookItem() {
    override fun targetProcess(): Int {
        return SyncUtils.PROC_WXA_CONTAINER
    }

    class WxMiniAppInfo {
        var appId = ""
        var pagePath = ""
        var referPagePath = ""

        constructor(appId: String, pagePath: String, referPagePath: String) {
            this.appId = appId
            this.pagePath = pagePath
            this.referPagePath = referPagePath
        }

        constructor()
    }

    fun getWxMiniAppInfo(activity: Activity): WxMiniAppInfo {
        val frameLayout = FieldUtils.create("com.tencent.luggage.wxa.j1.b").fieldType(
            FrameLayout::class.java).firstValue<FrameLayout>(activity)
        val llg = frameLayout.children.find { it.id == -1 } ?: return WxMiniAppInfo()
        if (llg !is FrameLayout) return WxMiniAppInfo()
        val gaa = llg.children.find { it::class.java.name == "com.tencent.luggage.wxa.s6.g\$a\$a" } ?: return WxMiniAppInfo()
        val e = FieldUtils.create("com.tencent.luggage.wxa.y4.b").fieldType(
            ClassUtils.findClass("com.tencent.luggage.wxa.fk.e")).firstValue<Any>(gaa) ?: return WxMiniAppInfo()
        val i = FieldUtils.create(e).fieldType(ClassUtils.findClass("com.tencent.luggage.wxa.fk.i")).firstValue<Any>(e) ?: return WxMiniAppInfo()
        val appId = FieldUtils.create(i).fieldName("c").firstValue<String>(i) ?: ""
        val pagePath = FieldUtils.create(i).fieldName("i").firstValue<String>(i) ?: ""
        val referPagePath = FieldUtils.create(i).fieldName("l").firstValue<String>(i) ?: ""
        return WxMiniAppInfo(appId, pagePath, referPagePath)
    }

    override fun entry(classLoader: ClassLoader) {
        XposedHelpers.findAndHookMethod(
            "com.tencent.luggage.wxa.j1.b",
            classLoader,
            "onCreate",
            Bundle::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as Activity
                    Logger.d("com.tencent.luggage.wxa.j1.b#onCreate after, intent=${activity.intent}")
                    SyncUtils.postDelayed(2000) {
                        showActivityFloatingBall(activity)
                    }
                }
            }
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    fun showActivityFloatingBall(activity: Activity) {
        val context = activity
        val floatingBall = ImageView(context).apply {
            setImageResource(R.drawable.ic_ouo)
            setBackgroundResource(android.R.drawable.presence_online)
            layoutParams = FrameLayout.LayoutParams(150, 150).apply {
                gravity = Gravity.TOP or Gravity.START
                topMargin = 500
                leftMargin = 100
            }
        }

        val decorView = activity.window.decorView as FrameLayout
        decorView.addView(floatingBall)

        fun showTextDialog(context: Context, title: String, text: String) {
            MaterialAlertDialogBuilder(context).apply {
                setTitle(title)
                setMessage(text)
                setPositiveButton("复制") { _, _ ->
                    SystemServiceUtils.copyToClipboard(context, text)
                }
            }.show()
        }

        floatingBall.setOnClickListener { _ ->
            try {
                val context = CommonContextWrapper.createAppCompatContext(activity)
                val wxMiniAppInfo = getWxMiniAppInfo(activity)
                MaterialAlertDialogBuilder(context).apply {
                    setTitle("你要干什么呢?")
                    setItems(arrayListOf(
                        "查看 AppId",
                        "查看当前页面",
                        "查看 Refer 页面"
                    ).toTypedArray()) { _, i ->
                        when(i) {
                            0 -> {
                                showTextDialog(context, "AppId", wxMiniAppInfo.appId)
                            }
                            1 -> {
                                showTextDialog(context, "当前页面", wxMiniAppInfo.pagePath)
                            }
                            else -> {
                                showTextDialog(context, "Refer 页面", wxMiniAppInfo.referPagePath)
                            }
                        }
                    }
                }.show()
            } catch (e: Exception) {
                Logger.e(e)
            }
        }

        floatingBall.setOnTouchListener(object : View.OnTouchListener {

            private var downX = 0f
            private var downY = 0f
            private var lastX = 0f
            private var lastY = 0f
            private var isDragging = false
            private val touchSlop =
                ViewConfiguration.get(context).scaledTouchSlop

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {

                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        lastX = downX
                        lastY = downY
                        isDragging = false
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - lastX
                        val dy = event.rawY - lastY

                        if (!isDragging) {
                            val moveX = kotlin.math.abs(event.rawX - downX)
                            val moveY = kotlin.math.abs(event.rawY - downY)
                            if (moveX > touchSlop || moveY > touchSlop) {
                                isDragging = true
                            }
                        }

                        if (isDragging) {
                            v.x += dx
                            v.y += dy
                        }

                        lastX = event.rawX
                        lastY = event.rawY
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            v.performClick()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }
}