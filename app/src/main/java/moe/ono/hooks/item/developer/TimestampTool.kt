package moe.ono.hooks.item.developer

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.lxj.xpopup.XPopup
import com.lxj.xpopup.interfaces.OnInputConfirmListener
import moe.ono.creator.PacketHelperDialog
import moe.ono.hooks._base.BaseClickableFunctionHookItem
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks.base.util.Toasts
import moe.ono.loader.hookapi.IShortcutMenu
import moe.ono.ui.CommonContextWrapper
import moe.ono.util.AppRuntimeHelper
import moe.ono.util.SyncUtils
import moe.ono.util.analytics.ActionReporter.reportVisitor
import java.lang.Exception

@SuppressLint("DiscouragedApi")
@HookItem(
    path = "开发者选项/时间戳工具"
)
class TimestampTool : BaseSwitchFunctionHookItem(), IShortcutMenu {

    override fun isAdd(): Boolean {
        return this.isEnabled
    }

    override val menuName: String
        get() = "时间戳工具"

    override fun clickHandle(context: Context) {
        val fixContext = CommonContextWrapper.createAppCompatContext(context)
        reportVisitor(AppRuntimeHelper.getAccount(), "CreateView-TimestampTool")

        fun createEdit(hint: String): android.widget.EditText {
            return android.widget.EditText(fixContext).apply {
                this.hint = hint
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                layoutParams = android.widget.LinearLayout.LayoutParams(0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        }

        fun row(vararg views: android.view.View): android.widget.LinearLayout {
            return android.widget.LinearLayout(fixContext).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                views.forEach { addView(it) }
            }
        }

        val year = createEdit("年")
        val month = createEdit("月")
        val day = createEdit("日")
        val hour = createEdit("时")
        val minute = createEdit("分")
        val second = createEdit("秒")

        val resultView = android.widget.TextView(fixContext).apply {
            textSize = 14f
            setPadding(0, 20, 0, 0)
        }

        val copyBtn = android.widget.Button(fixContext).apply {
            text = "复制"
            visibility = android.view.View.GONE
        }

        val root = android.widget.LinearLayout(fixContext).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)

            addView(row(year, month, day))
            addView(row(hour, minute, second))
            addView(resultView)
            addView(copyBtn)
        }

        val dialog = android.app.AlertDialog.Builder(fixContext)
            .setTitle("时间戳工具")
            .setView(root)
            .setPositiveButton("转换", null)
            .setNegativeButton("关闭", null)
            .create()

        dialog.setOnShowListener {
            val btn = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            btn.setOnClickListener {
                try {
                    val y = year.text.toString().toInt()
                    val m = month.text.toString().toInt()
                    val d = day.text.toString().toInt()
                    val h = hour.text.toString().toInt()
                    val min = minute.text.toString().toInt()
                    val s = second.text.toString().toInt()

                    val ts = java.time.LocalDateTime.of(y, m, d, h, min, s)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toEpochSecond()

                    resultView.text = "Unix：$ts"

                    copyBtn.visibility = android.view.View.VISIBLE
                    copyBtn.setOnClickListener {
                        val clipboard = fixContext.getSystemService(Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("timestamp", ts.toString())
                        )
                        Toasts.success(context, "已复制！")
                    }

                } catch (e: Exception) {
                    resultView.text = "❌ 输入有误"
                    copyBtn.visibility = android.view.View.GONE
                }
            }
        }

        dialog.show()
    }

    override fun entry(classLoader: ClassLoader) {
        // 不需要实现任何hook逻辑，因为这是一个快捷菜单项
        // 功能通过点击菜单触发，而不是通过hook实现
    }
}