package moe.ono.hooks.item.chat

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import androidx.core.view.children
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.github.kyuubiran.ezxhelper.utils.argTypes
import com.github.kyuubiran.ezxhelper.utils.args
import com.github.kyuubiran.ezxhelper.utils.invokeMethod
import com.github.kyuubiran.ezxhelper.utils.newInstance
import com.lxj.xpopup.util.XPopupUtils
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks.base.api.QQMessageViewListener
import moe.ono.hooks.base.api.QQMsgViewAdapter
import moe.ono.hooks.clazz
import moe.ono.reflex.ClassUtils
import moe.ono.reflex.ConstructorUtils
import moe.ono.reflex.FieldUtils
import moe.ono.util.AppRuntimeHelper
import moe.ono.util.QAppUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import android.os.Environment
import java.net.URL
import de.robv.android.xposed.XposedBridge
import moe.ono.config.ConfigManager
import moe.ono.constants.Constants
import moe.ono.hooks._core.factory.HookItemFactory.getItem
import moe.ono.hooks.item.developer.NoReport
import java.security.MessageDigest

@HookItem(
    path = "聊天与消息/提示黑色壳子用户",
    description = "在黑色壳子用户的消息下面显示提示是黑色壳子用户\n\n* 对方开启 开发者选项/关掉操作上报 后提示不了"
)
class HintBlackShellUser : BaseSwitchFunctionHookItem() {

    private val ID_HINT_LAYOUT = 0x114520
    private val ID_HINT_TEXTVIEW = 0x114521

    private val constraintSetClz by lazy { "androidx.constraintlayout.widget.ConstraintSet".clazz!! }
    private val constraintLayoutClz by lazy { "androidx.constraintlayout.widget.ConstraintLayout".clazz!! }

    private var blacklistMd5Set: Set<String> = emptySet()
    private var lastLoadTime = 0L

    override fun entry(classLoader: ClassLoader) {

        loadList()
        Thread {
            try {
                val noReport = ConfigManager.getDefaultConfig()
                    .getBooleanOrFalse(Constants.PrekXXX + getItem(NoReport::class.java).path)

                val file = File(
                    Environment.getExternalStorageDirectory().path +
                            "/Android/media/com.tencent.mobileqq/blackshell/users.json"
                )

                if (file.exists() && System.currentTimeMillis() - file.lastModified() < 3600_000) {
                    return@Thread
                }

                file.parentFile?.mkdirs()

                val uin = QAppUtils.getCurrentUin().toLong()
                if (uin == 0L) return@Thread

                val req = JSONObject().apply {
                    put("uin", uin)
                    put("noReport", noReport)
                }

                val conn = (URL("https://service.blackshellx.org/api/v1/users").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 5000
                    readTimeout = 5000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }

                conn.outputStream.use { it.write(req.toString().toByteArray(Charsets.UTF_8)) }

                val response = conn.inputStream.bufferedReader().use { it.readText() }

                val arr = JSONObject(response).getJSONArray("users_md5")

                file.writeText(arr.toString())

            } catch (t: Throwable) {
                XposedBridge.log(t)
            }
        }.start()
        QQMessageViewListener.addMessageViewUpdateListener(
            this,
            object : QQMessageViewListener.OnChatViewUpdateListener {
                override fun onViewUpdateAfter(msgItemView: View, msgRecord: Any) {
                    val rootView = msgItemView as ViewGroup
                    if (!QQMsgViewAdapter.hasContentMessage(rootView)) return

                    // 移除旧提示（View 复用时清理）
                    rootView.findViewById<View>(ID_HINT_LAYOUT)?.let {
                        rootView.removeView(it)
                    }

                    val senderUin: Long = FieldUtils.create(msgRecord)
                        .fieldName("senderUin")
                        .fieldType(Long::class.java)
                        .firstValue(msgRecord)

                    // 自己发的不提示
                    if (senderUin == AppRuntimeHelper.getLongAccountUin()) return

                    // 超过1小时重新读文件
                    if (System.currentTimeMillis() - lastLoadTime > 3600_000) {
                        loadList()
                    }

                    if (isBlackShellUser(senderUin)) {
                        addHintView(rootView, senderUin)
                    }
                }
            }
        )
    }

    private fun isBlackShellUser(uin: Long): Boolean {
        val md5 = md5(uin.toString())
        return blacklistMd5Set.contains(md5)
    }

    private fun loadList() {
        try {
            val file = File("/sdcard/Android/media/com.tencent.mobileqq/blackshell/users.json")
            if (!file.exists()) return
            val arr = JSONArray(file.readText())
            blacklistMd5Set = (0 until arr.length()).map { arr.getString(it) }.toHashSet()
            lastLoadTime = System.currentTimeMillis()
        } catch (e: Exception) {
            // 读取失败不影响其他功能
        }
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @SuppressLint("SetTextI18n")
    private fun addHintView(rootView: ViewGroup, senderUin: Long) {
        val parentLayoutId = rootView.id
        val contentId: Int = QQMsgViewAdapter.getContentViewId()

        val newLayoutParams = ConstructorUtils.newInstance(
            ClassUtils.findClass("androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams"),
            arrayOf<Class<*>?>(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ) as ViewGroup.LayoutParams

        FieldUtils.create(newLayoutParams).fieldName("startToStart").setFirst(newLayoutParams, parentLayoutId)
        FieldUtils.create(newLayoutParams).fieldName("endToEnd").setFirst(newLayoutParams, parentLayoutId)
        FieldUtils.create(newLayoutParams).fieldName("topToTop").setFirst(newLayoutParams, contentId)

        val layout = LinearLayout(rootView.context).apply {
            layoutParams = ConstraintLayout.LayoutParams(
                0, // MATCH_CONSTRAINT
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
            id = ID_HINT_LAYOUT
            val drawable = GradientDrawable()
            drawable.shape = GradientDrawable.RECTANGLE
            drawable.setColor(Color.BLACK)
            drawable.cornerRadius = 10f
            drawable.alpha = 0x22
            background = drawable

            val _4 = XPopupUtils.dp2px(rootView.context, 4f)
            val _6 = XPopupUtils.dp2px(rootView.context, 6f)
            setPadding(_6, _4, _6, _4)
        }

        val textView = TextView(rootView.context).apply {
            id = ID_HINT_TEXTVIEW
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setTextColor(Color.WHITE)
            text = "黑色壳子用户"
        }

        layout.gravity = Gravity.CENTER
        layout.addView(textView)
        rootView.addView(layout)

        val constraintSet = constraintSetClz.newInstance(args())!!
        constraintSet.invokeMethod("clone", args(rootView), argTypes(constraintLayoutClz))

        val iMsg = rootView.children.indexOfFirst { it is LinearLayout && it.id != View.NO_ID }
        val idMsg = rootView.getChildAt(iMsg).id
        val idName = rootView.getChildAt(iMsg - 1).id

        constraintSet.invokeMethod(
            "connect",
            args(ID_HINT_LAYOUT, ConstraintLayout.LayoutParams.TOP, idMsg, ConstraintLayout.LayoutParams.BOTTOM, 0),
            argTypes(Int::class.java, Int::class.java, Int::class.java, Int::class.java, Int::class.java)
        )

        if (senderUin != AppRuntimeHelper.getLongAccountUin()) {
            constraintSet.invokeMethod(
                "connect",
                args(ID_HINT_LAYOUT, ConstraintSet.LEFT, idName, ConstraintSet.LEFT),
                argTypes(Int::class.java, Int::class.java, Int::class.java, Int::class.java)
            )
            constraintSet.invokeMethod(
                "setMargin",
                args(ID_HINT_LAYOUT, ConstraintSet.START, XPopupUtils.dp2px(rootView.context, 10f)),
                argTypes(Int::class.java, Int::class.java, Int::class.java)
            )
        } else {
            constraintSet.invokeMethod(
                "connect",
                args(ID_HINT_LAYOUT, ConstraintSet.RIGHT, idName, ConstraintSet.RIGHT),
                argTypes(Int::class.java, Int::class.java, Int::class.java, Int::class.java)
            )
            constraintSet.invokeMethod(
                "setMargin",
                args(ID_HINT_LAYOUT, ConstraintSet.END, XPopupUtils.dp2px(rootView.context, 10f)),
                argTypes(Int::class.java, Int::class.java, Int::class.java)
            )
        }

        constraintSet.invokeMethod("applyTo", args(rootView), argTypes(constraintLayoutClz))
    }
}