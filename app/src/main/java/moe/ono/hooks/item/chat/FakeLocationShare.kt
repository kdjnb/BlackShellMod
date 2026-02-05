package moe.ono.hooks.item.chat

import android.annotation.SuppressLint
import android.content.Context
import com.lxj.xpopup.XPopup
import com.lxj.xpopup.interfaces.OnInputConfirmListener
import moe.ono.creator.PacketHelperDialog
import moe.ono.hooks._base.BaseClickableFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks.base.util.Toasts
import moe.ono.loader.hookapi.IShortcutMenu
import moe.ono.ui.CommonContextWrapper
import moe.ono.util.SyncUtils
import android.os.Handler
import android.os.Looper

@SuppressLint("DiscouragedApi")
@HookItem(
    path = "聊天与消息/发假位置共享",
    description = "发送假位置共享消息，出现在快捷菜单中"
)
class FakeLocationShare : BaseClickableFunctionHookItem(), IShortcutMenu {

    override fun isAdd(): Boolean {
        return this.isEnabled
    }

    override val menuName: String
        get() = "发假位置共享"

    override fun clickHandle(context: Context) {
        // 创建输入框弹窗，获取用户输入的文字
        val fixContext = CommonContextWrapper.createAppCompatContext(context)
        XPopup.Builder(fixContext)
            .asInputConfirm(
                "发假位置共享",
                "请输入位置名称",
                "",
                object : OnInputConfirmListener {
                    override fun onConfirm(inputText: String?) {
                        if (inputText.isNullOrEmpty()) {
                            Toasts.error(context, "请输入位置名称")
                            return
                        }

                        // 构造JSON数据
                        val jsonTemplate = """{
    "53": {
        "1": 31,
        "2": {
            "1": "$inputText"
        },
        "3": 1
    }
}"""

                        // 启动PacketHelper并填入构造好的JSON
                        SyncUtils.runOnUiThread {
                            PacketHelperDialog.createView(null, context, jsonTemplate)

                            // 等待100ms后自动点击发送
                            Handler(Looper.getMainLooper()).postDelayed({
                                PacketHelperDialog.performAutoSend()
                            }, 100)
                        }
                    }
                }
            )
            .show()
    }

    override fun entry(classLoader: ClassLoader) {
        // 不需要实现任何hook逻辑，因为这是一个快捷菜单项
        // 功能通过点击菜单触发，而不是通过hook实现
    }
}