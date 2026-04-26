package moe.ono.hooks.item.entertainment

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import moe.ono.creator.PacketHelperDialog
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks.base.util.Toasts
import moe.ono.loader.hookapi.IShortcutMenu
import moe.ono.ui.CommonContextWrapper
import moe.ono.util.AppRuntimeHelper
import moe.ono.util.SyncUtils
import moe.ono.util.analytics.ActionReporter.reportVisitor

@SuppressLint("DiscouragedApi")
@HookItem(
    path = "娱乐功能/发大号聊天记录",
    description = "* 需在 快捷菜单 中使用"
)
class BigForward : BaseSwitchFunctionHookItem(), IShortcutMenu {

    override fun isAdd(): Boolean {
        return this.isEnabled
    }

    override val menuName: String
        get() = "发大号聊天记录"

    override fun clickHandle(context: Context) {
        val fixContext = CommonContextWrapper.createAppCompatContext(context)
        reportVisitor(AppRuntimeHelper.getAccount(), "CreateView-BigForward")

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

        val etTitle = createEdit("标题","标题")
        val etDesc  = createEdit("简介","简介")
        val etTag  = createEdit("Tag","Tag")
        val etResid  = createEdit("resid","aPFVh2DJBVPCBh9RMzNmk8BKxZJ1RYRB/vHgN1m7/DJWO7RYlZml9UGcmj5HJWjO")

        val root = LinearLayout(fixContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
            addView(labeledRow("标题", etTitle))
            addView(labeledRow("简介", etDesc))
            addView(labeledRow("Tag", etTag))
            addView(labeledRow("resid", etResid))
        }

        val dialog = AlertDialog.Builder(fixContext)
            .setTitle("发送大号聊天记录 Xml")
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
                val tag = etTag.text.toString().also {
                    if (it.isEmpty()) { Toasts.error(context, "请输入Tag"); return@setOnClickListener }
                }
                val resid = etResid.text.toString().also {
                    if (it.isEmpty()) { Toasts.error(context, "请输入Resid"); return@setOnClickListener }
                }

                val xmlTemplate = """<?xml version="1.0" encoding="utf-8"?><msg brief="" m_fileName="0" action="viewMultiMsg" tSum="1" flag="3" m_resid="$resid" serviceID="35" m_fileSize="0"> <item layout="1"> <title color="#777777" size="100">$title</title><title color="#777777" size="100">$desc</title> <summary color="#161616" size="26">$tag</summary> </item> <source name="群作业"></source></msg>"""

                dialog.dismiss()

                SyncUtils.runOnUiThread {
                    PacketHelperDialog.createView(null, context, xmlTemplate)
                    Handler(Looper.getMainLooper()).postDelayed({
                        PacketHelperDialog.setSendTypeToXml()
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