package moe.ono.hooks.item.entertainment

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
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
    path = "娱乐功能/偷流量",
    description = "发送偷流量消息 (是不司马了)\n* 需在 快捷菜单 中使用"
)
class StealTraffic : BaseSwitchFunctionHookItem(), IShortcutMenu {

    override fun isAdd(): Boolean {
        return this.isEnabled
    }

    override val menuName: String
        get() = "偷流量"

    override fun clickHandle(context: Context) {
        val fixContext = CommonContextWrapper.createAppCompatContext(context)
        reportVisitor(AppRuntimeHelper.getAccount(), "CreateView-StealTraffic")

        fun createEdit(hint: String, default: String = ""): EditText {
            return EditText(fixContext).apply {
                this.hint = hint
                setText(default)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
        }

        fun labeledRow(label: String, edit: EditText): LinearLayout {
            return LinearLayout(fixContext).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
                addView(TextView(fixContext).apply {
                    text = label
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.marginEnd = 16 }
                })
                addView(edit)
            }
        }

        val etTitle = createEdit("标题","当你看到这条消息的时候你已经失去了1g流量和内存")
        val etDesc  = createEdit("简介","正在为你下载大小为1gb的垃圾文件")

        val root = LinearLayout(fixContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
            addView(labeledRow("标题", etTitle))
            addView(labeledRow("简介", etDesc))
        }

        val dialog = AlertDialog.Builder(fixContext)
            .setTitle("发送偷流量消息")
            .setView(root)
            .setPositiveButton("发送", null)
            .setNegativeButton("取消", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = etTitle.text.toString().also {
                    if (it.isEmpty()) { Toasts.error(context, "请输入标题"); return@setOnClickListener }
                }
                val desc = etDesc.text.toString().also {
                    if (it.isEmpty()) { Toasts.error(context, "请输入简介"); return@setOnClickListener }
                }

                val PBTemplate = """{
    "1": {
        "1": "qq.com",
        "12": {
            "14": {
                "1": "$title",
                "2": "$desc",
                "3": "https://gzc-download.ftn.qq.com/ftn_handler/98B4D18377A79E7B8F61BF731C94BC14F7B31FBA78378AC724D3C8CA87DDAFCF3550C345AB1AC7D0A4EAC72FFD8A379307D5D9CD78EE78ACF9AEE79F6346A14B/&client_proto=qq"
            }
        }
    }
}"""

                dialog.dismiss()

                SyncUtils.runOnUiThread {
                    PacketHelperDialog.createView(null, context, PBTemplate)
                    Handler(Looper.getMainLooper()).postDelayed({
                        PacketHelperDialog.setContent(PBTemplate,true)
                    }, 50)
//                    Handler(Looper.getMainLooper()).postDelayed({
//                        PacketHelperDialog.performAutoSend(context)
//                    }, 100)
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