package moe.ono.hooks.item.chat

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.lxj.xpopup.XPopup
import com.lxj.xpopup.interfaces.OnInputConfirmListener
import moe.ono.creator.PacketHelperDialog
import moe.ono.hooks._base.BaseClickableFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks.base.util.Toasts
import moe.ono.loader.hookapi.IShortcutMenu
import moe.ono.ui.CommonContextWrapper
import moe.ono.util.SyncUtils
import java.lang.Exception

@SuppressLint("DiscouragedApi")
@HookItem(
    path = "聊天与消息/发白字",
    description = "发送白字消息，出现在快捷菜单中"
)
class SendWhiteText : BaseClickableFunctionHookItem(), IShortcutMenu {

    override fun isAdd(): Boolean {
        return this.isEnabled
    }

    override val menuName: String
        get() = "发白字"

    override fun clickHandle(context: Context) {
        // 创建输入框弹窗
        val fixContext = CommonContextWrapper.createAppCompatContext(context)
        XPopup.Builder(fixContext)
            .asInputConfirm(
                "发送白字",
                "请输入要发送的白字内容",
                "",
                object : OnInputConfirmListener {
                    override fun onConfirm(text: String?) {
                        if (text.isNullOrEmpty()) {
                            Toasts.error(context, "请输入要发送的内容")
                            return
                        }
                        
                        // 构造JSON模板
                        val jsonTemplate = "[{\"37\":{\"19\":{\"15\":102262}}},{\"1\":{\"1\":\"$text\"}}]"
                        
                        // 启动PacketHelper并填入JSON
                        SyncUtils.runOnUiThread {
                            PacketHelperDialog.createView(null, context, jsonTemplate)
                            
                            // 100ms后自动点击发送按钮
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