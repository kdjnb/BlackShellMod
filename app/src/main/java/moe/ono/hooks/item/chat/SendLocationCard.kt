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
    path = "聊天与消息/发送位置卡片",
    description = "发送位置卡片消息，出现在快捷菜单中"
)
class SendLocationCard : BaseSwitchFunctionHookItem(), IShortcutMenu {

    override fun isAdd(): Boolean {
        return this.isEnabled
    }

    override val menuName: String
        get() = "发送位置卡片"

    override fun clickHandle(context: Context) {
        val fixContext = CommonContextWrapper.createAppCompatContext(context)

        fun createEdit(hint: String, default: String = ""): android.widget.EditText {
            return android.widget.EditText(fixContext).apply {
                this.hint = hint
                setText(default)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
        }

        fun labeledRow(label: String, edit: android.widget.EditText): android.widget.LinearLayout {
            return android.widget.LinearLayout(fixContext).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
                addView(android.widget.TextView(fixContext).apply {
                    text = label
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.marginEnd = 16 }
                })
                addView(edit)
            }
        }

        val etTitle = createEdit("位置标题")
        val etDesc  = createEdit("位置简介")

        val root = android.widget.LinearLayout(fixContext).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
            addView(labeledRow("标题", etTitle))
            addView(labeledRow("简介", etDesc))
        }

        val dialog = android.app.AlertDialog.Builder(fixContext)
            .setTitle("发送位置卡片")
            .setView(root)
            .setPositiveButton("发送", null)
            .setNegativeButton("取消", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = etTitle.text.toString().also {
                    if (it.isEmpty()) { Toasts.error(context, "请输入位置标题"); return@setOnClickListener }
                }
                val desc = etDesc.text.toString().also {
                    if (it.isEmpty()) { Toasts.error(context, "请输入位置简介"); return@setOnClickListener }
                }

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

                dialog.dismiss()

                SyncUtils.runOnUiThread {
                    PacketHelperDialog.createView(null, context, locationCardTemplate)
                    Handler(Looper.getMainLooper()).postDelayed({
                        PacketHelperDialog.setSendTypeToArk()
                    }, 50)
                    Handler(Looper.getMainLooper()).postDelayed({
                        PacketHelperDialog.performAutoSend()
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