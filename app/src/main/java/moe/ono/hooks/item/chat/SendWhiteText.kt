package moe.ono.hooks.item.chat

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
import moe.ono.util.SyncUtils
import java.lang.Exception

@SuppressLint("DiscouragedApi")
@HookItem(
    path = "聊天与消息/发白字",
    description = "发送白色字体的消息\n* 需在 快捷菜单 中使用"
)
class SendWhiteText : BaseSwitchFunctionHookItem(), IShortcutMenu {

    override fun isAdd(): Boolean {
        return this.isEnabled
    }

    override val menuName: String
        get() = "发白字"

    override fun clickHandle(context: Context) {
        val fixContext = CommonContextWrapper.createAppCompatContext(context)

        val etText = android.widget.EditText(fixContext).apply {
            hint = "要发送的白字内容"
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val root = android.widget.LinearLayout(fixContext).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
            addView(etText)
        }

        val dialog = android.app.AlertDialog.Builder(fixContext)
            .setTitle("发送白字")
            .setView(root)
            .setPositiveButton("发送", null)
            .setNegativeButton("取消", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = etText.text.toString().also {
                    if (it.isEmpty()) { Toasts.error(context, "请输入要发送的内容"); return@setOnClickListener }
                }

                val jsonTemplate = "[{\"37\":{\"19\":{\"15\":102262}}},{\"1\":{\"1\":\"$text\"}}]"

                dialog.dismiss()

                SyncUtils.runOnUiThread {
                    PacketHelperDialog.createView(null, context, jsonTemplate)
                    Handler(Looper.getMainLooper()).postDelayed({
                        PacketHelperDialog.performAutoSend(context)
                    }, 100)
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