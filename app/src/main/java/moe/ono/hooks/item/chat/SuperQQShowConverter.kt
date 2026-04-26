package moe.ono.hooks.item.chat

import android.annotation.SuppressLint
import android.content.Context
import com.lxj.xpopup.XPopup
import com.lxj.xpopup.interfaces.OnInputConfirmListener
import de.robv.android.xposed.XC_MethodHook
import moe.ono.R
import moe.ono.creator.PacketHelperDialog
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks._core.factory.HookItemFactory.getItem
import moe.ono.hooks.dispatcher.OnMenuBuilder
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
    description = "长按图片或表情 → 转超表，别强求所有人都能用，至少9.2.27用不了。。。"
)
class SuperQQShowConverter : BaseSwitchFunctionHookItem(), OnMenuBuilder {

    override fun entry(classLoader: ClassLoader) {}

    override val targetTypes = arrayOf(
        "com.tencent.mobileqq.aio.msglist.holder.component.pic.AIOPicContentComponent",
        "com.tencent.mobileqq.aio.msglist.holder.component.mix.AIOMixContentComponent",
        "com.tencent.mobileqq.aio.msglist.holder.component.marketface.AIOMarketFaceComponent"
    )

    override fun onGetMenu(aioMsgItem: Any, targetType: String, param: XC_MethodHook.MethodHookParam) {
        if (!getItem(this.javaClass).isEnabled) return

        val msgData = extractMsgDataFromMsg(aioMsgItem)
        writeDebugLog("提取msgData结果: ${msgData != null}")

        val item = CustomMenu.createItemIconNt(
            aioMsgItem,
            "转超表",
            R.drawable.ic_sticker,
            R.id.item_super_qq_show_convert
        ) {
            writeDebugLog("用户点击转超表菜单, msgData: ${msgData != null}")

            var context: Context? = null
            try {
                context = aioMsgItem.invoke("getContext") as? Context
            } catch (e: Exception) {
                writeDebugLog("通过getContext失败: ${e.message}")
            }
            if (context == null) {
                try {
                    context = ContextUtils.getCurrentActivity()
                    writeDebugLog("通过getCurrentActivity获取context: ${context != null}")
                } catch (e: Exception) {
                    writeDebugLog("通过ContextUtils失败: ${e.message}")
                }
            }

            if (context != null) {
                showFaceNameInputDialog(context, msgData)
            } else {
                writeDebugLog("所有方法都无法获取context")
            }
        }

        param.result = listOf(item) + param.result as List<*>
    }

    private fun writeDebugLog(message: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val debugDir = File("/sdcard/Android/media/com.tencent.mobileqq/blackshell/")
            if (!debugDir.exists()) debugDir.mkdirs()
            File(debugDir, "debug.log").appendText("[$timestamp] SuperQQShowConverter: $message\n")
        } catch (_: Exception) {}
    }

    /**
     * 提取 PicElement：
     * PicMsgItem.i1 是 SynchronizedLazyImpl，_value 就是 PicElement
     * 同时兜底遍历所有字段找 PicElement 类型
     */
    private fun extractMsgDataFromMsg(msgRecord: Any): Map<*, *>? {
        return runCatching {
            writeDebugLog("msgRecord类型: ${msgRecord.javaClass.name}")

            // 优先路径：i1 -> Lazy._value -> PicElement（高版本已确认）
            val picElement = extractPicElementFromLazyField(msgRecord, "i1")
                ?: extractPicElementByTraversal(msgRecord)

            if (picElement != null) {
                writeDebugLog("成功获取PicElement: ${picElement.javaClass.name}")
                return@runCatching mutableMapOf<Any, Any>("picElement" to picElement)
            }

            writeDebugLog("未找到PicElement，返回null")
            null
        }.onFailure {
            writeDebugLog("extractMsgDataFromMsg异常: ${it.message}")
        }.getOrNull()
    }

    /** 从指定 Lazy 字段名解包拿 PicElement */
    private fun extractPicElementFromLazyField(obj: Any, fieldName: String): Any? {
        return runCatching {
            val field = obj.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            val lazy = field.get(obj) ?: return@runCatching null

            // 解包 Lazy
            val valueField = lazy.javaClass.getDeclaredField("_value")
            valueField.isAccessible = true
            val value = valueField.get(lazy) ?: return@runCatching null

            // 确认是 PicElement
            if (value.javaClass.name.contains("PicElement", ignoreCase = true)) {
                writeDebugLog("通过字段[$fieldName]._value 获取到PicElement")
                value
            } else null
        }.onFailure {
            writeDebugLog("extractPicElementFromLazyField[$fieldName]失败: ${it.message}")
        }.getOrNull()
    }

    /** 兜底：遍历所有字段（含父类），找类名包含 PicElement 的 */
    private fun extractPicElementByTraversal(obj: Any): Any? {
        return runCatching {
            var cls: Class<*>? = obj.javaClass
            while (cls != null && cls != Any::class.java) {
                for (field in cls.declaredFields) {
                    if (field.name.startsWith("$")) continue
                    field.isAccessible = true
                    val raw = runCatching { field.get(obj) }.getOrNull() ?: continue

                    // 直接是 PicElement
                    if (raw.javaClass.name.contains("PicElement", ignoreCase = true)) {
                        writeDebugLog("兜底遍历：字段[${field.name}]直接命中PicElement")
                        return@runCatching raw
                    }

                    // Lazy 包装的 PicElement
                    if (raw.javaClass.simpleName.contains("Lazy", ignoreCase = true)) {
                        val inner = runCatching {
                            val vf = raw.javaClass.getDeclaredField("_value")
                            vf.isAccessible = true
                            vf.get(raw)
                        }.getOrNull() ?: continue
                        if (inner.javaClass.name.contains("PicElement", ignoreCase = true)) {
                            writeDebugLog("兜底遍历：字段[${field.name}](Lazy)命中PicElement")
                            return@runCatching inner
                        }
                    }

                    // MsgElement 内部的 picElement 子字段
                    if (raw.javaClass.name.contains("MsgElement", ignoreCase = true)) {
                        val sub = runCatching {
                            val sf = raw.javaClass.getDeclaredField("picElement")
                            sf.isAccessible = true
                            sf.get(raw)
                        }.getOrNull()
                        if (sub != null && sub.javaClass.name.contains("PicElement", ignoreCase = true)) {
                            writeDebugLog("兜底遍历：字段[${field.name}].picElement 命中")
                            return@runCatching sub
                        }
                    }
                }
                cls = cls.superclass
            }
            null
        }.onFailure {
            writeDebugLog("extractPicElementByTraversal异常: ${it.message}")
        }.getOrNull()
    }

    // ========== d1 / d2 / d3 提取 ==========
    // 已确认字段名：md5HexStr / fileName / fileUuid

    private fun extractD1FromMsg(msg: Map<*, *>): String? {
        return extractStringField(msg, "md5HexStr").also {
            writeDebugLog("d1(md5HexStr) = $it")
        }
    }

    private fun extractD2FromMsg(msg: Map<*, *>): String? {
        return extractStringField(msg, "fileName").also {
            writeDebugLog("d2(fileName) = $it")
        }
    }

    private fun extractD3FromMsg(msg: Map<*, *>): String? {
        return extractStringField(msg, "fileUuid").also {
            writeDebugLog("d3(fileUuid) = $it")
        }
    }

    private fun extractStringField(msg: Map<*, *>, vararg names: String): String? {
        val picElement = msg["picElement"] ?: return null
        var cls: Class<*>? = picElement.javaClass
        while (cls != null && cls != Any::class.java) {
            for (name in names) {
                val field = runCatching { cls!!.getDeclaredField(name) }.getOrNull() ?: continue
                field.isAccessible = true
                val v = runCatching { field.get(picElement) as? String }.getOrNull()
                if (!v.isNullOrEmpty()) return v
            }
            cls = cls.superclass
        }
        return null
    }

    // ========== UI ==========

    private fun showFaceNameInputDialog(context: Context, msgData: Map<*, *>?) {
        val fixContext = CommonContextWrapper.createAppCompatContext(context)

        XPopup.Builder(fixContext)
            .asBottomList("选择表情类型", arrayOf("超级QQ秀表情", "AI表情")) { position, _ ->
                val title = if (position == 0) "转超级QQ秀表情" else "转AI表情"
                XPopup.Builder(fixContext)
                    .asInputConfirm(title, "请输入表情名称", "嘿壳表情",
                        object : OnInputConfirmListener {
                            override fun onConfirm(faceName: String?) {
                                val finalFaceName = if (faceName.isNullOrEmpty()) "嘿壳表情" else faceName
                                writeDebugLog("选择[$title]，名称=$finalFaceName")

                                val d1 = msgData?.let { extractD1FromMsg(it) }
                                val d2 = msgData?.let { extractD2FromMsg(it) }
                                val d3 = msgData?.let { extractD3FromMsg(it) }
                                writeDebugLog("d1=$d1, d2=$d2, d3=$d3")

                                val json = if (d1 != null && d2 != null && d3 != null) {
                                    if (position == 0) buildTemplateJson(d1, d2, d3, finalFaceName)
                                    else buildTemplateJson2(d1, d2, d3, finalFaceName)
                                } else {
                                    writeDebugLog("数据不完整，使用默认模板")
                                    buildDefaultTemplate(finalFaceName)
                                }

                                PacketHelperDialog.createView(null, context, json)
                                Handler(Looper.getMainLooper()).postDelayed({
                                    PacketHelperDialog.performAutoSend(context)
                                }, 100)
                            }
                        })
                    .show()
            }
            .show()
    }

    // ========== 模板 ==========

    private fun buildDefaultTemplate(faceName: String): String = """[
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
                            "5": {"1": 1,"2": 2000,"3": 0,"4": 0},
                            "6": 360,"7": 360,"8": 0,"9": 0
                        },
                        "2": "1000","3": 1,"4": 1766841635,"5": 2678400,"6": 0
                    },
                    "2": {
                        "1": "/download?appid=1407&fileid=1000",
                        "2": {"1": "&spec=0","2": "&spec=720","3": "&spec=198"},
                        "3": "multimedia.nt.qq.com.cn"
                    },
                    "5": 0,
                    "6": {"2": "hex->E6417C37C58CD6208F835F631E931730EE1A596C"}
                },
                "2": {
                    "1": {
                        "1": 1,"2": "[$faceName]","1001": 2,"1002": 2,"1003": 3712448771,
                        "12": {
                            "1": 1,"34": 0,"18": {},"19": {},"3": 0,"4": 0,
                            "21": {"1": 6740,"2": "$faceName","3": 1,"4": 100,"5": 0,"7": {}},
                            "9": "[$faceName]","10": 0,"12": {},
                            "30": "&rkey=CAISONPsN0nSR8aLWIQhoe7Q9E7fpVD5-0VvOwO-C40DlC_G71a_cWCblgd4HvLFkvF1TH6BxoPvEmrz",
                            "31": {}
                        }
                    },
                    "2": {"3": {}},"3": {"11": {},"12": {}},"10": 0
                }
            },
            "3": 20
        }
    }
]"""

    private fun buildTemplateJson(d1: String, d2: String, d3: String, faceName: String): String = """[
    {
        "37": {
            "16": 0,"17": 161824,"1": 19,
            "19": {
                "96": 0,"65": {"1": 1,"2": 20},"66": 33554560,"34": 2000,"4": 10315,
                "71": 3,"72": 0,"73": {"1": 45,"2": 0,"3": 113,"6": 5,"7": 2},
                "41": 0,"107": 828,"79": 131136,"15": 161494,"80": 37,"81": 16,
                "51": 339,"116": -444136893304753334,"52": 8,"54": 1,"55": 1,"56": 0,
                "25": 0,"90": {"3": [{"1": 0,"2": "u_"}]},"58": 0,"30": 0,"31": 0
            },
            "6": 2,
            "7": "aQoAJ330Au8x79xu3tAI4Ntf0clrbrn66Fux3TbeiYZp6YAiHKgGu+VjtVuHsKjA",
            "12": 1
        }
    },
    {"9": {"1": 2141485}},
    {
        "53": {
            "1": 48,
            "2": {
                "1": {
                    "1": {
                        "1": {
                            "1": 618785,"2": "$d1",
                            "3": "11314e9ab6d9233bb5a78fc4e6b22a728f817b1c",
                            "4": "$d2",
                            "5": {"1": 1,"2": 2000,"3": 0,"4": 0},
                            "6": 360,"7": 360,"8": 0,"9": 0
                        },
                        "2": "$d3","3": 1,"4": 1766841635,"5": 2678400,"6": 0
                    },
                    "2": {
                        "1": "/download?appid=1407&fileid=$d3",
                        "2": {"1": "&spec=0","2": "&spec=720","3": "&spec=198"},
                        "3": "multimedia.nt.qq.com.cn"
                    },
                    "5": 0,
                    "6": {"2": "hex->E6417C37C58CD6208F835F631E931730EE1A596C"}
                },
                "2": {
                    "1": {
                        "1": 1,"2": "[$faceName]","1001": 2,"1002": 2,"1003": 3712448771,
                        "12": {
                            "1": 1,"34": 0,"18": {},"19": {},"3": 0,"4": 0,
                            "21": {"1": 6740,"2": "$faceName","3": 1,"4": 100,"5": 0,"7": {}},
                            "9": "[$faceName]","10": 0,"12": {},
                            "30": "&rkey=CAISONPsN0nSR8aLWIQhoe7Q9E7fpVD5-0VvOwO-C40DlC_G71a_cWCblgd4HvLFkvF1TH6BxoPvEmrz",
                            "31": {}
                        }
                    },
                    "2": {"3": {}},"3": {"11": {},"12": {}},"10": 0
                }
            },
            "3": 20
        }
    },
    {"16": {"1": "x","3": 1,"4": 8,"7": {}}}
]"""

    private fun buildTemplateJson2(d1: String, d2: String, d3: String, faceName: String): String = """[
    {
        "37": {
            "16": 2122936,"17": 160133,"1": 19,
            "19": {
                "96": 0,"65": {"2": 13},"66": 33556352,"34": 2000,"4": 10315,
                "71": 258,"72": 0,"73": {"1": 45,"2": 0,"6": 107},
                "41": 0,"107": 1710,"79": 163920,"15": 145867,"80": 39,"81": 16,
                "51": 339,"116": 6090842017719333217,"52": 4,"54": 0,"55": 0,"56": 21989,
                "25": 0,"90": {"3": [{"1": 0,"2": "u_blackshellNB"}]},"58": 0,"30": 0,"31": 0
            },
            "6": 2,
            "7": "3+NdwcvbghK4Ybrh0\/6PWDI9CSFScSUWPjsdrDTP2j2XCQPtnEgScuAZWGzvQzlE"
        }
    },
    {"9": {"1": 2133178}},
    {
        "53": {
            "1": 48,
            "2": {
                "1": {
                    "1": {
                        "1": {
                            "1": 801156,"2": "$d1",
                            "3": "f96a7b3b844c18590a4c955b34d8cffcc943dbfc",
                            "4": "$d2",
                            "5": {"1": 1,"2": 2000,"3": 0,"4": 0},
                            "6": 240,"7": 240,"8": 0,"9": 0
                        },
                        "2": "$d3","3": 1,"4": 1767104904,"5": 2678400,"6": 0
                    },
                    "2": {
                        "1": "\/download?appid=1407&fileid=$d3",
                        "2": {"1": "&spec=0","2": "&spec=720","3": "&spec=198"},
                        "3": "multimedia.nt.qq.com.cn"
                    },
                    "5": 0,
                    "6": {"2": "hex->38ED05A9E2EA63C9E77BE77977277DA6D951247F"}
                },
                "2": {
                    "1": {
                        "1": 14,"2": "[$faceName]","1001": 2,"1002": 2,"1003": 1074838787,
                        "12": {
                            "1": 14,"34": 11,"18": {},"19": {},"3": 0,"4": 0,
                            "21": {"1": 0,"2": {},"3": 0,"4": 0,"5": 0,"7": {}},
                            "9": "[$faceName]","10": 0,"12": {},
                            "30": "&rkey=CAESOE4_cASDm1t1h9zOBR0q8M5FW0dz0Zxhjkqs5_9dweD3hzItCPaWXAl5GJPqXsW_tictlW9YKrFS",
                            "31": "edc086e75a8b7458b7bcecdbfe53fbe7"
                        }
                    },
                    "2": {"3": {}},"3": {"11": {},"12": {}},"10": 0
                }
            },
            "3": 20
        }
    },
    {"16": {"1": "x","3": 1,"7": {}}}
]"""
}