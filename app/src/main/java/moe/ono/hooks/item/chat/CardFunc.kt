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


@SuppressLint("DiscouragedApi")
@HookItem(
    path = "聊天与消息/卡片功能",
    description = "请勿使用此功能用作任何违法行为，作者概不负责。部分功能非开源，你无法通过任何方式从BlackShell Mod中获取任何一点关于未开源的卡片的线索"
)
class CardFunc : BaseSwitchFunctionHookItem(), IShortcutMenu {

    data class Product(
        val img: String,
        val ori_price: String,
        val price: String,
        val sales: String,
        val title: String,
        val url: String
    )

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

    override fun isAdd(): Boolean {
        return this.isEnabled
    }

    override val menuName: String
        get() = "卡片功能"

    override fun clickHandle(context: Context) {
        val fixContext = CommonContextWrapper.createAppCompatContext(context)
        val options = arrayOf("音卡（OIAPI）","*元宝卡","*千问卡","*商品卡")//, "方式二：还没写好"
        
        XPopup.Builder(fixContext)
            .asCenterList("选择卡片\n(带*的选项仅授权后可用)", options, OnSelectListener { position, text ->
                when (position) {
                    0 -> sendMusicCardByAPI(context)
//                    1 -> {
//                        sendPacket("LightAppSvc.mini_app_share.AdaptShareInfo", "{\"1\":13,\"2\":\"V1_AND_SQ_9.2.27_12160_YYB_D\",\"3\":\"i=cef2936225c19485249acfd710001e41a206&imsi=cef2936225c19485249acfd710001e41a206&mac=02:00:00:00:00:00&m=Redmi K30&o=12&a=31&sd=0&c64=1&sc=1&p=1080*2261&aid=cef2936225c19485249acfd710001e41a206&f=Xiaomi&mm=7568&cf=1762&cc=8&qimei=00227d2850bf18355bb57799ccb04ece8b41a2064799b7c7&qimei36=cef2936225c19485249acfd710001e41a206&sharpP=0&n=wifi&support_xsj_live=true&client_mod=default\",\"4\":{\"2\":\"1109937557\",\"3\":\"哔哩哔哩\",\"4\":\"嘿壳嘿壳嘿壳嘿入bili\",\"5\":1770427251,\"6\":3,\"7\":1,\"8\":0,\"9\":{},\"11\":\"pages/video/video?bvid=BV1McmDBrEEm&share_source=qq_ugc&unique_k=d6N2im4\",\"13\":3,\"14\":0,\"16\":0,\"17\":\"https://b23.tv/d6N2im4?share_medium=android&share_source=qq&bbid=XY61C0F607264FC6318A92B9E13C65DB7CD3C&ts=1770427250798\",\"18\":{\"6\":3906651886253060144},\"22\":0},\"5\":\"3623383556_0207092051621_96202\"}")
//                    }
                    1 -> {
                        yuanbaoCard(context)
                    }
                    2 -> {
                        QianwenCard(context)
                    }
                    3 -> {
                        getGshopCard(context)
                    }
                }
            })
            .show()
    }

    private fun sendMusicCardByAPI(context: Context) {
        val fixContext = CommonContextWrapper.createAppCompatContext(context)
        
        // 第一步：获取音乐链接
        XPopup.Builder(fixContext)
            .asInputConfirm(
                "发送音乐卡片",
                "请输入歌曲链接",
                "",
                object : OnInputConfirmListener {
                    override fun onConfirm(musicUrl: String?) {
                        if (musicUrl.isNullOrEmpty()) {
                            Toasts.error(context, "请输入歌曲链接")
                            return
                        }

                        // 第二步：获取歌名
                        XPopup.Builder(fixContext)
                            .asInputConfirm(
                                "发送音乐卡片",
                                "请输入歌名",
                                "",
                                object : OnInputConfirmListener {
                                    override fun onConfirm(songName: String?) {
                                        if (songName.isNullOrEmpty()) {
                                            Toasts.error(context, "请输入歌名")
                                            return
                                        }

                                        // 第三步：获取歌手
                                        XPopup.Builder(fixContext)
                                            .asInputConfirm(
                                                "发送音乐卡片",
                                                "请输入歌手",
                                                "",
                                                object : OnInputConfirmListener {
                                                    override fun onConfirm(singer: String?) {
                                                        if (singer.isNullOrEmpty()) {
                                                            Toasts.error(context, "请输入歌手")
                                                            return
                                                        }

                                                        // 第四步：获取封面URL
                                                        XPopup.Builder(fixContext)
                                                            .asInputConfirm(
                                                                "发送音乐卡片",
                                                                "请输入封面URL",
                                                                "",
                                                                object : OnInputConfirmListener {
                                                                    override fun onConfirm(coverUrl: String?) {
                                                                        if (coverUrl.isNullOrEmpty()) {
                                                                            Toasts.error(context, "请输入封面URL")
                                                                            return
                                                                        }

                                                                        // 第五步：获取跳转链接
                                                                        XPopup.Builder(fixContext)
                                                                            .asInputConfirm(
                                                                                "发送音乐卡片",
                                                                                "请输入跳转链接",
                                                                                "",
                                                                                object : OnInputConfirmListener {
                                                                                    override fun onConfirm(jumpUrl: String?) {
                                                                                        if (jumpUrl.isNullOrEmpty()) {
                                                                                            Toasts.error(context, "请输入跳转链接")
                                                                                            return
                                                                                        }

                                                                                        // 第六步：选择格式
                                                                                        val formats = arrayOf("qq", "163", "kugou", "kuwo", "migu", "mihoyo", "kugoulite", "bodian", "baidu", "miui", "kuan", "qidianskland", "bilibili")
                                                                                        XPopup.Builder(fixContext)
                                                                                            .asCenterList("选择格式", formats, OnSelectListener { position, format ->
                                                                                                // 构造请求参数
                                                                                                val requestBody = mapOf(
                                                                                                    "url" to musicUrl,
                                                                                                    "song" to songName,
                                                                                                    "singer" to singer,
                                                                                                    "cover" to coverUrl,
                                                                                                    "jump" to jumpUrl,
                                                                                                    "format" to format
                                                                                                )

                                                                                                // 发送POST请求到API
                                                                                                postRequestToAPI(context, requestBody)
                                                                                            })
                                                                                            .show()
                                                                                    }
                                                                                })
                                                                            .show()
                                                                    }
                                                                })
                                                            .show()
                                                    }
                                                })
                                            .show()
                                    }
                                })
                            .show()
                    }
                })
            .show()
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
                    Toasts.error(context, "hacker确实不能让你用")
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
        var peerid: String = getCurrentPeerID()
        var rqunuin: String
        var rtitle: String
        var rdesc: String
        var rcoverUrl: String
        var rjumpUrl: String
        var rver: String
        if(readPassFromFile(context)==null){
            Toasts.error(context, "hacker不让你用")
            return
        }

        var rpeerid: String = peerid.takeIf { it.isNotEmpty() } ?: "1076550424"

        // 第一步：获取音乐链接
        XPopup.Builder(fixContext)
            .asInputConfirm(
                "发送元宝卡片",
                "请输入发送群号",
                rpeerid,
                object : OnInputConfirmListener {
                    override fun onConfirm(qunuin: String?) {
//                        if (qunuin.isNullOrEmpty()) {
//                            rqunuin=
//                        }
                        rqunuin = qunuin?.takeIf { it.isNotEmpty() } ?: rpeerid

                        // 第二步：获取标题
                        XPopup.Builder(fixContext)
                            .asInputConfirm(
                                "发送元宝卡片",
                                "标题",
                                "BlackShell Mod",
                                object : OnInputConfirmListener {
                                    override fun onConfirm(title: String?) {
                                        rtitle = title?.takeIf { it.isNotEmpty() } ?: "BlackShell Mod"


                                        // 第三步：获取歌手
                                        XPopup.Builder(fixContext)
                                            .asInputConfirm(
                                                "发送元宝卡片",
                                                "请输入简介",
                                                "元宝已被BlackShellX.org严肃嘿入",
                                                object : OnInputConfirmListener {
                                                    override fun onConfirm(desc: String?) {
                                                        rdesc = desc?.takeIf { it.isNotEmpty() } ?: "元宝已被BlackShellX.org严肃嘿入"


                                                        // 第四步：获取封面URL
                                                        XPopup.Builder(fixContext)
                                                            .asInputConfirm(
                                                                "发送元宝卡片",
                                                                "请输入封面URL",
                                                                "https://p.qlogo.cn/gdynamic/MIwbbVhjoVzDgAsUTNsD0CtU5WJCz3gnHibZicw4YmISI/0",
                                                                object : OnInputConfirmListener {
                                                                    override fun onConfirm(coverUrl: String?) {
                                                                        rcoverUrl = coverUrl?.takeIf { it.isNotEmpty() } ?: "https://p.qlogo.cn/gdynamic/MIwbbVhjoVzDgAsUTNsD0CtU5WJCz3gnHibZicw4YmISI/0"


                                                                        // 第五步：获取跳转链接
                                                                        XPopup.Builder(fixContext)
                                                                            .asInputConfirm(
                                                                                "发送元宝卡片",
                                                                                "请输入跳转链接",
                                                                                "https://c.safaa.cn/bs/ybcard_default.html",
                                                                                object : OnInputConfirmListener {
                                                                                    override fun onConfirm(jumpUrl: String?) {
                                                                                        rjumpUrl = jumpUrl?.takeIf { it.isNotEmpty() } ?: "https://c.safaa.cn/bs/ybcard_default.html"
                                                                                        XPopup.Builder(fixContext)
                                                                                            .asInputConfirm(
                                                                                                "发送元宝卡片",
                                                                                                "*请输入ver\n填你自己版本",
                                                                                                "9.2.27",
                                                                                                object : OnInputConfirmListener {
                                                                                                    override fun onConfirm(ver: String?) {
                                                                                                        rver = ver?.takeIf { it.isNotEmpty() } ?: "9.2.27"
                                                                                                        val pass = readPassFromFile(fixContext)
                                                                                                        if (pass.isNullOrEmpty()) {
                                                                                                            return
                                                                                                        }

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


                                                                                                    }
                                                                                                })
                                                                                            .show()


                                                                                    }
                                                                                })
                                                                            .show()
                                                                    }
                                                                })
                                                            .show()
                                                    }
                                                })
                                            .show()
                                    }
                                })
                            .show()
                    }
                })
            .show()

    }
    private fun QianwenCard(context: Context) {
        val fixContext = CommonContextWrapper.createAppCompatContext(context)
        var peerid: String = getCurrentPeerID()
        var rqunuin: String
        var rtitle: String
        var rdesc: String
        var rcoverUrl: String
        var rjumpUrl: String
        var rver: String
        if(readPassFromFile(context)==null){
            Toasts.error(context, "hacker不让你用")
            return
        }

        var rpeerid: String = peerid.takeIf { it.isNotEmpty() } ?: "1076550424"

        // 第一步：获取音乐链接
        XPopup.Builder(fixContext)
            .asInputConfirm(
                "发送千问卡片",
                "请输入发送群号",
                rpeerid,
                object : OnInputConfirmListener {
                    override fun onConfirm(qunuin: String?) {
//                        if (qunuin.isNullOrEmpty()) {
//                            rqunuin=
//                        }
                        rqunuin = qunuin?.takeIf { it.isNotEmpty() } ?: rpeerid

                        // 第二步：获取标题
                        XPopup.Builder(fixContext)
                            .asInputConfirm(
                                "发送千问卡片",
                                "标题",
                                "BlackShell Mod",
                                object : OnInputConfirmListener {
                                    override fun onConfirm(title: String?) {
                                        rtitle = title?.takeIf { it.isNotEmpty() } ?: "BlackShell Mod"


                                        // 第三步：获取歌手
                                        XPopup.Builder(fixContext)
                                            .asInputConfirm(
                                                "发送千问卡片",
                                                "请输入简介",
                                                "千问已被BlackShellX.org严肃嘿入",
                                                object : OnInputConfirmListener {
                                                    override fun onConfirm(desc: String?) {
                                                        rdesc = desc?.takeIf { it.isNotEmpty() } ?: "千问已被BlackShellX.org严肃嘿入"


                                                        // 第四步：获取封面URL
                                                        XPopup.Builder(fixContext)
                                                            .asInputConfirm(
                                                                "发送千问卡片",
                                                                "请输入封面URL",
                                                                "https://p.qlogo.cn/gdynamic/MIwbbVhjoVzDgAsUTNsD0CtU5WJCz3gnHibZicw4YmISI/0",
                                                                object : OnInputConfirmListener {
                                                                    override fun onConfirm(coverUrl: String?) {
                                                                        rcoverUrl = coverUrl?.takeIf { it.isNotEmpty() } ?: "https://p.qlogo.cn/gdynamic/MIwbbVhjoVzDgAsUTNsD0CtU5WJCz3gnHibZicw4YmISI/0"


                                                                        // 第五步：获取跳转链接
                                                                        XPopup.Builder(fixContext)
                                                                            .asInputConfirm(
                                                                                "发送千问卡片",
                                                                                "请输入跳转链接",
                                                                                "https://c.safaa.cn/bs/ybcard_default.html",
                                                                                object : OnInputConfirmListener {
                                                                                    override fun onConfirm(jumpUrl: String?) {
                                                                                        rjumpUrl = jumpUrl?.takeIf { it.isNotEmpty() } ?: "https://c.safaa.cn/bs/ybcard_default.html"
                                                                                        XPopup.Builder(fixContext)
                                                                                            .asInputConfirm(
                                                                                                "发送千问卡片",
                                                                                                "*请输入ver\n填你自己版本",
                                                                                                "9.2.27",
                                                                                                object : OnInputConfirmListener {
                                                                                                    override fun onConfirm(ver: String?) {
                                                                                                        rver = ver?.takeIf { it.isNotEmpty() } ?: "9.2.27"
                                                                                                        val pass = readPassFromFile(fixContext)
                                                                                                        if (pass.isNullOrEmpty()) {
                                                                                                            return
                                                                                                        }

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


                                                                                                    }
                                                                                                })
                                                                                            .show()


                                                                                    }
                                                                                })
                                                                            .show()
                                                                    }
                                                                })
                                                            .show()
                                                    }
                                                })
                                            .show()
                                    }
                                })
                            .show()
                    }
                })
            .show()

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
                                PacketHelperDialog.performAutoSend()
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

    private fun getGshopCard(context: Context) {
        val fixContext = CommonContextWrapper.createAppCompatContext(context)
        
        if (readPassFromFile(context) == null) {
            Toasts.error(context, "hacker不让你用")
            return
        }

        // 首先获取按钮文字和标题
        XPopup.Builder(fixContext)
            .asInputConfirm(
                "商品卡片",
                "按钮文字",
                "大嘿壳",
                object : OnInputConfirmListener {
                    override fun onConfirm(btnText: String?) {
                        val finalBtnText = btnText?.takeIf { it.isNotEmpty() } ?: "大嘿壳"
                        
                        XPopup.Builder(fixContext)
                            .asInputConfirm(
                                "商品卡片", 
                                "外显",
                                "嘿壳群主",
                                object : OnInputConfirmListener {
                                    override fun onConfirm(prompt: String?) {
                                        val finalPrompt = prompt?.takeIf { it.isNotEmpty() } ?: "嘿壳群主"
                                        
                                        // 获取商品数量
                                        XPopup.Builder(fixContext)
                                            .asInputConfirm(
                                                "商品卡片",
                                                "请输入商品数量",
                                                "1",
                                                object : OnInputConfirmListener {
                                                    override fun onConfirm(numStr: String?) {
                                                        val num = try {
                                                            numStr?.toInt()?.coerceAtLeast(1) ?: 1
                                                        } catch (e: NumberFormatException) {
                                                            1
                                                        }
                                                        
                                                        // 开始收集商品信息
                                                        collectProductsInfo(fixContext, finalBtnText, finalPrompt, num, 0, mutableListOf())
                                                    }
                                                })
                                            .show()
                                    }
                                })
                            .show()
                    }
                })
            .show()
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
            // 所有商品信息收集完成，发送请求
            sendGshopCardRequest(context, readPassFromFile(context)!!, btnText, prompt, products)
            return
        }

        val fixContext = CommonContextWrapper.createAppCompatContext(context)
        
        // 步骤1: 获取商品图片链接
        XPopup.Builder(fixContext)
            .asInputConfirm(
                "第${currentIndex + 1}个商品", 
                "商品图片链接", 
                products.getOrNull(currentIndex)?.img ?: "https://gchat.qpic.cn/gchatpic_new/0/0-0-1E29EE09B8A0323A99F35A2A3275655F/0?term=2&is_origin=1"
            ) { img ->
                val finalImg = img.takeIf { it.isNotEmpty() } ?: "https://gchat.qpic.cn/gchatpic_new/0/0-0-1E29EE09B8A0323A99F35A2A3275655F/0?term=2&is_origin=1"
                
                if (finalImg.isEmpty()) {
                    Toasts.error(context, "商品图片链接不能为空")
                    return@asInputConfirm
                }
                
                // 步骤2: 获取原价
                XPopup.Builder(fixContext)
                    .asInputConfirm(
                        "第${currentIndex + 1}个商品", 
                        "商品原价", 
                        products.getOrNull(currentIndex)?.ori_price ?: ""
                    ) { oriPrice ->
                        val finalOriPrice = oriPrice.takeIf { it.isNotEmpty() } ?: ""
                        
                        if (finalOriPrice.isEmpty()) {
                            Toasts.error(context, "商品原价不能为空")
                            return@asInputConfirm
                        }
                        
                        // 步骤3: 获取现价
                        XPopup.Builder(fixContext)
                            .asInputConfirm(
                                "第${currentIndex + 1}个商品", 
                                "商品现价", 
                                products.getOrNull(currentIndex)?.price ?: ""
                            ) { price ->
                                val finalPrice = price.takeIf { it.isNotEmpty() } ?: ""
                                
                                if (finalPrice.isEmpty()) {
                                    Toasts.error(context, "商品现价不能为空")
                                    return@asInputConfirm
                                }
                                
                                // 步骤4: 获取销量
                                XPopup.Builder(fixContext)
                                    .asInputConfirm(
                                        "第${currentIndex + 1}个商品", 
                                        "销量", 
                                        products.getOrNull(currentIndex)?.sales ?: ""
                                    ) { sales ->
                                        val finalSales = sales.takeIf { it.isNotEmpty() } ?: ""
                                        
                                        if (finalSales.isEmpty()) {
                                            Toasts.error(context, "销量不能为空")
                                            return@asInputConfirm
                                        }
                                        
                                        // 步骤5: 获取商品名称
                                        XPopup.Builder(fixContext)
                                            .asInputConfirm(
                                                "第${currentIndex + 1}个商品", 
                                                "商品名称", 
                                                products.getOrNull(currentIndex)?.title ?: ""
                                            ) { title ->
                                                val finalTitle = title.takeIf { it.isNotEmpty() } ?: ""
                                                
                                                if (finalTitle.isEmpty()) {
                                                    Toasts.error(context, "商品名称不能为空")
                                                    return@asInputConfirm
                                                }
                                                
                                                // 步骤6: 获取商品链接
                                                XPopup.Builder(fixContext)
                                                    .asInputConfirm(
                                                        "第${currentIndex + 1}个商品", 
                                                        "商品链接", 
                                                        products.getOrNull(currentIndex)?.url ?: ""
                                                    ) { url ->
                                                        val finalUrl = url.takeIf { it.isNotEmpty() } ?: ""
                                                        
                                                        if (finalUrl.isEmpty()) {
                                                            Toasts.error(context, "商品链接不能为空")
                                                            return@asInputConfirm
                                                        }
                                                        
                                                        // 所有字段都不为空，创建Product对象
                                                        val product = Product(
                                                            finalImg, 
                                                            finalOriPrice, 
                                                            finalPrice, 
                                                            finalSales, 
                                                            finalTitle, 
                                                            finalUrl
                                                        )
                                                        products.add(product)
                                                        
                                                        // 继续收集下一个商品
                                                        collectProductsInfo(
                                                            context, 
                                                            btnText, 
                                                            prompt, 
                                                            totalNum, 
                                                            currentIndex + 1, 
                                                            products
                                                        )
                                                    }.show()
                                            }.show()
                                    }.show()
                            }.show()
                    }.show()
            }.show()
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

                    // 在主线程中处理返回的ark数据
                    SyncUtils.runOnUiThread {
                        // 自动打开PacketHelper
                        PacketHelperDialog.createView(null, context, arkJson)
                        // 自动切换到ark发送模式
                        PacketHelperDialog.setSendTypeToArk()
                        // 等待100ms后自动点击发送
                        Handler(Looper.getMainLooper()).postDelayed({
                            PacketHelperDialog.performAutoSend()
                        }, 100)
                    }

                } catch (e: Exception) {
                    SyncUtils.runOnUiThread {
                        Toasts.error(context, "解析响应时出错: ${e.message}")
                    }
                }
            }
        })
    }





    override fun entry(classLoader: ClassLoader) {
        // 不需要实现任何hook逻辑，因为这是一个快捷菜单项
        // 功能通过点击菜单触发，而不是通过hook实现
    }
}