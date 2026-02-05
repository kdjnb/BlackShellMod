package moe.ono.hooks.item.chat

import android.annotation.SuppressLint
import android.content.Context
import com.lxj.xpopup.XPopup
import com.lxj.xpopup.interfaces.OnInputConfirmListener
import com.tencent.qqnt.kernel.nativeinterface.*
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import moe.ono.R
import moe.ono.bridge.ntapi.ChatTypeConstants
import moe.ono.bridge.ntapi.MsgServiceHelper
import moe.ono.creator.PacketHelperDialog
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks._core.factory.HookItemFactory.getItem
import moe.ono.hooks.dispatcher.OnMenuBuilder
import moe.ono.hooks.protocol.sendMessage
import moe.ono.reflex.Reflex
import moe.ono.reflex.invoke
import moe.ono.ui.CommonContextWrapper
import moe.ono.util.*
import android.os.Handler
import android.os.Looper

@SuppressLint("DiscouragedApi")
@HookItem(
    path = "聊天与消息/一键转超级QQ秀表情",
    description = "长按图片或表情 → 转超表"
)
class SuperQQShowConverter : BaseSwitchFunctionHookItem(), OnMenuBuilder {

    override fun entry(classLoader: ClassLoader) {
        // Hook implementation will go here when needed
    }

    // Define target types for the hook
    override val targetTypes = arrayOf(
        "com.tencent.mobileqq.aio.msglist.holder.component.pic.AIOPicContentComponent",
        "com.tencent.mobileqq.aio.msglist.holder.component.mix.AIOMixContentComponent",
        "com.tencent.mobileqq.aio.msglist.holder.component.marketface.AIOMarketFaceComponent"
    )

    override fun onGetMenu(aioMsgItem: Any, targetType: String, param: XC_MethodHook.MethodHookParam) {
        // Check if the hook is enabled
        if (!getItem(this.javaClass).isEnabled) return

        // Extract the full message data from the message
        val msgData = extractMsgDataFromMsg(aioMsgItem)
        
        if (msgData != null) {
            // Create a menu item for converting to Super QQ Show
            val item = CustomMenu.createItemIconNt(
                aioMsgItem,
                "转超表",
                R.drawable.ic_sticker, // Use an existing icon
                R.id.item_super_qq_show_convert
            ) {
                Logger.i("SuperQQShowConverter", "用户点击转超表菜单")
                // Show input dialog to get face name - need to get context from the aioMsgItem
                val context = try {
                    // Try to get context from the aioMsgItem
                    aioMsgItem.invoke("getContext") as? Context
                } catch (e: Exception) {
                    Logger.e("SuperQQShowConverter", "Failed to get context from aioMsgItem: ${e.message}")
                    null
                }
                
                if (context != null) {
                    showFaceNameInputDialog(context, msgData)
                } else {
                    Logger.e("SuperQQShowConverter", "无法获取context")
                }
            }

            // Add the item to the existing menu
            param.result = listOf(item) + param.result as List<*>
        }
    }

    /**
     * Show input dialog to get face name from user
     */
    private fun showFaceNameInputDialog(context: Context, msgData: Map<*, *>) {
        val fixContext = CommonContextWrapper.createAppCompatContext(context)
        
        XPopup.Builder(fixContext)
            .asInputConfirm(
                "转超级QQ秀表情",
                "请输入表情名称",
                "嘿壳表情", // 默认值
                object : OnInputConfirmListener {
                    override fun onConfirm(faceName: String?) {
                        val finalFaceName = if (faceName.isNullOrEmpty()) "嘿壳表情" else faceName
                        Logger.i("SuperQQShowConverter", "用户输入表情名称: $finalFaceName")
                        
                        // Extract d1, d2, d3 from the original message
                        val d1 = extractD1FromMsg(msgData)
                        val d2 = extractD2FromMsg(msgData)
                        val d3 = extractD3FromMsg(msgData)
                        
                        Logger.i("SuperQQShowConverter", "提取到d1: $d1, d2: $d2, d3: $d3")
                        
                        if (d1 != null && d2 != null && d3 != null) {
                            // Build the template JSON with extracted values
                            val newJson = buildTemplateJson(d1, d2, d3, finalFaceName)
                            
                            // Call PacketHelper to fill in the JSON and send
                            PacketHelperDialog.createView(null, context, newJson)
                            
                            // Wait 100ms then auto-send
                            Handler(Looper.getMainLooper()).postDelayed({
                                PacketHelperDialog.performAutoSend()
                            }, 100)
                        } else {
                            Logger.e("SuperQQShowConverter", "无法提取完整的d1, d2, d3数据")
                        }
                    }
                }
            )
            .show()
    }

    /**
     * Extract the full message data from the message object
     */
    private fun extractMsgDataFromMsg(msgRecord: Any): Map<*, *>? {
        return runCatching {
            // Try to get the message body as a map
            val body = msgRecord.invoke("getMsgBody")
                ?: msgRecord.invoke("getRawMsg")
                ?: msgRecord.invoke("getMsgBodyMap")

            if (body is Map<*, *>) {
                body
            } else {
                // Try extracting from elements if body is not a map
                val elements = msgRecord.invoke("getElements") as? List<*>
                if (!elements.isNullOrEmpty()) {
                    // For elements approach, we need to convert to a compatible format
                    // This is a simplified approach - in practice you might need more complex conversion
                    null
                } else {
                    null
                }
            }
        }.onFailure {
            Logger.e("SuperQQShowConverter", it)
        }.getOrNull()
    }

    /**
     * Extract d1 from the message data
     * d1 = "6"."3"."1"."2"."53"."2"."1"."1"."1"."2" 
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractD1FromMsg(msg: Map<*, *>): String? {
        try {
            val section6 = msg[6] as? Map<*, *>
            val section3 = section6?.get(3) as? Map<*, *>
            val section1 = section3?.get(1) as? Map<*, *>
            val list2 = section1?.get(2) as? List<*>
            
            list2?.forEach { elem ->
                if (elem is Map<*, *>) {
                    // Look for the 53 element in the list
                    elem.entries.forEach { entry ->
                        val key = entry.key
                        if (key == 53) {
                            val value53 = entry.value as? Map<*, *>
                            val section2 = value53?.get(2) as? Map<*, *>
                            val section1Inner = section2?.get(1) as? Map<*, *>
                            val section1Double = section1Inner?.get(1) as? Map<*, *>
                            val section1Triple = section1Double?.get(1) as? Map<*, *>
                            val d1Value = section1Triple?.get(2) as? String
                            if (d1Value != null) {
                                return d1Value
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("SuperQQShowConverter", "Error extracting d1: ${e.message}")
        }

        return null
    }

    /**
     * Extract d2 from the message data
     * d2 = "6"."3"."1"."2"."53"."2"."1"."1"."1"."4"
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractD2FromMsg(msg: Map<*, *>): String? {
        try {
            val section6 = msg[6] as? Map<*, *>
            val section3 = section6?.get(3) as? Map<*, *>
            val section1 = section3?.get(1) as? Map<*, *>
            val list2 = section1?.get(2) as? List<*>
            
            list2?.forEach { elem ->
                if (elem is Map<*, *>) {
                    // Look for the 53 element in the list
                    elem.entries.forEach { entry ->
                        val key = entry.key
                        if (key == 53) {
                            val value53 = entry.value as? Map<*, *>
                            val section2 = value53?.get(2) as? Map<*, *>
                            val section1Inner = section2?.get(1) as? Map<*, *>
                            val section1Double = section1Inner?.get(1) as? Map<*, *>
                            val section1Triple = section1Double?.get(1) as? Map<*, *>
                            val d2Value = section1Triple?.get(4) as? String
                            if (d2Value != null) {
                                return d2Value
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("SuperQQShowConverter", "Error extracting d2: ${e.message}")
        }

        return null
    }

    /**
     * Extract d3 from the message data
     * d3 = "6"."3"."1"."2"."53"."2"."1"."1"."2"
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractD3FromMsg(msg: Map<*, *>): String? {
        try {
            val section6 = msg[6] as? Map<*, *>
            val section3 = section6?.get(3) as? Map<*, *>
            val section1 = section3?.get(1) as? Map<*, *>
            val list2 = section1?.get(2) as? List<*>
            
            list2?.forEach { elem ->
                if (elem is Map<*, *>) {
                    // Look for the 53 element in the list
                    elem.entries.forEach { entry ->
                        val key = entry.key
                        if (key == 53) {
                            val value53 = entry.value as? Map<*, *>
                            val section2 = value53?.get(2) as? Map<*, *>
                            val section1Inner = section2?.get(1) as? Map<*, *>
                            val section1Double = section1Inner?.get(1) as? Map<*, *>
                            val d3Value = section1Double?.get(2) as? String
                            if (d3Value != null) {
                                return d3Value
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("SuperQQShowConverter", "Error extracting d3: ${e.message}")
        }

        return null
    }

    /**
     * Build the template JSON with extracted values
     */
    private fun buildTemplateJson(d1: String, d2: String, d3: String, faceName: String): String {
        return """[
    {
        "37": {
            "16": 0,
            "17": 161824,
            "1": 19,
            "19": {
                "96": 0,
                "65": {
                    "1": 1,
                    "2": 20
                },
                "66": 33554560,
                "34": 2000,
                "4": 10315,
                "71": 3,
                "72": 0,
                "73": {
                    "1": 45,
                    "2": 0,
                    "3": 113,
                    "6": 5,
                    "7": 2
                },
                "41": 0,
                "107": 828,
                "79": 131136,
                "15": 161494,
                "80": 37,
                "81": 16,
                "51": 339,
                "116": -444136893304753334,
                "52": 8,
                "54": 1,
                "55": 1,
                "56": 0,
                "25": 0,
                "90": {
                    "3": [
                        {
                            "1": 0,
                            "2": "u_"
                        }
                    ]
                },
                "58": 0,
                "30": 0,
                "31": 0
            },
            "6": 2,
            "7": "aQoAJ330Au8x79xu3tAI4Ntf0clrbrn66Fux3TbeiYZp6YAiHKgGu+VjtVuHsKjA",
            "12": 1
        }
    },
    {
        "9": {
            "1": 2141485
        }
    },
    {
        "53": {
            "1": 48,
            "2": {
                "1": {
                    "1": {
                        "1": {
                            "1": 618785,
                            "2": "$d1",
                            "3": "11314e9ab6d9233bb5a78fc4e6b22a728f817b1c",
                            "4": "$d2",
                            "5": {
                                "1": 1,
                                "2": 2000,
                                "3": 0,
                                "4": 0
                            },
                            "6": 360,
                            "7": 360,
                            "8": 0,
                            "9": 0
                        },
                        "2": "$d3",
                        "3": 1,
                        "4": 1766841635,
                        "5": 2678400,
                        "6": 0
                    },
                    "2": {
                        "1": "/download?appid=1407&fileid=$d3",
                        "2": {
                            "1": "&spec=0",
                            "2": "&spec=720",
                            "3": "&spec=198"
                        },
                        "3": "multimedia.nt.qq.com.cn"
                    },
                    "5": 0,
                    "6": {
                        "2": "hex->E6417C37C58CD6208F835F631E931730EE1A596C"
                    }
                },
                "2": {
                    "1": {
                        "1": 1,
                        "2": "[$faceName]",
                        "1001": 2,
                        "1002": 2,
                        "1003": 3712448771,
                        "12": {
                            "1": 1,
                            "34": 0,
                            "18": {},
                            "19": {},
                            "3": 0,
                            "4": 0,
                            "21": {
                                "1": 6740,
                                "2": "$faceName",
                                "3": 1,
                                "4": 100,
                                "5": 0,
                                "7": {}
                            },
                            "9": "[$faceName]",
                            "10": 0,
                            "12": {},
                            "30": "&rkey=CAISONPsN0nSR8aLWIQhoe7Q9E7fpVD5-0VvOwO-C40DlC_G71a_cWCblgd4HvLFkvF1TH6BxoPvEmrz",
                            "31": {}
                        }
                    },
                    "2": {
                        "3": {}
                    },
                    "3": {
                        "11": {},
                        "12": {}
                    },
                    "10": 0
                }
            },
            "3": 20
        }
    },
    {
        "16": {
            "1": "x",
            "3": 1,
            "4": 8,
            "7": {}
        }
    }
]"""
    }
}