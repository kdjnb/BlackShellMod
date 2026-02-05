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
    path = "聊天与消息/发送位置卡片",
    description = "发送位置卡片消息，出现在快捷菜单中"
)
class SendLocationCard : BaseClickableFunctionHookItem(), IShortcutMenu {

    override fun isAdd(): Boolean {
        return this.isEnabled
    }

    override val menuName: String
        get() = "发送位置卡片"

    override fun clickHandle(context: Context) {
        // 创建输入框弹窗，获取位置标题和位置简介
        val fixContext = CommonContextWrapper.createAppCompatContext(context)
        XPopup.Builder(fixContext)
            .asInputConfirm(
                "发送位置卡片",
                "请输入位置标题",
                "",
                object : OnInputConfirmListener {
                    override fun onConfirm(title: String?) {
                        if (title.isNullOrEmpty()) {
                            Toasts.error(context, "请输入位置标题")
                            return
                        }

                        // 创建输入框弹窗，获取位置简介
                        XPopup.Builder(fixContext)
                            .asInputConfirm(
                                "发送位置卡片",
                                "请输入位置简介",
                                "",
                                object : OnInputConfirmListener {
                                    override fun onConfirm(desc: String?) {
                                        if (desc.isNullOrEmpty()) {
                                            Toasts.error(context, "请输入位置简介")
                                            return
                                        }

                                        // 构造位置卡片JSON模板
                                        val locationCardTemplate = """{
    "app": "com.tencent.map",
    "config": {
        "autosize": false,
        "ctime": ${System.currentTimeMillis() / 1000},
        "forward": true,
        "token": "65953cdd33534eb26ac0f8381dab791e",
        "type": "normal"
    },
    "desc": "",
    "from": 1,
    "meta": {
        "Location.Search": {
            "address": "$desc",
            "enum_relation_type": 1,
            "from": "plusPanel",
            "from_account": 1211556192,
            "id": "",
            "lat": "39.018193",
            "lng": "125.800029",
            "name": "$title",
            "uint64_peer_account": 184796527
        }
    },
    "prompt": "[位置]$title",
    "ver": "1.1.2.21",
    "view": "LocationShare"
}"""

                                        // 启动PacketHelper并填入位置卡片JSON
                                        SyncUtils.runOnUiThread {
                                            PacketHelperDialog.createView(null, context, locationCardTemplate)
                                            
                                            // 稍等UI初始化后切换到ARK模式
                                            Handler(Looper.getMainLooper()).postDelayed({
                                                PacketHelperDialog.setSendTypeToArk()
                                            }, 50)

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
                }
            )
            .show()
    }

    override fun entry(classLoader: ClassLoader) {
        // 不需要实现任何hook逻辑，因为这是一个快捷菜单项
        // 功能通过点击菜单触发，而不是通过hook实现
    }
}