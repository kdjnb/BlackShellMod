package moe.ono.hooks.item.chat

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.os.Environment
import com.lxj.xpopup.XPopup
import com.lxj.xpopup.interfaces.OnInputConfirmListener
import com.lxj.xpopup.interfaces.OnSelectListener
import moe.ono.creator.PacketHelperDialog
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks.base.util.Toasts
import moe.ono.bridge.ManagerHelper
import moe.ono.hooks.item.developer.GetCookie
import moe.ono.hooks.message.SessionUtils
import moe.ono.hooks.protocol.sendPacket
import moe.ono.loader.hookapi.IShortcutMenu
import moe.ono.loader.hookapi.IRespHandler
import moe.ono.reflex.XField
import moe.ono.ui.CommonContextWrapper
import moe.ono.util.AesUtils
import moe.ono.util.AppRuntimeHelper
import moe.ono.util.Initiator
import moe.ono.util.Logger
import moe.ono.util.QAppUtils
import moe.ono.util.Session.getCurrentPeerID
import moe.ono.util.SyncUtils
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.io.File
import java.security.MessageDigest
import com.tencent.qphone.base.remote.FromServiceMsg
import com.tencent.qphone.base.remote.ToServiceMsg
import moe.ono.util.analytics.ActionReporter.reportVisitor
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import moe.ono.R


@SuppressLint("DiscouragedApi")
@HookItem(
    path = "聊天与消息/卡片功能",
    description = "请勿使用此功能用作任何违法行为，作者概不负责。部分功能非开源，你无法通过任何方式从BlackShell Mod的开源仓库中获取任何关于未开源的卡片的线索。\n\n* 为了溯源某些消息，服务端会记录日志\n* 需在 快捷菜单 中使用"
)
class CardFunc : BaseSwitchFunctionHookItem(), IShortcutMenu, IRespHandler {

    data class Product(
        val img: String,
        val ori_price: String,
        val price: String,
        val sales: String,
        val title: String,
        val url: String
    )

    // 用于处理响应的回调存储
    private var pendingResponseCallback: ((String?) -> Unit)? = null
    private var pendingResponseCmd: String? = null

    /**
     * Write debug log to file
     */
    private fun writeDebugLog(message: String) {
        try {
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
            val logMessage = "[$timestamp] CardFunc: $message\n"
            
            // 确保目录存在
            val debugDir = java.io.File("/sdcard/Android/media/com.tencent.mobileqq/blackshell/")
            if (!debugDir.exists()) {
                debugDir.mkdirs()
            }
            
            // 写入日志文件
            val logFile = java.io.File(debugDir, "debug.log")
            logFile.appendText(logMessage)
        } catch (e: Exception) {
            Logger.e("CardFunc", "Failed to write debug log: ${e.message}")
        }
    }

    // IRespHandler接口实现
    override val cmd: String
        get() = pendingResponseCmd ?: ""

    override fun onHandle(data: JSONObject?, service: ToServiceMsg, fromServiceMsg: FromServiceMsg) {
        if (service.serviceCmd == pendingResponseCmd) {
            pendingResponseCallback?.invoke(data?.toString())
            pendingResponseCallback = null
            pendingResponseCmd = null
        }
    }

    override fun isAdd(): Boolean {
        return this.isEnabled
    }

    override val menuName: String
        get() = "卡片功能"

    override fun clickHandle(context: Context) {
        val fixContext = CommonContextWrapper.createAppCompatContext(context)
        reportVisitor(AppRuntimeHelper.getAccount(), "CreateView-CardFunc")
        val options = arrayOf("音卡（OIAPI）","*元宝卡","*千问卡","*商品卡","*QQ空间盲盒签卡","*QQ空间video卡","*无tag图文卡","*测测测","*新版报名卡","*AI头像卡")//, "方式二：还没写好"
        
        XPopup.Builder(fixContext)
            .asCenterList("选择卡片(带*的选项仅授权后可用)", options, OnSelectListener { position, text ->
                when (position) {
                    0 -> sendMusicCardByAPI(context)
                    1 -> {
                        yuanbaoCard(context)
                    }
                    2 -> {
                        QianwenCard(context)
                    }
                    3 -> {
                        getGshopCard(context)
                    }
                    4 -> {
                        getQzoneCard(context)
                    }
                    5 -> {
                        getQzoneCard2(context)
                    }
                    6 -> {
                        noTagTuwen(context)
                    }
                    7 -> {
                        testCard(context)
                    }
                    8 -> {
                        teamupCard(context)
                    }
                    9 -> {
                        aiAvatarCard(context)
                    }
                }
            })
            .show()
    }

    private fun sendMusicCardByAPI(context: Context) {
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

        val formats = arrayOf("qq", "163", "kugou", "kuwo", "migu", "mihoyo", "kugoulite",
            "bodian", "baidu", "miui", "kuan", "qidianskland", "bilibili")

        val etMusicUrl = createEdit("歌曲链接")
        val etSongName = createEdit("歌名")
        val etSinger   = createEdit("歌手")
        val etCover    = createEdit("封面URL")
        val etJumpUrl  = createEdit("跳转链接")

        // 格式选择 Spinner
        val spinner = android.widget.Spinner(fixContext).apply {
            adapter = android.widget.ArrayAdapter(
                fixContext,
                android.R.layout.simple_spinner_dropdown_item,
                formats
            )
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val spinnerRow = android.widget.LinearLayout(fixContext).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
            addView(android.widget.TextView(fixContext).apply {
                text = "格式"
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = 16 }
            })
            addView(spinner)
        }

        val root = android.widget.LinearLayout(fixContext).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
            addView(labeledRow("歌曲链接", etMusicUrl))
            addView(labeledRow("歌名", etSongName))
            addView(labeledRow("歌手", etSinger))
            addView(labeledRow("封面", etCover))
            addView(labeledRow("跳转", etJumpUrl))
            addView(spinnerRow)
        }

        val dialog = android.app.AlertDialog.Builder(fixContext)
            .setTitle("发送音乐卡片")
            .setView(root)
            .setPositiveButton("发送", null)
            .setNegativeButton("取消", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val musicUrl = etMusicUrl.text.toString().also {
                    if (it.isEmpty()) { Toasts.error(context, "请输入歌曲链接"); return@setOnClickListener }
                }
                val songName = etSongName.text.toString().also {
                    if (it.isEmpty()) { Toasts.error(context, "请输入歌名"); return@setOnClickListener }
                }
                val singer = etSinger.text.toString().also {
                    if (it.isEmpty()) { Toasts.error(context, "请输入歌手"); return@setOnClickListener }
                }
                val coverUrl = etCover.text.toString().also {
                    if (it.isEmpty()) { Toasts.error(context, "请输入封面URL"); return@setOnClickListener }
                }
                val jumpUrl = etJumpUrl.text.toString().also {
                    if (it.isEmpty()) { Toasts.error(context, "请输入跳转链接"); return@setOnClickListener }
                }
                val format = spinner.selectedItem.toString()

                val requestBody = mapOf(
                    "url"    to musicUrl,
                    "song"   to songName,
                    "singer" to singer,
                    "cover"  to coverUrl,
                    "jump"   to jumpUrl,
                    "format" to format
                )

                postRequestToAPI(context, requestBody)
                dialog.dismiss()
            }
        }

        dialog.show()
    }
    private fun readPassFromFile(context: Context): String? {
        writeDebugLog("开始读取授权码文件")
        return try {
            val basePath = Environment.getExternalStorageDirectory().path
            val file = File(
                basePath,
                "Android/media/com.tencent.mobileqq/blackshell/pass.txt"
            )

            if (!file.exists()) {
                writeDebugLog("授权码文件不存在: ${file.absolutePath}")
                SyncUtils.runOnUiThread {
                    Toasts.popup("去找嘿壳获取授权码吧...\n你似乎需要进入这个群：\n1076550424")
                }
                null
            } else {
                val pass = file.readText()
                    .replace("\n", "")
                    .replace("\r", "")
                    .replace(" ", "")
                    .replace("\t", "")
                    .takeIf { it.isNotEmpty() }

                writeDebugLog("从文件中读取到授权码: ${if(pass != null) "长度${pass.length}字符" else "null"}")

                // 校验授权码格式
                if (pass != null && !isValidPass(pass)) {
                    writeDebugLog("授权码格式验证失败")
                    SyncUtils.runOnUiThread {
                        Toasts.error(context, "授权码格式无效")
                    }
                    return null
                } else if (pass != null) {
                    writeDebugLog("授权码格式验证成功")
                }

                pass
            }
        } catch (e: Exception) {
            writeDebugLog("读取授权码文件失败: ${e.message}")
            SyncUtils.runOnUiThread {
                Toasts.error(context, "读取 pass 失败: ${e.message}")
            }
            null
        }
    }

    private fun isValidPass(pass: String): Boolean {
        writeDebugLog("开始验证授权码格式")
        
        // 检查长度是否为64位
        if (pass.length != 64) {
            writeDebugLog("授权码长度验证失败: 实际长度${pass.length}，期望长度64")
            return false
        } else {
            writeDebugLog("授权码长度验证通过: 长度为64")
        }

        // 检查后4位是否为BSAC
        if (!pass.endsWith("BSAC")) {
            writeDebugLog("授权码后缀验证失败: 实际后缀'${pass.takeLast(4)}'，期望后缀'BSAC'")
            return false
        } else {
            writeDebugLog("授权码后缀验证通过: 后缀为'BSAC'")
        }

        // 获取当前用户信息，用于构建前32位的校验
        val uin = QAppUtils.getCurrentUin().toString()
        val domain = "qzone.qq.com" // 使用群聊域名，可以根据需要调整
        
        try {
            writeDebugLog("开始获取用户信息进行授权码前缀验证，当前UIN: $uin")
            
            // 使用GetCookie类获取p_skey、skey和p_uin
            val ticketManager = ManagerHelper.getManager(2)
            
            val getPSkeyMethod = ticketManager.javaClass.getDeclaredMethod("getPskey", String::class.java, String::class.java)
            val getSkeyMethod = ticketManager.javaClass.getDeclaredMethod("getSkey", String::class.java)
            val getPt4TokenMethod = ticketManager.javaClass.getDeclaredMethod("getPt4Token", String::class.java, String::class.java)

            val p_skey = getPSkeyMethod.invoke(ticketManager, uin, domain) as String
            val skey = getSkeyMethod.invoke(ticketManager, uin) as String
            val pt4Token = getPt4TokenMethod.invoke(ticketManager, uin, domain) as String?
            val p_uin = "o$uin"

            // 构建前半部分字符串
            val preString = "$uin$p_uin$p_uin"
            
            // 计算MD5并转大写
            val expectedPrefix = md5(preString).uppercase()
            
            // 验证前32位是否匹配
            val actualPrefix = pass.substring(0, 32)
            
            writeDebugLog("授权码前缀验证:")
            writeDebugLog("  - 构建的验证字符串: $preString")
            writeDebugLog("  - 计算出的期望前缀: $expectedPrefix")
            writeDebugLog("  - 实际授权码前缀: $actualPrefix")
            writeDebugLog("  - 前缀匹配结果: ${actualPrefix == expectedPrefix}")
            
            return actualPrefix == expectedPrefix
        } catch (e: Exception) {
            writeDebugLog("授权码验证过程中发生异常: ${e.message}")
            return false
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }


    private fun yuanbaoCard(context: Context) {
        val fixContext = CommonContextWrapper.createAppCompatContext(context)
        val peerid: String = getCurrentPeerID()

        if (readPassFromFile(context) == null) {
            Toasts.popup("去找嘿壳获取授权码吧...\n你似乎需要进入这个群：\n1076550424")
            return
        }

        val rpeerid = peerid.takeIf { it.isNotEmpty() } ?: "1076550424"

        fun createEdit(hint: String, default: String): android.widget.EditText {
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

        val etQunuin  = createEdit("发送群号", rpeerid)
        val etTitle   = createEdit("标题", "BlackShell Mod")
        val etDesc    = createEdit("简介", "元宝已被BlackShellX.org严肃嘿入")
        val etCover   = createEdit("封面URL", "https://p.qlogo.cn/gdynamic/MIwbbVhjoVzDgAsUTNsD0CtU5WJCz3gnHibZicw4YmISI/0")
        val etJumpUrl = createEdit("跳转链接", "https://c.safaa.cn/bs/ybcard_default.html")
        val etVer     = createEdit("ver（填你自己版本）", "9.2.66")

        val root = android.widget.LinearLayout(fixContext).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
            addView(labeledRow("群号", etQunuin))
            addView(labeledRow("标题", etTitle))
            addView(labeledRow("简介", etDesc))
            addView(labeledRow("封面", etCover))
            addView(labeledRow("跳转", etJumpUrl))
            addView(labeledRow("ver", etVer))
        }

        val dialog = android.app.AlertDialog.Builder(fixContext)
            .setTitle("发送元宝卡片")
            .setView(root)
            .setPositiveButton("发送", null)
            .setNegativeButton("取消", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val rqunuin   = etQunuin.text.toString().takeIf  { it.isNotEmpty() } ?: rpeerid
                val rtitle    = etTitle.text.toString().takeIf   { it.isNotEmpty() } ?: "BlackShell Mod"
                val rdesc     = etDesc.text.toString().takeIf    { it.isNotEmpty() } ?: "元宝已被BlackShellX.org严肃嘿入"
                val rcoverUrl = etCover.text.toString().takeIf   { it.isNotEmpty() } ?: "https://p.qlogo.cn/gdynamic/MIwbbVhjoVzDgAsUTNsD0CtU5WJCz3gnHibZicw4YmISI/0"
                val rjumpUrl  = etJumpUrl.text.toString().takeIf { it.isNotEmpty() } ?: "https://c.safaa.cn/bs/ybcard_default.html"
                val rver      = etVer.text.toString().takeIf     { it.isNotEmpty() } ?: "9.2.66"

                val pass = readPassFromFile(fixContext)
                if (pass.isNullOrEmpty()) return@setOnClickListener

                getYuanbaoPacket(
                    context = fixContext,
                    pass = pass,
                    qunuin = rqunuin,
                    title = rtitle,
                    desc = rdesc,
                    coverUrl = rcoverUrl,
                    jumpUrl = rjumpUrl,
                    ver = rver
                ) { cmd, json ->
                    sendPacket(cmd, json)
                }

                dialog.dismiss()
            }
        }

        dialog.show()
    }
    private fun QianwenCard(context: Context) {
        val fixContext = CommonContextWrapper.createAppCompatContext(context)
        val peerid: String = getCurrentPeerID()

        if (readPassFromFile(context) == null) {
            Toasts.popup("去找嘿壳获取授权码吧...\n你似乎需要进入这个群：\n1076550424")
            return
        }

        val rpeerid = peerid.takeIf { it.isNotEmpty() } ?: "1076550424"

        fun createEdit(hint: String, default: String): android.widget.EditText {
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

        val etQunuin  = createEdit("发送群号", rpeerid)
        val etTitle   = createEdit("标题", "BlackShell Mod")
        val etDesc    = createEdit("简介", "千问已被BlackShellX.org严肃嘿入")
        val etCover   = createEdit("封面URL", "https://p.qlogo.cn/gdynamic/MIwbbVhjoVzDgAsUTNsD0CtU5WJCz3gnHibZicw4YmISI/0")
        val etJumpUrl = createEdit("跳转链接", "https://c.safaa.cn/bs/ybcard_default.html")
        val etVer     = createEdit("ver（填你自己版本）", "9.2.27")

        val root = android.widget.LinearLayout(fixContext).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
            addView(labeledRow("群号", etQunuin))
            addView(labeledRow("标题", etTitle))
            addView(labeledRow("简介", etDesc))
            addView(labeledRow("封面", etCover))
            addView(labeledRow("跳转", etJumpUrl))
            addView(labeledRow("ver", etVer))
        }

        val dialog = android.app.AlertDialog.Builder(fixContext)
            .setTitle("发送千问卡片")
            .setView(root)
            .setPositiveButton("发送", null)
            .setNegativeButton("取消", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val rqunuin   = etQunuin.text.toString().takeIf  { it.isNotEmpty() } ?: rpeerid
                val rtitle    = etTitle.text.toString().takeIf   { it.isNotEmpty() } ?: "BlackShell Mod"
                val rdesc     = etDesc.text.toString().takeIf    { it.isNotEmpty() } ?: "千问已被BlackShellX.org严肃嘿入"
                val rcoverUrl = etCover.text.toString().takeIf   { it.isNotEmpty() } ?: "https://p.qlogo.cn/gdynamic/MIwbbVhjoVzDgAsUTNsD0CtU5WJCz3gnHibZicw4YmISI/0"
                val rjumpUrl  = etJumpUrl.text.toString().takeIf { it.isNotEmpty() } ?: "https://c.safaa.cn/bs/ybcard_default.html"
                val rver      = etVer.text.toString().takeIf     { it.isNotEmpty() } ?: "9.2.27"

                val pass = readPassFromFile(fixContext)
                if (pass.isNullOrEmpty()) return@setOnClickListener

                getQianwenPacket(
                    context = fixContext,
                    pass = pass,
                    qunuin = rqunuin,
                    title = rtitle,
                    desc = rdesc,
                    coverUrl = rcoverUrl,
                    jumpUrl = rjumpUrl,
                    ver = rver
                ) { cmd, json ->
                    sendPacket(cmd, json)
                }

                dialog.dismiss()
            }
        }

        dialog.show()
    }
    private fun noTagTuwen(context: Context) {
        val fixContext = CommonContextWrapper.createAppCompatContext(context)
        val peerid: String = getCurrentPeerID()

        if (readPassFromFile(context) == null) {
            Toasts.popup("去找嘿壳获取授权码吧...\n你似乎需要进入这个群：\n1076550424")
            return
        }

        val rpeerid = peerid.takeIf { it.isNotEmpty() } ?: "1076550424"

        fun createEdit(hint: String, default: String): android.widget.EditText {
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

        val etQunuin  = createEdit("发送群号", rpeerid)
        val etTitle   = createEdit("标题", "BlackShell Mod")
        val etDesc    = createEdit("简介", "BlackShellX.org")
        val etCover   = createEdit("封面URL", "https://p.qlogo.cn/gdynamic/MIwbbVhjoVzDgAsUTNsD0CtU5WJCz3gnHibZicw4YmISI/0")
        val etJumpUrl = createEdit("跳转链接", "https://c.safaa.cn/bs/ybcard_default.html")

        val root = android.widget.LinearLayout(fixContext).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
            addView(labeledRow("群号", etQunuin))
            addView(labeledRow("标题", etTitle))
            addView(labeledRow("简介", etDesc))
            addView(labeledRow("封面", etCover))
            addView(labeledRow("跳转", etJumpUrl))
        }

        val dialog = android.app.AlertDialog.Builder(fixContext)
            .setTitle("发送无Tag图文卡片")
            .setView(root)
            .setPositiveButton("发送", null)
            .setNegativeButton("取消", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val rqunuin   = etQunuin.text.toString().takeIf  { it.isNotEmpty() } ?: rpeerid
                val rtitle    = etTitle.text.toString().takeIf   { it.isNotEmpty() } ?: "BlackShell Mod"
                val rdesc     = etDesc.text.toString().takeIf    { it.isNotEmpty() } ?: "BlackShellX.org"
                val rcoverUrl = etCover.text.toString().takeIf   { it.isNotEmpty() } ?: "https://p.qlogo.cn/gdynamic/MIwbbVhjoVzDgAsUTNsD0CtU5WJCz3gnHibZicw4YmISI/0"
                val rjumpUrl  = etJumpUrl.text.toString().takeIf { it.isNotEmpty() } ?: "https://c.safaa.cn/bs/ybcard_default.html"

                val pass = readPassFromFile(fixContext)
                if (pass.isNullOrEmpty()) return@setOnClickListener

                getNoTagTuwenPacket(
                    context = fixContext,
                    qunuin = rqunuin,
                    pass = pass,
                    title = rtitle,
                    desc = rdesc,
                    coverUrl = rcoverUrl,
                    jumpUrl = rjumpUrl
                ) { cmd, json ->
                    sendPacket(cmd, json)
                }

                dialog.dismiss()
            }
        }

        dialog.show()
    }
        private fun postRequestToAPI(context: Context, requestBody: Map<String, String>) {
            // 构建POST请求体
            val json = JSONObject(requestBody).toString()
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = json.toRequestBody(mediaType)
            val request = Request.Builder()
                .url("https://oiapi.net/api/QQMusicJSONArk")
                .post(body)
                .build()

            val client = OkHttpClient()
            // 发起网络请求
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    SyncUtils.runOnUiThread {
                        Toasts.error(context, "网络请求失败: ${e.message}")
                    }
                }

    

                override fun onResponse(call: Call, response: Response) {
                    try {
                        if (!response.isSuccessful) {
                            SyncUtils.runOnUiThread {
                                Toasts.error(context, "请求失败: ${response.code}")
                            }
                            return
                        }
                        val responseBody = response.body?.string()
                        if (responseBody.isNullOrEmpty()) {
                            SyncUtils.runOnUiThread {
                                Toasts.error(context, "响应为空")
                            }
                            return
                        }
                        val jsonResponse = JSONObject(responseBody)
                        if (jsonResponse.optInt("code") != 1) {
                            SyncUtils.runOnUiThread {
                                Toasts.error(context, "API返回错误: ${jsonResponse.optString("msg")}")
                            }
                            return
                        }
                        // 提取data字段
                        var data = jsonResponse.optJSONObject("data")?.toString()
                        if (data.isNullOrEmpty()) {
                            SyncUtils.runOnUiThread {
                                Toasts.error(context, "未找到data字段")
                            }
                            return

                        }
                                            // 修改data字段，更新extra.uin为当前用户uin，并添加tips字段到最前面
                                            val originalDataJson = JSONObject(data)
                                            
                                            // 修改extra中的uin字段为当前用户uin
                                            if (originalDataJson.has("extra")) {
                                                val extraJson = originalDataJson.getJSONObject("extra")
                                                // 获取当前用户uin
                                                val currentUin = moe.ono.util.AppRuntimeHelper.getAccount()?.toLongOrNull() ?: 0L
                                                extraJson.put("uin", currentUin)
                                            } else {
                                                // 如果没有extra字段，创建一个并添加uin
                                                val extraJson = JSONObject()
                                                val currentUin = moe.ono.util.AppRuntimeHelper.getAccount()?.toLongOrNull() ?: 0L
                                                extraJson.put("uin", currentUin)
                                                originalDataJson.put("extra", extraJson)
                                            }
                                            
                                            // 创建一个新的JSON对象，确保tips字段在最前面
                                            val finalDataJson = JSONObject()
                                            finalDataJson.put("tips", "Powered by BlackShellMod | OIAPI")
                                            
                                            // 复制原始数据中的所有字段到新对象
                                            val keys = originalDataJson.keys()
                                            while (keys.hasNext()) {
                                                val key = keys.next()
                                                finalDataJson.put(key, originalDataJson.get(key))
                                            }
                                            
                                            data = finalDataJson.toString()
                                            
                                            SyncUtils.runOnUiThread {
                            // 自动打开PacketHelper
                            PacketHelperDialog.createView(null, context, data)
                            // 自动切换到ark发送模式
                            PacketHelperDialog.setSendTypeToArk()
                            // 等待100ms后自动点击发送
                            Handler(Looper.getMainLooper()).postDelayed({
                                PacketHelperDialog.performAutoSend(context)
                            }, 100)
                        }
                    } catch (e: Exception) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(context, "处理响应时出错: ${e.message}")
                        }
                    }
                }
            })
        }
    private fun getYuanbaoPacket(
        context: Context,
        pass: String,
        qunuin: String,
        title: String,
        desc: String,
        coverUrl: String,
        jumpUrl: String,
        ver: String,
        callback: (cmd: String, json: String) -> Unit
    ) {
        val requestBody = mapOf(
            "password" to pass,
            "qunuin" to qunuin,
            "title" to title,
            "desc" to desc,
            "coverUrl" to coverUrl,
            "jumpUrl" to jumpUrl,
            "ver" to ver
        )

        val json = JSONObject(requestBody).toString()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("https://service.blackshellx.org/api/v1/getYuanbaoCardPB")
            .post(body)
            .build()

        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                SyncUtils.runOnUiThread {
                    Toasts.error(context, "网络请求失败: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(context, "请求失败: ${response.code}")
                        }
                        return
                    }

                    val responseBody = response.body?.string()
                    if (responseBody.isNullOrEmpty()) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(context, "空响应")
                        }
                        return
                    }

                    val jsonResponse = JSONObject(responseBody)

                    if (jsonResponse.optInt("status") != 200) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(
                                context,
                                "API错误: ${jsonResponse.optString("msg")}"
                            )
                        }
                        return
                    }

                    val cmd = jsonResponse.optString("cmd")
                    val dataObj = jsonResponse.optJSONObject("data")

                    if (cmd.isEmpty() || dataObj == null) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(context, "返回数据不完整")
                        }
                        return
                    }

                    val dataJson = dataObj.toString()

                    // ⭐ 这里才是正确的使用点
                    SyncUtils.runOnUiThread {
                        callback(cmd, dataJson)
                    }

                } catch (e: Exception) {
                    SyncUtils.runOnUiThread {
                        Toasts.error(context, "解析失败: ${e.message}")
                    }
                }
            }
        })
    }
    private fun testCard(context: Context) {
        val fixContext = CommonContextWrapper.createAppCompatContext(context)
        val peerid: String = getCurrentPeerID()

        if (readPassFromFile(context) == null) {
            Toasts.popup("去找嘿壳获取授权码吧...\n你似乎需要进入这个群：\n1076550424")
            return
        }

        val rpeerid = peerid.takeIf { it.isNotEmpty() } ?: "1076550424"

        fun createEdit(hint: String, default: String): android.widget.EditText {
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

        val etQunuin  = createEdit("发送群号", rpeerid)
        val etTitle   = createEdit("标题", "BlackShell Mod")
        val etDesc    = createEdit("简介", "测试测试测试测试测试")
        val etCover   = createEdit("封面URL", "https://p.qlogo.cn/gdynamic/MIwbbVhjoVzDgAsUTNsD0CtU5WJCz3gnHibZicw4YmISI/0")
        val etJumpUrl = createEdit("跳转链接", "https://c.safaa.cn/bs/ybcard_default.html")

        val root = android.widget.LinearLayout(fixContext).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
            addView(labeledRow("群号", etQunuin))
            addView(labeledRow("标题", etTitle))
            addView(labeledRow("简介", etDesc))
            addView(labeledRow("封面", etCover))
            addView(labeledRow("跳转", etJumpUrl))
        }

        val dialog = android.app.AlertDialog.Builder(fixContext)
            .setTitle("发送测试卡片")
            .setView(root)
            .setPositiveButton("发送", null)
            .setNegativeButton("取消", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val rqunuin  = etQunuin.text.toString().takeIf  { it.isNotEmpty() } ?: rpeerid
                val rtitle   = etTitle.text.toString().takeIf   { it.isNotEmpty() } ?: "BlackShell Mod"
                val rdesc    = etDesc.text.toString().takeIf    { it.isNotEmpty() } ?: "测试测试测试测试测试"
                val rcoverUrl = etCover.text.toString().takeIf  { it.isNotEmpty() } ?: "https://p.qlogo.cn/gdynamic/MIwbbVhjoVzDgAsUTNsD0CtU5WJCz3gnHibZicw4YmISI/0"
                val rjumpUrl = etJumpUrl.text.toString().takeIf { it.isNotEmpty() } ?: "https://c.safaa.cn/bs/ybcard_default.html"

                val pass = readPassFromFile(fixContext)
                if (pass.isNullOrEmpty()) return@setOnClickListener

                getTestPacket(
                    context = fixContext,
                    pass = pass,
                    qunuin = rqunuin,
                    title = rtitle,
                    desc = rdesc,
                    coverUrl = rcoverUrl,
                    jumpUrl = rjumpUrl
                ) { cmd, json ->
                    sendPacket(cmd, json)
                }

                dialog.dismiss()
            }
        }

        dialog.show()
    }
    private fun getTestPacket(
        context: Context,
        pass: String,
        qunuin: String,
        title: String,
        desc: String,
        coverUrl: String,
        jumpUrl: String,
        callback: (cmd: String, json: String) -> Unit
    ) {
        val requestBody = mapOf(
            "password" to pass,
            "qunuin" to qunuin,
            "title" to title,
            "desc" to desc,
            "coverUrl" to coverUrl,
            "jumpUrl" to jumpUrl
        )

        val json = JSONObject(requestBody).toString()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("https://service.blackshellx.org/api/v1/getTestCardPB")
            .post(body)
            .build()

        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                SyncUtils.runOnUiThread {
                    Toasts.error(context, "网络请求失败: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
//                    if (!response.isSuccessful) {
//                        SyncUtils.runOnUiThread {
//                            Toasts.error(context, "请求失败: ${response.code}")
//                        }
//                        return
//                    }

                    val responseBody = response.body?.string()
                    if (responseBody.isNullOrEmpty()) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(context, "空响应")
                        }
                        return
                    }

                    val jsonResponse = JSONObject(responseBody)

                    if (jsonResponse.optInt("status") != 200) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(
                                context,
                                "${jsonResponse.optString("msg")}"
                            )
                        }
                        return
                    }

                    val cmd = jsonResponse.optString("cmd")
                    val dataObj = jsonResponse.optJSONObject("data")

                    if (cmd.isEmpty() || dataObj == null) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(context, "返回数据不完整")
                        }
                        return
                    }

                    val dataJson = dataObj.toString()

                    // ⭐ 这里才是正确的使用点
                    SyncUtils.runOnUiThread {
                        callback(cmd, dataJson)
                    }

                } catch (e: Exception) {
                    SyncUtils.runOnUiThread {
                        Toasts.error(context, "解析失败: ${e.message}")
                    }
                }
            }
        })
    }

    private fun aiAvatarCard(context: Context) {
        val fixContext = CommonContextWrapper.createAppCompatContext(context)
        val peerid: String = getCurrentPeerID()

        if (readPassFromFile(context) == null) {
            Toasts.popup("去找嘿壳获取授权码吧...\n你似乎需要进入这个群：\n1076550424")
            return
        }

        val rpeerid = peerid.takeIf { it.isNotEmpty() } ?: "1076550424"

        fun createEdit(hint: String, default: String): android.widget.EditText {
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

        val etQunuin  = createEdit("发送群号", rpeerid)
        val etTitle   = createEdit("标题", "BlackShell Mod")
        val etDesc    = createEdit("简介", "AI头像卡片测试")
        val etCover   = createEdit("封面URL", "https://qq-video.cdn-go.cn/url-resource/latest/defaultmode/ai_avatar/qq_avatar_ai_ark_img.png")
        val etTag = createEdit("tag", "BlackShell Mod")
        val etTagIcon = createEdit("tag图标", "https://qq-video.cdn-go.cn/url-resource/latest/defaultmode/ai_avatar/qq_icon_avatar_ai_ark.png")

        val root = android.widget.LinearLayout(fixContext).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
            addView(labeledRow("群号", etQunuin))
            addView(labeledRow("标题", etTitle))
            addView(labeledRow("简介", etDesc))
            addView(labeledRow("封面", etCover))
            addView(labeledRow("tag", etTag))
            addView(labeledRow("tag图标", etTagIcon))
        }

        val dialog = android.app.AlertDialog.Builder(fixContext)
            .setTitle("发送AI头像卡片")
            .setView(root)
            .setPositiveButton("发送", null)
            .setNegativeButton("取消", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val rqunuin  = etQunuin.text.toString().takeIf  { it.isNotEmpty() } ?: rpeerid
                val rtitle   = etTitle.text.toString().takeIf   { it.isNotEmpty() } ?: "BlackShell Mod"
                val rdesc    = etDesc.text.toString().takeIf    { it.isNotEmpty() } ?: "AI头像卡片测试"
                val rcoverUrl = etCover.text.toString().takeIf  { it.isNotEmpty() } ?: "https://qq-video.cdn-go.cn/url-resource/latest/defaultmode/ai_avatar/qq_avatar_ai_ark_img.png"
                val rtag = etTag.text.toString().takeIf { it.isNotEmpty() } ?: "BlackShell Mod"
                val rtagicon = etTagIcon.text.toString().takeIf { it.isNotEmpty() } ?: "https://qq-video.cdn-go.cn/url-resource/latest/defaultmode/ai_avatar/qq_icon_avatar_ai_ark.png"

                val pass = readPassFromFile(fixContext)
                if (pass.isNullOrEmpty()) return@setOnClickListener

                getAiAvatarPacket(
                    context = fixContext,
                    pass = pass,
                    qunuin = rqunuin,
                    title = rtitle,
                    desc = rdesc,
                    coverUrl = rcoverUrl,
                    tag = rtag,
                    tagIcon = rtagicon
                ) { cmd, json ->
                    sendPacket(cmd, json)
                }

                dialog.dismiss()
            }
        }

        dialog.show()
    }
    private fun getAiAvatarPacket(
        context: Context,
        pass: String,
        qunuin: String,
        title: String,
        desc: String,
        coverUrl: String,
        tag: String,
        tagIcon: String,
        callback: (cmd: String, json: String) -> Unit
    ) {
        val requestBody = mapOf(
            "password" to pass,
            "qunuin" to qunuin,
            "title" to title,
            "desc" to desc,
            "coverUrl" to coverUrl,
            "tag" to tag,
            "tagIcon" to tagIcon
        )

        val json = JSONObject(requestBody).toString()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("https://service.blackshellx.org/api/v1/getAiAvatarCardPB")
            .post(body)
            .build()

        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                SyncUtils.runOnUiThread {
                    Toasts.error(context, "网络请求失败: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
//                    if (!response.isSuccessful) {
//                        SyncUtils.runOnUiThread {
//                            Toasts.error(context, "请求失败: ${response.code}")
//                        }
//                        return
//                    }

                    val responseBody = response.body?.string()
                    if (responseBody.isNullOrEmpty()) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(context, "空响应")
                        }
                        return
                    }

                    val jsonResponse = JSONObject(responseBody)

                    if (jsonResponse.optInt("status") != 200) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(
                                context,
                                "${jsonResponse.optString("msg")}"
                            )
                        }
                        return
                    }

                    val cmd = jsonResponse.optString("cmd")
                    val dataObj = jsonResponse.optJSONObject("data")

                    if (cmd.isEmpty() || dataObj == null) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(context, "返回数据不完整")
                        }
                        return
                    }

                    val dataJson = dataObj.toString()

                    // ⭐ 这里才是正确的使用点
                    SyncUtils.runOnUiThread {
                        callback(cmd, dataJson)
                    }

                } catch (e: Exception) {
                    SyncUtils.runOnUiThread {
                        Toasts.error(context, "解析失败: ${e.message}")
                    }
                }
            }
        })
    }

    private fun teamupCard(context: Context) {
        val fixContext = CommonContextWrapper.createAppCompatContext(context)
        val peerid: String = getCurrentPeerID()

        if (readPassFromFile(context) == null) {
            Toasts.popup("去找嘿壳获取授权码吧...\n你似乎需要进入这个群：\n1076550424")
            return
        }

        val rpeerid = peerid.takeIf { it.isNotEmpty() } ?: "1076550424"

        fun createEdit(hint: String, default: String): android.widget.EditText {
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

        val etQunuin = createEdit("发送群号", rpeerid)
        val etTitle = createEdit("标题", "BlackShell Mod")
        val etDesc = createEdit("简介", "简介")
        val etTimestamp = createEdit("截止时间戳", (System.currentTimeMillis() / 1000).toString())

        val root = android.widget.LinearLayout(fixContext).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
            addView(labeledRow("群号", etQunuin))
            addView(labeledRow("标题", etTitle))
            addView(labeledRow("简介", etDesc))
            addView(labeledRow("截止时间", etTimestamp))
        }

        val dialog = android.app.AlertDialog.Builder(fixContext)
            .setTitle("发送新版群报名卡片")
            .setView(root)
            .setPositiveButton("发送", null)
            .setNegativeButton("取消", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val rqunuin = etQunuin.text.toString().takeIf { it.isNotEmpty() } ?: rpeerid
                val rtitle = etTitle.text.toString().takeIf { it.isNotEmpty() } ?: "BlackShell Mod"
                val rdesc = etDesc.text.toString().takeIf { it.isNotEmpty() } ?: "简介"
                val roverTimestamp = etTimestamp.text.toString().takeIf { it.isNotEmpty() }
                    ?.toIntOrNull() ?: (System.currentTimeMillis() / 1000).toInt()

                val pass = readPassFromFile(fixContext)
                if (pass.isNullOrEmpty()) return@setOnClickListener

                getTeamUpPacket(
                    context = fixContext,
                    pass = pass,
                    qunuin = rqunuin,
                    title = rtitle,
                    desc = rdesc,
                    overTimestamp = roverTimestamp
                ) { cmd, json ->
                    sendPacket(cmd, json)
                }

                dialog.dismiss()
            }
        }

        dialog.show()
    }
    private fun getTeamUpPacket(
        context: Context,
        pass: String,
        qunuin: String,
        title: String,
        desc: String,
        overTimestamp: Int,
        callback: (cmd: String, json: String) -> Unit
    ) {
        val requestBody = mapOf(
            "password" to pass,
            "qunuin" to qunuin,
            "title" to title,
            "desc" to desc,
            "overTimestamp" to overTimestamp
        )

        val json = JSONObject(requestBody).toString()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("https://service.blackshellx.org/api/v1/getTeamUpCardPB")
            .post(body)
            .build()

        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                SyncUtils.runOnUiThread {
                    Toasts.error(context, "网络请求失败: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
//                    if (!response.isSuccessful) {
//                        SyncUtils.runOnUiThread {
//                            Toasts.error(context, "请求失败: ${response.code}")
//                        }
//                        return
//                    }

                    val responseBody = response.body?.string()
                    if (responseBody.isNullOrEmpty()) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(context, "空响应")
                        }
                        return
                    }

                    val jsonResponse = JSONObject(responseBody)

                    if (jsonResponse.optInt("status") != 200) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(
                                context,
                                "${jsonResponse.optString("msg")}"
                            )
                        }
                        return
                    }

                    val cmd = jsonResponse.optString("cmd")
                    val dataObj = jsonResponse.optJSONObject("data")

                    if (cmd.isEmpty() || dataObj == null) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(context, "返回数据不完整")
                        }
                        return
                    }

                    val dataJson = dataObj.toString()

                    // ⭐ 这里才是正确的使用点
                    SyncUtils.runOnUiThread {
                        callback(cmd, dataJson)
                    }

                } catch (e: Exception) {
                    SyncUtils.runOnUiThread {
                        Toasts.error(context, "解析失败: ${e.message}")
                    }
                }
            }
        })
    }


    private fun getQianwenPacket(
        context: Context,
        pass: String,
        qunuin: String,
        title: String,
        desc: String,
        coverUrl: String,
        jumpUrl: String,
        ver: String,
        callback: (cmd: String, json: String) -> Unit
    ) {
        val requestBody = mapOf(
            "password" to pass,
            "qunuin" to qunuin,
            "title" to title,
            "desc" to desc,
            "coverUrl" to coverUrl,
            "jumpUrl" to jumpUrl,
            "ver" to ver
        )

        val json = JSONObject(requestBody).toString()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("https://service.blackshellx.org/api/v1/getQianwenCardPB")
            .post(body)
            .build()

        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                SyncUtils.runOnUiThread {
                    Toasts.error(context, "网络请求失败: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(context, "请求失败: ${response.code}")
                        }
                        return
                    }

                    val responseBody = response.body?.string()
                    if (responseBody.isNullOrEmpty()) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(context, "空响应")
                        }
                        return
                    }

                    val jsonResponse = JSONObject(responseBody)

                    if (jsonResponse.optInt("status") != 200) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(
                                context,
                                "API错误: ${jsonResponse.optString("msg")}"
                            )
                        }
                        return
                    }

                    val cmd = jsonResponse.optString("cmd")
                    val dataObj = jsonResponse.optJSONObject("data")

                    if (cmd.isEmpty() || dataObj == null) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(context, "返回数据不完整")
                        }
                        return
                    }

                    val dataJson = dataObj.toString()

                    // ⭐ 这里才是正确的使用点
                    SyncUtils.runOnUiThread {
                        callback(cmd, dataJson)
                    }

                } catch (e: Exception) {
                    SyncUtils.runOnUiThread {
                        Toasts.error(context, "解析失败: ${e.message}")
                    }
                }
            }
        })
    }
    private fun getNoTagTuwenPacket(
        context: Context,
        qunuin: String,
        pass: String,
        title: String,
        desc: String,
        coverUrl: String,
        jumpUrl: String,
        callback: (cmd: String, json: String) -> Unit
    ) {
        val requestBody = mapOf(
            "password" to pass,
            "qunuin" to qunuin,
            "title" to title,
            "desc" to desc,
            "coverUrl" to coverUrl,
            "jumpUrl" to jumpUrl
        )

        val json = JSONObject(requestBody).toString()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("https://service.blackshellx.org/api/v1/getNoTagTuwenPB")
            .post(body)
            .build()

        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                SyncUtils.runOnUiThread {
                    Toasts.error(context, "网络请求失败: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(context, "请求失败: ${response.code}")
                        }
                        return
                    }

                    val responseBody = response.body?.string()
                    if (responseBody.isNullOrEmpty()) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(context, "空响应")
                        }
                        return
                    }

                    val jsonResponse = JSONObject(responseBody)

                    if (jsonResponse.optInt("status") != 200) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(
                                context,
                                "API错误: ${jsonResponse.optString("msg")}"
                            )
                        }
                        return
                    }

                    val cmd = jsonResponse.optString("cmd")
                    val dataObj = jsonResponse.optJSONObject("data")

                    if (cmd.isEmpty() || dataObj == null) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(context, "返回数据不完整")
                        }
                        return
                    }

                    val dataJson = dataObj.toString()

                    // ⭐ 这里才是正确的使用点
                    SyncUtils.runOnUiThread {
                        callback(cmd, dataJson)
                    }

                } catch (e: Exception) {
                    SyncUtils.runOnUiThread {
                        Toasts.error(context, "解析失败: ${e.message}")
                    }
                }
            }
        })
    }

    private fun getGshopCard(context: Context) {
        val fixContext = CommonContextWrapper.createAppCompatContext(context)

        if (readPassFromFile(context) == null) {
            Toasts.popup("去找嘿壳获取授权码吧...\n你似乎需要进入这个群：\n1076550424")
            return
        }

        fun createEdit(hint: String, default: String): android.widget.EditText {
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

        val etBtnText = createEdit("按钮文字", "大嘿壳")
        val etPrompt  = createEdit("外显", "嘿壳群主")
        val etNum     = createEdit("商品数量", "1").apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        val root = android.widget.LinearLayout(fixContext).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
            addView(labeledRow("按钮", etBtnText))
            addView(labeledRow("外显", etPrompt))
            addView(labeledRow("数量", etNum))
        }

        val dialog = android.app.AlertDialog.Builder(fixContext)
            .setTitle("商品卡片")
            .setView(root)
            .setPositiveButton("下一步", null)
            .setNegativeButton("取消", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val finalBtnText = etBtnText.text.toString().takeIf { it.isNotEmpty() } ?: "大嘿壳"
                val finalPrompt  = etPrompt.text.toString().takeIf  { it.isNotEmpty() } ?: "嘿壳群主"
                val num = etNum.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1

                dialog.dismiss()
                collectProductsInfo(fixContext, finalBtnText, finalPrompt, num, 0, mutableListOf())
            }
        }

        dialog.show()
    }

    private fun collectProductsInfo(
        context: Context,
        btnText: String,
        prompt: String,
        totalNum: Int,
        currentIndex: Int,
        products: MutableList<Product>
    ) {
        if (currentIndex >= totalNum) {
            sendGshopCardRequest(context, readPassFromFile(context)!!, btnText, prompt, products)
            return
        }

        val fixContext = CommonContextWrapper.createAppCompatContext(context)
        val existing = products.getOrNull(currentIndex)
        val defaultImg = "https://gchat.qpic.cn/gchatpic_new/0/0-0-1E29EE09B8A0323A99F35A2A3275655F/0?term=2&is_origin=1"

        fun createEdit(hint: String, default: String): android.widget.EditText {
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

        val etImg      = createEdit("商品图片链接", existing?.img      ?: defaultImg)
        val etOriPrice = createEdit("商品原价",     existing?.ori_price ?: "")
        val etPrice    = createEdit("商品现价",     existing?.price     ?: "")
        val etSales    = createEdit("销量",         existing?.sales     ?: "")
        val etTitle    = createEdit("商品名称",     existing?.title     ?: "")
        val etUrl      = createEdit("商品链接",     existing?.url       ?: "")

        val root = android.widget.LinearLayout(fixContext).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
            addView(labeledRow("图片",  etImg))
            addView(labeledRow("原价",  etOriPrice))
            addView(labeledRow("现价",  etPrice))
            addView(labeledRow("销量",  etSales))
            addView(labeledRow("名称",  etTitle))
            addView(labeledRow("链接",  etUrl))
        }

        val dialog = android.app.AlertDialog.Builder(fixContext)
            .setTitle("第${currentIndex + 1}个商品（共${totalNum}个）")
            .setView(root)
            .setPositiveButton("下一步", null)
            .setNegativeButton("取消", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val finalImg      = etImg.text.toString().takeIf      { it.isNotEmpty() } ?: run { Toasts.error(context, "商品图片链接不能为空"); return@setOnClickListener }
                val finalOriPrice = etOriPrice.text.toString().takeIf { it.isNotEmpty() } ?: run { Toasts.error(context, "商品原价不能为空");     return@setOnClickListener }
                val finalPrice    = etPrice.text.toString().takeIf    { it.isNotEmpty() } ?: run { Toasts.error(context, "商品现价不能为空");     return@setOnClickListener }
                val finalSales    = etSales.text.toString().takeIf    { it.isNotEmpty() } ?: run { Toasts.error(context, "销量不能为空");         return@setOnClickListener }
                val finalTitle    = etTitle.text.toString().takeIf    { it.isNotEmpty() } ?: run { Toasts.error(context, "商品名称不能为空");     return@setOnClickListener }
                val finalUrl      = etUrl.text.toString().takeIf      { it.isNotEmpty() } ?: run { Toasts.error(context, "商品链接不能为空");     return@setOnClickListener }

                products.add(Product(finalImg, finalOriPrice, finalPrice, finalSales, finalTitle, finalUrl))
                dialog.dismiss()

                collectProductsInfo(context, btnText, prompt, totalNum, currentIndex + 1, products)
            }
        }

        dialog.show()
    }

    private fun showArkConfirmDialog(context: Context, arkJson: String) {
        val dialog = AlertDialog.Builder(context).create()
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_ark_preview, null)
        dialog.setView(view)
        dialog.setCancelable(false)

        view.findViewById<TextView>(R.id.tv_ark_content).text = try {
            JSONObject(arkJson).toString(2)
        } catch (e: Exception) {
            arkJson
        }

        // 关闭
        view.findViewById<Button>(R.id.btn_close).setOnClickListener {
            dialog.dismiss()
        }

        // 复制
        view.findViewById<Button>(R.id.btn_copy).setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Ark", arkJson))
            Toasts.success(context, "已复制到剪贴板")
        }

        // 发送
        view.findViewById<Button>(R.id.btn_send).setOnClickListener {
            dialog.dismiss()
            PacketHelperDialog.createView(null, context, arkJson)
            PacketHelperDialog.setSendTypeToArk()
            Handler(Looper.getMainLooper()).postDelayed({
                PacketHelperDialog.performAutoSend(context)
            }, 100)
        }

        dialog.show()
    }

    private fun sendGshopCardRequest(
        context: Context,
        password: String,
        btnText: String,
        prompt: String,
        products: List<Product>
    ) {
        val json = JSONObject().apply {
            put("password", password)
            put("btnText", btnText)
            put("prompt", prompt)
            
            // 将产品列表转换为JSONArray
            val productsArray = org.json.JSONArray()
            products.forEach { product ->
                val productJson = org.json.JSONObject().apply {
                    put("img", product.img)
                    put("ori_price", product.ori_price)
                    put("price", product.price)
                    put("sales", product.sales)
                    put("title", product.title)
                    put("url", product.url)
                }
                productsArray.put(productJson)
            }
            put("products", productsArray)
        }.toString()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("https://service.blackshellx.org/api/v1/getGshopArk")
            .post(body)
            .build()

        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                SyncUtils.runOnUiThread {
                    Toasts.error(context, "网络请求失败: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(context, "请求失败: ${response.code}")
                        }
                        return
                    }

                    val responseBody = response.body?.string()
                    if (responseBody.isNullOrEmpty()) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(context, "空响应")
                        }
                        return
                    }

                    val jsonResponse = JSONObject(responseBody)

                    if (jsonResponse.optInt("status") != 200) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(
                                context,
                                "API错误: ${jsonResponse.optString("msg")}"
                            )
                        }
                        return
                    }

                    // 提取ark字段
                    val arkObj = jsonResponse.optJSONObject("ark")
                    if (arkObj == null) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(context, "返回数据中未找到ark字段")
                        }
                        return
                    }

                    val arkJson = arkObj.toString()

                    SyncUtils.runOnUiThread {
                        showArkConfirmDialog(context, arkJson)
                    }

                } catch (e: Exception) {
                    SyncUtils.runOnUiThread {
                        Toasts.error(context, "解析响应时出错: ${e.message}")
                    }
                }
            }
        })
        }

    private fun getQzoneCard(context: Context) {
        val fixContext = CommonContextWrapper.createAppCompatContext(context)

        if (readPassFromFile(context) == null) {
            Toasts.popup("去找嘿壳获取授权码吧...\n你似乎需要进入这个群：\n1076550424")
            return
        }

        fun createEdit(hint: String, default: String): android.widget.EditText {
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

        val etTitle   = createEdit("标题", "邀请你抽取AI盲盒签")
        val etJumpUrl = createEdit("跳转链接", "https://h5.tu.qq.com/stable/daily-check-in/index?parent_trace_id=3a423341-4cec-174b-7d76-86d2a1508d66&current_channel=link&root_channel=qiandao&level=1&jump2App=1")
        val etPreview = createEdit("预览图链接", "https://shadow-h5-prd-1251316161.file.myqcloud.com/daily-check-in/regular_skin_v2/result/preview.png")
        val etPrompt  = createEdit("外显(prompt)", "邀请你抽取AI盲盒签")
        val etBtnText = createEdit("按钮文字", "立即抽取")

        val root = android.widget.LinearLayout(fixContext).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
            addView(labeledRow("标题",   etTitle))
            addView(labeledRow("跳转",   etJumpUrl))
            addView(labeledRow("预览图", etPreview))
            addView(labeledRow("外显",   etPrompt))
            addView(labeledRow("按钮",   etBtnText))
        }

        val dialog = android.app.AlertDialog.Builder(fixContext)
            .setTitle("QQ空间卡片")
            .setView(root)
            .setPositiveButton("发送", null)
            .setNegativeButton("取消", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title   = etTitle.text.toString().takeIf   { it.isNotEmpty() } ?: "邀请你抽取AI盲盒签"
                val jumpUrl = etJumpUrl.text.toString().takeIf { it.isNotEmpty() } ?: "https://h5.tu.qq.com/stable/daily-check-in/index?parent_trace_id=3a423341-4cec-174b-7d76-86d2a1508d66&current_channel=link&root_channel=qiandao&level=1&jump2App=1"
                val preview = etPreview.text.toString().takeIf { it.isNotEmpty() } ?: "https://shadow-h5-prd-1251316161.file.myqcloud.com/daily-check-in/regular_skin_v2/result/preview.png"
                val prompt  = etPrompt.text.toString().takeIf  { it.isNotEmpty() } ?: "邀请你抽取AI盲盒签"
                val btnText = etBtnText.text.toString().takeIf { it.isNotEmpty() } ?: "立即抽取"

                sendQzoneCardRequest(context, title, jumpUrl, preview, prompt, btnText)
                dialog.dismiss()
            }
        }

        dialog.show()
    }
    
        private fun sendQzoneCardRequest(
            context: Context,
            title: String,
            jumpUrl: String,
            preview: String,
            prompt: String,
            btnText: String
        ) {
            val password = readPassFromFile(context) ?: run {
                Toasts.popup("授权码无效")
                return
            }
    
            // 构造请求体
            val requestBody = mapOf(
                "password" to password,
                "title" to title,
                "jumpUrl" to jumpUrl,
                "preview" to preview,
                "prompt" to prompt,
                "btnText" to btnText
            )
    
            val json = JSONObject(requestBody).toString()
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = json.toRequestBody(mediaType)
    
            val request = Request.Builder()
                .url("https://service.blackshellx.org/api/v1/getQzonePB")
                .post(body)
                .build()
    
            val client = OkHttpClient()
    
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    SyncUtils.runOnUiThread {
                        Toasts.error(context, "网络请求失败: ${e.message}")
                    }
                }
    
                override fun onResponse(call: Call, response: Response) {
                    try {
                        if (!response.isSuccessful) {
                            SyncUtils.runOnUiThread {
                                Toasts.error(context, "请求失败: ${response.code}")
                            }
                            return
                        }
    
                        val responseBody = response.body?.string()
                        if (responseBody.isNullOrEmpty()) {
                            SyncUtils.runOnUiThread {
                                Toasts.error(context, "空响应")
                            }
                            return
                        }
    
                        val jsonResponse = JSONObject(responseBody)
    
                        // 检查返回状态
                        val status = if (jsonResponse.has("status")) {
                            jsonResponse.optInt("status")
                        } else if (jsonResponse.has("code")) {
                            jsonResponse.optInt("code")
                        } else {
                            500 // 默认错误状态
                        }
    
                        if (status != 200) {
                            val message = if (jsonResponse.has("msg")) {
                                jsonResponse.optString("msg")
                            } else if (jsonResponse.has("message")) {
                                jsonResponse.optString("message")
                            } else {
                                "未知错误"
                            }
    
                            SyncUtils.runOnUiThread {
                                Toasts.error(context, "API错误: $message")
                            }
                            return
                        }
    
                        // 提取cmd和data字段
                        val cmd = jsonResponse.optString("cmd")
                        val dataObj = jsonResponse.optJSONObject("data")
    
                        if (cmd.isEmpty() || dataObj == null) {
                            SyncUtils.runOnUiThread {
                                Toasts.error(context, "返回数据不完整")
                            }
                            return
                        }
    
                        // 发送Packet并等待响应
                        val dataJson = dataObj.toString()
                        sendPacketWithCallback(cmd, dataJson, context)
                    } catch (e: Exception) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(context, "解析响应时出错: ${e.message}")
                        }
                    }
                }
            })
            }
    private fun getQzoneCard2(context: Context) {
        val fixContext = CommonContextWrapper.createAppCompatContext(context)

        if (readPassFromFile(context) == null) {
            Toasts.popup("去找嘿壳获取授权码吧...\n你似乎需要进入这个群：\n1076550424")
            return
        }

        val defaultAvatar = "https://thirdqq.qlogo.cn/g?b=qq&nk=${QAppUtils.getCurrentUin()}&s=640"

        fun createEdit(hint: String, default: String): android.widget.EditText {
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

        val etTag     = createEdit("tag", "BlackShell Mod")
        val etJumpUrl = createEdit("跳转链接", "https://c.safaa.cn/bs/ybcard_default.html")
        val etPreview = createEdit("预览图链接", defaultAvatar)
        val etTagIcon = createEdit("tag图标", defaultAvatar)

        val root = android.widget.LinearLayout(fixContext).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
            addView(labeledRow("tag",  etTag))
            addView(labeledRow("跳转", etJumpUrl))
            addView(labeledRow("预览图", etPreview))
            addView(labeledRow("tag图标", etTagIcon))
        }

        val dialog = android.app.AlertDialog.Builder(fixContext)
            .setTitle("QQ空间视频卡片")
            .setView(root)
            .setPositiveButton("发送", null)
            .setNegativeButton("取消", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val tag     = etTag.text.toString().takeIf     { it.isNotEmpty() } ?: "BlackShell Mod"
                val jumpUrl = etJumpUrl.text.toString().takeIf { it.isNotEmpty() } ?: "https://c.safaa.cn/bs/ybcard_default.html"
                val preview = etPreview.text.toString().takeIf { it.isNotEmpty() } ?: defaultAvatar
                val tagIcon = etTagIcon.text.toString().takeIf { it.isNotEmpty() } ?: defaultAvatar

                sendQzoneCardRequest2(context, tag, jumpUrl, preview, tagIcon)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun sendQzoneCardRequest2(
        context: Context,
        tag: String,
        jumpUrl: String,
        preview: String,
        tagIcon: String,
    ) {
        val password = readPassFromFile(context) ?: run {
            Toasts.popup("去找嘿壳获取授权码吧...\n你似乎需要进入这个群：\n1076550424")
            return
        }

        // 构造请求体
        val requestBody = mapOf(
            "password" to password,
            "tag" to tag,
            "jumpUrl" to jumpUrl,
            "preview" to preview,
            "tagIcon" to tagIcon
        )

        val json = JSONObject(requestBody).toString()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("https://service.blackshellx.org/api/v1/getQzoneVideoPB")
            .post(body)
            .build()

        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                SyncUtils.runOnUiThread {
                    Toasts.error(context, "网络请求失败: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(context, "请求失败: ${response.code}")
                        }
                        return
                    }

                    val responseBody = response.body?.string()
                    if (responseBody.isNullOrEmpty()) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(context, "空响应")
                        }
                        return
                    }

                    val jsonResponse = JSONObject(responseBody)

                    // 检查返回状态
                    val status = if (jsonResponse.has("status")) {
                        jsonResponse.optInt("status")
                    } else if (jsonResponse.has("code")) {
                        jsonResponse.optInt("code")
                    } else {
                        500 // 默认错误状态
                    }

                    if (status != 200) {
                        val message = if (jsonResponse.has("msg")) {
                            jsonResponse.optString("msg")
                        } else if (jsonResponse.has("message")) {
                            jsonResponse.optString("message")
                        } else {
                            "未知错误"
                        }

                        SyncUtils.runOnUiThread {
                            Toasts.error(context, "API错误: $message")
                        }
                        return
                    }

                    // 提取cmd和data字段
                    val cmd = jsonResponse.optString("cmd")
                    val dataObj = jsonResponse.optJSONObject("data")

                    if (cmd.isEmpty() || dataObj == null) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(context, "返回数据不完整")
                        }
                        return
                    }

                    // 发送Packet并等待响应
                    val dataJson = dataObj.toString()
                    sendPacketWithCallback(cmd, dataJson, context)
                } catch (e: Exception) {
                    SyncUtils.runOnUiThread {
                        Toasts.error(context, "解析响应时出错: ${e.message}")
                    }
                }
            }
        })
    }
        
            private fun sendPacketWithCallback(cmd: String, json: String, context: Context) {
                // 设置临时响应处理器
                pendingResponseCmd = cmd
                pendingResponseCallback = { response ->
                    handleSendPacketResponse(response, context)
                }
                
                // 使用纯正的sendPacket方法发送数据包
                SyncUtils.runOnUiThread {
                    try {
                        sendPacket(cmd, json)
                    } catch (e: Exception) {
                        Toasts.error(context, "发送数据包失败: ${e.message}")
                        // 清理回调
                        pendingResponseCallback = null
                        pendingResponseCmd = null
                    }
                }
            }
        
            private fun handleSendPacketResponse(response: String?, context: Context) {
                SyncUtils.runOnUiThread {
                    try {
                        if (response.isNullOrEmpty()) {
                            Toasts.error(context, "发送数据包后没有收到响应")
                            return@runOnUiThread
                        }
        
                        val responseJson = JSONObject(response)
                        // 检查响应中是否有"3"字段，该字段包含ark数据
                        if (responseJson.has("3")) {
                            val arkDataString = responseJson.optString("3")
                            if (arkDataString.isNotEmpty()) {
                                // 解析ark数据字符串为JSON对象
                                val arkJsonObject = JSONObject(arkDataString)
        
                                // 检查arkJsonObject中是否有"arKMsg"字段
                                if (arkJsonObject.has("arKMsg")) {
                                    val arKMsg = arkJsonObject.optString("arKMsg")
                                    if (arKMsg.isNotEmpty()) {
                                        // arKMsg就是最终的Ark数据
                                        openPacketHelperAndSendArk(arKMsg, context)
                                    } else {
                                        Toasts.error(context, "返回的ark数据格式不正确，缺少arKMsg字段")
                                    }
                                } else {
                                    Toasts.error(context, "返回的ark数据格式不正确，缺少arKMsg字段")
                                }
                            } else {
                                Toasts.error(context, "返回的ark数据为空")
                            }
                        } else {
                            Toasts.error(context, "返回的响应中没有'3'字段")
                        }
                    } catch (e: Exception) {
                        Toasts.error(context, "处理SendPacket响应时出错: ${e.message}")
                    }
                }
            }
        
            private fun openPacketHelperAndSendArk(arkData: String, context: Context) {
                SyncUtils.runOnUiThread {
                    try {
                        // 自动打开PacketHelper
                        PacketHelperDialog.createView(null, context, arkData)
                        // 自动切换到ark发送模式
                        PacketHelperDialog.setSendTypeToArk()
                        // 等待100ms后自动点击发送
                        Handler(Looper.getMainLooper()).postDelayed({
                            PacketHelperDialog.performAutoSend(context)
                        }, 100)
                    } catch (e: Exception) {
                        Toasts.error(context, "打开PacketHelper时出错: ${e.message}")
                    }
                }
            }
        
            override fun entry(classLoader: ClassLoader) {
                // 添加这个实例到响应处理器列表中
                moe.ono.hooks.base.api.QQMsgRespHandler.handlers.add(this)
            }
        }