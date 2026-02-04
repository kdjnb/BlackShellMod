package moe.ono.hooks.item.entertainment

import android.os.Handler
import android.os.Looper
import com.google.protobuf.CodedOutputStream
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import de.robv.android.xposed.XC_MethodHook
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import moe.ono.R
import moe.ono.bridge.ntapi.MsgServiceHelper
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks._core.factory.HookItemFactory.getItem
import moe.ono.hooks.dispatcher.OnMenuBuilder
import moe.ono.reflex.Reflex
import moe.ono.util.AppRuntimeHelper
import moe.ono.util.ContextUtils
import moe.ono.util.CustomMenu
import moe.ono.util.Logger
import moe.ono.util.Session
import moe.ono.util.SyncUtils
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

@HookItem(
    path = "娱乐功能/链接复读",
    description = "长按文字消息生成特定格式的消息并发送\n\n* 长按文字消息菜单中使用"
)
class LinkRepeater : BaseSwitchFunctionHookItem(), OnMenuBuilder {

    override fun entry(classLoader: ClassLoader) {}

    override val targetTypes = arrayOf(
        "com.tencent.mobileqq.aio.msglist.holder.component.text.AIOTextContentComponent",
    )

    override fun onGetMenu(aioMsgItem: Any, targetType: String, param: XC_MethodHook.MethodHookParam) {
        if (!getItem(this.javaClass).isEnabled) return

        val item = CustomMenu.createItemIconNt(
            aioMsgItem,
            "链接复读",
            R.drawable.ic_baseline_free_breakfast_24,
            R.id.item_link_repeater
        ) {
            try {
                Logger.d("MENU_CLICK", "用户点击菜单")

                val msgId = Reflex.invokeVirtual(aioMsgItem, "getMsgId") as Long
                val ids = arrayListOf(msgId)

                AppRuntimeHelper.getAppRuntime()?.let {
                    MsgServiceHelper.getKernelMsgService(it)
                }?.getMsgsByMsgId(Session.getContact(), ids) { _, _, list ->
                    SyncUtils.runOnUiThread {
                        for (msgRecord in list) {
                            Logger.d("MSG_FETCHED", "成功获取消息")

                            val text = msgRecord.elements
                                ?.firstOrNull()
                                ?.textElement
                                ?.content ?: continue

                            val nick = msgRecord.sendNickName ?: "未知昵称"
                            val uin = msgRecord.senderUin

                            val msg = buildLinkRepeaterMessage(nick, uin, text)
                            Logger.d("BUILD_REPEAT_JSON", msg)

                            sendLinkRepeaterMessage(msg, msgRecord)
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e("MENU_ERROR", "菜单处理异常")
            }
            Unit
        }

        param.result = listOf(item) + param.result as List<*>    }

    private fun buildLinkRepeaterMessage(
        senderNick: String,
        senderUin: Long,
        text: String
    ): String {
        //嘿壳不要再逆向我了。。。。。
        return """{"1":{"1":"blackshellx.org","12":{"14":{"1":"$senderNick","2":"$text","3":"https://q1.qlogo.cn/g?b=qq&nk=$senderUin&s=640"}}}}"""
    }

    private fun sendLinkRepeaterMessage(message: String, msgRecord: MsgRecord) {
        try {
            // 获取当前Activity上下文
            val context = ContextUtils.getCurrentActivity()
            if (context != null) {
                // 打开PacketHelper对话框
                moe.ono.creator.PacketHelperDialog.createView(
                    context,
                    context,
                    message  // 初始内容
                )

                // 延迟设置内容和longmsg模式，确保对话框已创建
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        moe.ono.creator.PacketHelperDialog.setContentForLongmsg(message)
                    } catch (e: Exception) {
                        Logger.e("SET_CONTENT_ERROR", "设置内容失败: ${e.message}")
                    }
                }, 150) // 150ms延迟

                Logger.d("OPEN_PACKETHELPER", "已打开PacketHelper对话框")
            } else {
                Logger.d("CONTEXT_ERROR", "无法获取Activity上下文")
            }
        } catch (e: Exception) {
            Logger.e("SEND_ERROR", "打开PacketHelper失败")
        }
    }

    private fun compressData2(bytes: ByteArray): ByteArray {
        val def = Deflater()
        def.setInput(bytes)
        def.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!def.finished()) {
            out.write(buf, 0, def.deflate(buf))
        }
        def.end()
        return out.toByteArray()
    }

    private fun getUinFromUid(uid: String): Long =
        try {
            Class.forName("org.blackshellx.hook.bridge.ntapi.RelationNTUinAndUidApi")
                .getMethod("getUinFromUid", String::class.java)
                .invoke(null, uid) as Long
        } catch (e: Exception) {
            Logger.e("UID2UIN_ERROR", "uid=$uid")
            0L
        }

    private fun buildMessageLocal(json: String): ByteArray {
        val element = Json.Default.parseToJsonElement(json)
        return encodeMessageLocal(parseJsonToMapLocal(element))
    }

    private fun encodeMessageLocal(map: Map<Int, Any>): ByteArray =
        ByteArrayOutputStream().use {
            val out = CodedOutputStream.newInstance(it)
            encodeMapToProtobufLocal(out, map)
            out.flush()
            it.toByteArray()
        }

    private fun encodeMapToProtobufLocal(out: CodedOutputStream, map: Map<Int, Any>) {
        map.forEach { (k, v) ->
            when (v) {
                is Int -> out.writeInt32(k, v)
                is Long -> out.writeInt64(k, v)
                is String -> out.writeString(k, v)
                is ByteArray -> {
                    out.writeTag(k, 2)
                    out.writeUInt32NoTag(v.size)
                    out.writeRawBytes(v)
                }
                is Map<*, *> -> {
                    val child = encodeMessageLocal(v as Map<Int, Any>)
                    out.writeTag(k, 2)
                    out.writeUInt32NoTag(child.size)
                    out.writeRawBytes(child)
                }
                is List<*> -> v.forEach { if (it is Map<*, *>) encodeMapToProtobufLocal(out, it as Map<Int, Any>) }
            }
        }
    }

    private fun parseJsonToMapLocal(e: JsonElement): Map<Int, Any> {
        val map = mutableMapOf<Int, Any>()
        if (e is JsonObject) {
            e.forEach { (k, v) ->
                map[k.toInt()] = when (v) {
                    is JsonObject -> parseJsonToMapLocal(v)
                    is JsonPrimitive -> v.content
                    else -> v.toString()
                }
            }
        }
        return map
    }
}
