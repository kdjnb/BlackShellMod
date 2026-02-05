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
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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
        
        writeDebugLog("提取msgData结果: ${msgData != null}")
        
        // Create a menu item for converting to Super QQ Show regardless of whether msgData is null
        // If msgData is null, we'll still show the menu but handle it gracefully in the click handler
        val item = CustomMenu.createItemIconNt(
            aioMsgItem,
            "转超表",
            R.drawable.ic_sticker, // Use an existing icon
            R.id.item_super_qq_show_convert
        ) {
            Logger.i("SuperQQShowConverter", "用户点击转超表菜单")
            // Log detailed info for debugging
            writeDebugLog("用户点击转超表菜单, msgData: ${msgData != null}")
            
            // Show input dialog to get face name - need to get context from the aioMsgItem
            var context: Context? = null
            try {
                // Try to get context from the aioMsgItem
                context = aioMsgItem.invoke("getContext") as? Context
            } catch (e: Exception) {
                Logger.e("SuperQQShowConverter", "Failed to get context from aioMsgItem: ${e.message}")
                writeDebugLog("通过getContext方法获取context失败: ${e.message}")
            }
            
            // 如果上面的方法失败，尝试其他方法获取context
            if (context == null) {
                try {
                    // 尝试从父类或其他方法获取context
                    val activity = moe.ono.util.ContextUtils.getCurrentActivity()
                    if (activity != null) {
                        context = activity
                        writeDebugLog("通过ContextUtils.getCurrentActivity()获取context成功")
                    } else {
                        writeDebugLog("通过ContextUtils.getCurrentActivity()获取context失败")
                    }
                } catch (e: Exception) {
                    writeDebugLog("通过ContextUtils获取context失败: ${e.message}")
                }
            }
            
            if (context != null) {
                // Pass msgData to the input dialog, which could be null
                showFaceNameInputDialog(context, msgData)
            } else {
                Logger.e("SuperQQShowConverter", "无法获取context")
                writeDebugLog("所有方法都无法获取context")
            }
        }

        // Add the item to the existing menu
        param.result = listOf(item) + param.result as List<*>
    }

    /**
     * Write debug log to file
     */
    private fun writeDebugLog(message: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val logMessage = "[$timestamp] SuperQQShowConverter: $message\n"
            
            // 确保目录存在
            val debugDir = File("/sdcard/Android/media/com.tencent.mobileqq/blackshell/")
            if (!debugDir.exists()) {
                debugDir.mkdirs()
            }
            
            // 写入日志文件
            val logFile = File(debugDir, "debug.log")
            logFile.appendText(logMessage)
        } catch (e: Exception) {
            Logger.e("SuperQQShowConverter", "Failed to write debug log: ${e.message}")
        }
    }

    /**
     * Show input dialog to get face name from user
     */
    private fun showFaceNameInputDialog(context: Context, msgData: Map<*, *>?) {
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
                        writeDebugLog("用户输入表情名称: $finalFaceName")
                        
                        // Extract d1, d2, d3 from the original message if msgData is not null
                        val d1 = if (msgData != null) extractD1FromMsg(msgData) else null
                        val d2 = if (msgData != null) extractD2FromMsg(msgData) else null
                        val d3 = if (msgData != null) extractD3FromMsg(msgData) else null
                        
                        Logger.i("SuperQQShowConverter", "提取到d1: $d1, d2: $d2, d3: $d3")
                        writeDebugLog("提取到d1: $d1, d2: $d2, d3: $d3")
                        
                        if (d1 != null && d2 != null && d3 != null) {
                            writeDebugLog("成功提取d1, d2, d3数据，准备构建模板")
                            // Build the template JSON with extracted values
                            val newJson = buildTemplateJson(d1, d2, d3, finalFaceName)
                            
                            // Call PacketHelper to fill in the JSON and send
                            PacketHelperDialog.createView(null, context, newJson)
                            
                            // Wait 100ms then auto-send
                            Handler(Looper.getMainLooper()).postDelayed({
                                PacketHelperDialog.performAutoSend()
                            }, 100)
                        } else {
                            Logger.e("SuperQQShowConverter", "无法提取完整的d1, d2, d3数据，使用默认模板")
                            writeDebugLog("无法提取完整的d1, d2, d3数据，msgData为null或提取失败，使用默认模板")
                            // 使用默认值创建一个基本的超表模板
                            val newJson = buildDefaultTemplate(finalFaceName)
                            PacketHelperDialog.createView(null, context, newJson)
                            
                            Handler(Looper.getMainLooper()).postDelayed({
                                PacketHelperDialog.performAutoSend()
                            }, 100)
                        }
                    }
                }
            )
            .show()
    }
    
    /**
     * Build a default template when message data extraction fails
     */
    private fun buildDefaultTemplate(faceName: String): String {
        return """[
    {
        "53": {
            "1": 48,
            "2": {
                "1": {
                    "1": {
                        "1": {
                            "1": 618785,
                            "2": "a_11314e9ab6d9233bb5a78fc4e6b22a728f817b1c",
                            "3": "11314e9ab6d9233bb5a78fc4e6b22a728f817b1c",
                            "4": "100",
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
                        "2": "1000",
                        "3": 1,
                        "4": 1766841635,
                        "5": 2678400,
                        "6": 0
                    },
                    "2": {
                        "1": "/download?appid=1407&fileid=1000",
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
    }
]"""
    }

    /**
     * Extract the full message data from the message object
     */
    private fun extractMsgDataFromMsg(msgRecord: Any): Map<*, *>? {
        return runCatching {
            writeDebugLog("开始尝试提取消息数据，msgRecord类型: ${msgRecord.javaClass.name}")
            
            // 尝试通过反射获取消息相关字段
            try {
                // 尝试获取消息体相关字段
                val fields = msgRecord.javaClass.declaredFields.filter { !it.name.startsWith("kotlin") && !it.name.startsWith("$") }
                writeDebugLog("msgRecord类字段: ${fields.map { it.name }}")
                
                // 尝试获取a1字段，它包含PicElement
                for (field in fields) {
                    if (field.name == "a1") {
                        field.isAccessible = true
                        val value = field.get(msgRecord)
                        writeDebugLog("字段 ${field.name} 的值类型: ${value?.javaClass?.name}")
                        
                        // 如果a1是一个Lazy对象，需要获取其实际值
                        var actualValue = value
                        if (value?.javaClass?.simpleName?.contains("Lazy") == true) {
                            try {
                                actualValue = value.javaClass.getMethod("getValue").invoke(value)
                                writeDebugLog("Lazy值解析成功: ${actualValue?.javaClass?.name}")
                            } catch (e: Exception) {
                                writeDebugLog("Lazy值解析失败: ${e.message}")
                            }
                        }
                        
                        if (actualValue != null) {
                            // 尝试构建一个包含图片信息的Map，用于后续提取d1/d2/d3
                            val result = mutableMapOf<Any, Any>()
                            
                            // 添加一个特殊标记，表示这是通过PicElement获取的数据
                            result["picElement"] = actualValue
                            
                            writeDebugLog("通过a1字段成功构建消息数据")
                            return@runCatching result
                        }
                    }
                }
                
                // 如果上述方法都失败，尝试其他字段
                for (field in fields) {
                    if (field.name.contains("msg", ignoreCase = true) || 
                        field.name.contains("body", ignoreCase = true) ||
                        field.name.contains("data", ignoreCase = true) ||
                        field.name == "b1" || field.name == "c1" || 
                        field.name == "d1" || field.name == "e1" || field.name == "f1" || field.name == "g1") {
                        
                        field.isAccessible = true
                        val value = field.get(msgRecord)
                        writeDebugLog("字段 ${field.name} 的值类型: ${value?.javaClass?.name}, 值: ${if (value is Map<*, *>) "Map(size=${value.size})" else value}")
                        
                        if (value is Map<*, *>) {
                            writeDebugLog("找到Map类型的字段 ${field.name}")
                            return@runCatching value
                        }
                    }
                }
                
                // 尝试通过方法调用获取
                var body = msgRecord.invoke("getMsgBody")
                if (body != null) writeDebugLog("通过getMsgBody获取到: ${body.javaClass.name}")
                
                if (body == null) {
                    body = msgRecord.invoke("getRawMsg")
                    if (body != null) writeDebugLog("通过getRawMsg获取到: ${body.javaClass.name}")
                }
                
                if (body == null) {
                    body = msgRecord.invoke("getMsgBodyMap")
                    if (body != null) writeDebugLog("通过getMsgBodyMap获取到: ${body.javaClass.name}")
                }
                
                // 如果通过方法调用获取到了Map类型的body，返回它
                if (body is Map<*, *>) {
                    writeDebugLog("成功获取Map类型的消息体，大小: ${body.size}")
                    return@runCatching body
                }
                
            } catch (e: Exception) {
                writeDebugLog("提取消息数据时发生异常: ${e.message}")
            }
            
            writeDebugLog("无法获取消息数据，返回null")
            null
        }.onFailure {
            Logger.e("SuperQQShowConverter", it)
            writeDebugLog("提取消息数据失败: ${it.message}")
        }.getOrNull()
    }

    /**
     * Extract d1 from the message data
     * d1 = "6"."3"."1"."2"."53"."2"."1"."1"."1"."2" 
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractD1FromMsg(msg: Map<*, *>): String? {
        writeDebugLog("开始提取d1，msg map大小: ${msg.size}")
        writeDebugLog("msg keys: ${msg.keys}")
        
        try {
            // 检查是否是通过MsgElement获取的数据
            val msgElement = msg["picElement"]
            if (msgElement != null) {
                writeDebugLog("检测到MsgElement对象: ${msgElement.javaClass.name}")
                
                // 从MsgElement获取picElement
                val picElementField = msgElement.javaClass.getDeclaredField("picElement")
                picElementField.isAccessible = true
                val picElement = picElementField.get(msgElement)
                
                if (picElement != null) {
                    writeDebugLog("获取到picElement对象: ${picElement.javaClass.name}")
                    
                    // 尝试获取md5HexStr字段，这应该是d1的值
                    val md5HexStrField = picElement.javaClass.getDeclaredField("md5HexStr")
                    md5HexStrField.isAccessible = true
                    val md5HexStrValue = md5HexStrField.get(picElement) as? String
                    writeDebugLog("d1: 从md5HexStr获取到值: $md5HexStrValue")
                    
                    return md5HexStrValue
                }
            }
            
            // 如果不是MsgElement数据，使用原来的方法
            val section6 = msg[6] as? Map<*, *>
            writeDebugLog("d1: 获取section6结果: ${section6 != null}")
            
            val section3 = section6?.get(3) as? Map<*, *>
            writeDebugLog("d1: 获取section3结果: ${section3 != null}")
            
            val section1 = section3?.get(1) as? Map<*, *>
            writeDebugLog("d1: 获取section1结果: ${section1 != null}")
            
            val list2 = section1?.get(2) as? List<*>
            writeDebugLog("d1: 获取list2结果: ${list2 != null}, 大小: ${list2?.size}")
            
            list2?.forEach { elem ->
                if (elem is Map<*, *>) {
                    writeDebugLog("d1: 遍历list2中的元素，keys: ${elem.keys}")
                    // Look for the 53 element in the list
                    elem.entries.forEach { entry ->
                        val key = entry.key
                        writeDebugLog("d1: 检查key: $key")
                        if (key == 53) {
                            writeDebugLog("d1: 找到key 53")
                            val value53 = entry.value as? Map<*, *>
                            val section2 = value53?.get(2) as? Map<*, *>
                            val section1Inner = section2?.get(1) as? Map<*, *>
                            val section1Double = section1Inner?.get(1) as? Map<*, *>
                            val section1Triple = section1Double?.get(1) as? Map<*, *>
                            val d1Value = section1Triple?.get(2) as? String
                            writeDebugLog("d1: 最终提取结果: $d1Value")
                            if (d1Value != null) {
                                return d1Value
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            writeDebugLog("提取d1时发生异常: ${e.message}")
            Logger.e("SuperQQShowConverter", "Error extracting d1: ${e.message}")
        }

        writeDebugLog("d1: 提取失败，返回null")
        return null
    }

    /**
     * Extract d2 from the message data
     * d2 = "6"."3"."1"."2"."53"."2"."1"."1"."1"."4"
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractD2FromMsg(msg: Map<*, *>): String? {
        writeDebugLog("开始提取d2，msg map大小: ${msg.size}")
        
        try {
            // 检查是否是通过MsgElement获取的数据
            val msgElement = msg["picElement"]
            if (msgElement != null) {
                writeDebugLog("检测到MsgElement对象: ${msgElement.javaClass.name}")
                
                // 从MsgElement获取picElement
                val picElementField = msgElement.javaClass.getDeclaredField("picElement")
                picElementField.isAccessible = true
                val picElement = picElementField.get(msgElement)
                
                if (picElement != null) {
                    writeDebugLog("获取到picElement对象: ${picElement.javaClass.name}")
                    
                    // 尝试获取fileName字段，这应该是d2的值
                    val fileNameField = picElement.javaClass.getDeclaredField("fileName")
                    fileNameField.isAccessible = true
                    val fileNameValue = fileNameField.get(picElement) as? String
                    writeDebugLog("d2: 从fileName获取到值: $fileNameValue")
                    
                    return fileNameValue
                }
            }
            
            // 如果不是MsgElement数据，使用原来的方法
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
                            writeDebugLog("d2: 最终提取结果: $d2Value")
                            if (d2Value != null) {
                                return d2Value
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            writeDebugLog("提取d2时发生异常: ${e.message}")
            Logger.e("SuperQQShowConverter", "Error extracting d2: ${e.message}")
        }

        writeDebugLog("d2: 提取失败，返回null")
        return null
    }

    /**
     * Extract d3 from the message data
     * d3 = "6"."3"."1"."2"."53"."2"."1"."1"."2"
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractD3FromMsg(msg: Map<*, *>): String? {
        writeDebugLog("开始提取d3，msg map大小: ${msg.size}")
        
        try {
            // 检查是否是通过MsgElement获取的数据
            val msgElement = msg["picElement"]
            if (msgElement != null) {
                writeDebugLog("检测到MsgElement对象: ${msgElement.javaClass.name}")
                
                // 从MsgElement获取picElement
                val picElementField = msgElement.javaClass.getDeclaredField("picElement")
                picElementField.isAccessible = true
                val picElement = picElementField.get(msgElement)
                
                if (picElement != null) {
                    writeDebugLog("获取到picElement对象: ${picElement.javaClass.name}")
                    
                    // 尝试获取fileUuid字段，这应该是d3的值
                    val fileUuidField = picElement.javaClass.getDeclaredField("fileUuid")
                    fileUuidField.isAccessible = true
                    val fileUuidValue = fileUuidField.get(picElement) as? String
                    writeDebugLog("d3: 从fileUuid获取到值: $fileUuidValue")
                    
                    return fileUuidValue
                }
            }
            
            // 如果不是MsgElement数据，使用原来的方法
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
                            writeDebugLog("d3: 最终提取结果: $d3Value")
                            if (d3Value != null) {
                                return d3Value
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            writeDebugLog("提取d3时发生异常: ${e.message}")
            Logger.e("SuperQQShowConverter", "Error extracting d3: ${e.message}")
        }

        writeDebugLog("d3: 提取失败，返回null")
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