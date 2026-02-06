package moe.ono.hooks.item.chat

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.lxj.xpopup.XPopup
import com.lxj.xpopup.interfaces.OnInputConfirmListener
import com.lxj.xpopup.interfaces.OnSelectListener
import moe.ono.creator.PacketHelperDialog
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks.base.util.Toasts
import moe.ono.loader.hookapi.IShortcutMenu
import moe.ono.ui.CommonContextWrapper
import moe.ono.util.SyncUtils
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

@SuppressLint("DiscouragedApi")
@HookItem(
    path = "聊天与消息/发送音乐卡片",
    description = "可选两种方式，请勿使用此功能用作任何违法行为，作者概不负责。"
)
class SendMusicCard : BaseSwitchFunctionHookItem(), IShortcutMenu {

    override fun isAdd(): Boolean {
        return this.isEnabled
    }

    override val menuName: String
        get() = "发送音乐卡片"

    override fun clickHandle(context: Context) {
        val fixContext = CommonContextWrapper.createAppCompatContext(context)
        val options = arrayOf("方式一：通过API发送音乐卡片", "方式二：还没写好")
        
        XPopup.Builder(fixContext)
            .asCenterList("选择发送音乐卡片方式", options, OnSelectListener { position, text ->
                when (position) {
                    0 -> sendMusicCardByAPI(context)
                    1 -> {
                        Toasts.info(context, "还没写好")
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
                    val data = jsonResponse.optJSONObject("data")?.toString()
                    if (data.isNullOrEmpty()) {
                        SyncUtils.runOnUiThread {
                            Toasts.error(context, "未找到data字段")
                        }
                        return
                    }

                    // 在UI线程中执行后续操作
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

    override fun entry(classLoader: ClassLoader) {
        // 不需要实现任何hook逻辑，因为这是一个快捷菜单项
        // 功能通过点击菜单触发，而不是通过hook实现
    }
}