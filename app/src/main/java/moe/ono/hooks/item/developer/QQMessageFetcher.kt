package moe.ono.hooks.item.developer

import android.annotation.SuppressLint
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import moe.ono.R
import moe.ono.bridge.ntapi.ChatTypeConstants.C2C
import moe.ono.bridge.ntapi.ChatTypeConstants.GROUP
import moe.ono.bridge.ntapi.MsgServiceHelper
import moe.ono.config.CacheConfig.setMsgRecord
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks._core.factory.HookItemFactory.getItem
import moe.ono.hooks.base.util.Toasts
import moe.ono.hooks.dispatcher.OnMenuBuilder
import moe.ono.hooks.protocol.sendPacket
import moe.ono.reflex.Reflex
import moe.ono.reflex.invoke
import moe.ono.util.AppRuntimeHelper
import moe.ono.util.ContextUtils
import moe.ono.util.CustomMenu
import moe.ono.util.Logger
import moe.ono.util.Session
import moe.ono.util.SyncUtils

@SuppressLint("DiscouragedApi")
@HookItem(path = "开发者选项/Element(s) 反序列化", description = "长按消息点击“拉取”进行反序列化操作")
class QQMessageFetcher : BaseSwitchFunctionHookItem(), OnMenuBuilder {

    companion object {
        fun pullGroupMsg(msgRecord: MsgRecord){
            val seq = msgRecord.msgSeq
            sendPacket("MessageSvc.PbGetGroupMsg", """{"1": ${msgRecord.peerUid}, "2": ${seq}, "3": ${seq}, "6": 0}""")
        }

        fun pullC2CMsg(msgRecord: MsgRecord){
            sendPacket("MessageSvc.PbGetOneDayRoamMsg", """{"1": ${msgRecord.peerUin}, "2": ${msgRecord.msgTime}, "3": 0, "4": 1}""")
        }

        /**
         * 检测消息是否包含图片特征
         * 参考 SuperQQShowConverter.kt 的实现
         */
        fun hasImageFeature(msgRecord: Any): Boolean {
            return try {
                runCatching {
                    // Try elements first (most common)
                    val elements = msgRecord.invoke("getElements") as? List<*>
                    if (!elements.isNullOrEmpty() && extractFileIdFromElements(elements) != null) {
                        return true
                    }

                    // Fallback: try msgBody / rawMsg / map (different versions use different names)
                    val body =
                        msgRecord.invoke("getMsgBody")
                            ?: msgRecord.invoke("getRawMsg")
                            ?: msgRecord.invoke("getMsgBodyMap")

                    extractFileIdFromMap(body) != null
                }.onFailure {
                    Logger.e("QQMessageFetcher", it)
                }.getOrDefault(false)
            } catch (e: Exception) {
                Logger.e("QQMessageFetcher", e)
                false
            }
        }

        /**
         * 从 NT elements 里提取 fileId
         */
        private fun extractFileIdFromElements(elements: List<*>): String? {
            for (elem in elements) {
                if (elem == null) continue

                // type == 53 → file element (可能包含图片)
                val fileElem = runCatching {
                    elem.invoke("getFile")
                }.getOrNull() ?: continue

                val fileInfo = runCatching {
                    fileElem.invoke("getFileInfo")
                }.getOrNull() ?: continue

                val fileId = runCatching {
                    fileInfo.invoke("getFileId") as? String
                }.getOrNull()

                if (!fileId.isNullOrEmpty()) {
                    return fileId
                }

                // 也检查图片元素
                val picElem = runCatching {
                    elem.invoke("getPic")
                }.getOrNull()

                if (picElem != null) {
                    // 如果有图片元素，即使没有fileId也认为是图片消息
                    return "image_found" // 返回特殊标记表示有图片特征
                }
            }
            return null
        }

        /**
         * 从 Map / ProtoMap 结构里提取 fileId
         */
        @Suppress("UNCHECKED_CAST")
        private fun extractFileIdFromMap(msg: Any?): String? {
            if (msg !is Map<*, *>) return null

            /*
             * 对应路径：
             * 6 -> 3 -> 1 -> 2 (list)
             * list[x] -> 53 -> 2 -> 1 -> 1 -> 2 (fileId)
             */
            val elemList = (((msg[6] as? Map<*, *>)
                ?.get(3) as? Map<*, *>)
                ?.get(1) as? Map<*, *>)
                ?.get(2) as? List<*> ?: return null

            for (elem in elemList) {
                val fileId = (((((elem as? Map<*, *>)
                    ?.get(53) as? Map<*, *>)
                    ?.get(2) as? Map<*, *>)
                    ?.get(1) as? Map<*, *>)
                    ?.get(1) as? Map<*, *>)
                    ?.get(2) as? String

                if (!fileId.isNullOrEmpty()) {
                    return fileId
                }
            }
            return null
        }
    }

    override fun entry(classLoader: ClassLoader) {}

    override fun onGetMenu(aioMsgItem: Any, targetType: String, param: MethodHookParam) {
        if (!getItem(this.javaClass).isEnabled) {
            return
        }

        val pullItem: Any = CustomMenu.createItemIconNt(
            aioMsgItem,
            "拉取",
            R.drawable.ic_get_app_24,
            R.id.item_pull_msg
        ) {
            try {
                val msgID = Reflex.invokeVirtual(aioMsgItem, "getMsgId") as Long
                val msgIDs = java.util.ArrayList<Long>()
                msgIDs.add(msgID)
                AppRuntimeHelper.getAppRuntime()
                    ?.let {
                        MsgServiceHelper.getKernelMsgService(
                            it
                        )
                    }?.getMsgsByMsgId(
                        Session.getContact(),
                        msgIDs
                    ) { _, _, msgList ->
                        SyncUtils.runOnUiThread {
                            for (msgRecord in msgList) {
                                val chatType = msgRecord.chatType

                                when (chatType) {
                                    C2C -> {
                                        pullC2CMsg(msgRecord)
                                        setMsgRecord(msgRecord)
                                    }

                                    GROUP -> {
                                        pullGroupMsg(msgRecord)
                                        setMsgRecord(msgRecord)
                                    }

                                    else -> {
                                        Toasts.info(
                                            ContextUtils.getCurrentActivity(),
                                            "不支持的聊天类型"
                                        )
                                    }
                                }

                            }
                        }
                    }
            } catch (e: Exception) {
                Logger.e("QQPullMsgEntry.msgLongClick", e)
            }
            Unit
        }

        // 检查消息是否包含图片特征，如果是，则添加"转超表"按钮
        if (hasImageFeature(aioMsgItem)) {
            val convertItem: Any = CustomMenu.createItemIconNt(
                aioMsgItem,
                "转超表",
                R.drawable.ic_sticker, // 使用贴纸图标
                R.id.item_super_qq_show_convert
            ) {
                try {
                    val msgID = Reflex.invokeVirtual(aioMsgItem, "getMsgId") as Long
                    val msgIDs = java.util.ArrayList<Long>()
                    msgIDs.add(msgID)
                    AppRuntimeHelper.getAppRuntime()
                        ?.let {
                            MsgServiceHelper.getKernelMsgService(
                                it
                            )
                        }?.getMsgsByMsgId(
                            Session.getContact(),
                            msgIDs
                        ) { _, _, msgList ->
                            SyncUtils.runOnUiThread {
                                for (msgRecord in msgList) {
                                    val fileId = extractFileIdFromMsgRecord(msgRecord)
                                    
                                    if (!fileId.isNullOrEmpty()) {
                                        Logger.i("QQMessageFetcher", "转超表 - 用户点击菜单，fileId=$fileId")
                                        
                                        // 这里可以实现转超表的实际逻辑
                                        // 目前只是记录日志
                                        Toasts.success(ContextUtils.getCurrentActivity(), "已检测到图片，可转换为超级QQ秀")
                                    } else {
                                        Logger.i("QQMessageFetcher", "转超表 - 未找到fileId")
                                        Toasts.info(ContextUtils.getCurrentActivity(), "未找到可转换的图片数据")
                                    }
                                }
                            }
                        }
                } catch (e: Exception) {
                    Logger.e("QQMessageFetcher.convertToSuperQQShow", e)
                    Toasts.error(ContextUtils.getCurrentActivity(), "转换失败")
                }
                Unit
            }

            param.result = listOf(convertItem, pullItem) + param.result as List<*>
        } else {
            // 没有图片特征，只添加拉取按钮
            param.result = listOf(pullItem) + param.result as List<*>
        }
    }

    /**
     * 从MsgRecord中提取fileId
     */
    private fun extractFileIdFromMsgRecord(msgRecord: MsgRecord): String? {
        return runCatching {
            val elements = msgRecord.elements
            if (!elements.isNullOrEmpty()) {
                extractFileIdFromElements(elements.map { it })
            } else {
                null
            }
        }.onFailure {
            Logger.e("QQMessageFetcher", it)
        }.getOrNull()
    }

    /**
     * 从MsgRecord.Element列表中提取fileId
     */
    private fun extractFileIdFromElements(elements: List<Any?>): String? {
        for (elem in elements) {
            if (elem == null) continue

            // 检查文件元素
            val fileElem = runCatching {
                elem.invoke("getFile")
            }.getOrNull() ?: continue

            val fileInfo = runCatching {
                fileElem.invoke("getFileInfo")
            }.getOrNull() ?: continue

            val fileId = runCatching {
                fileInfo.invoke("getFileId") as? String
            }.getOrNull()

            if (!fileId.isNullOrEmpty()) {
                return fileId
            }

            // 检查图片元素
            val picElem = runCatching {
                elem.invoke("getPic")
            }.getOrNull()

            if (picElem != null) {
                // 尝试从图片元素获取fileId
                val picFileId = runCatching {
                    picElem.invoke("getFileId") as? String
                }.getOrNull()

                if (!picFileId.isNullOrEmpty()) {
                    return picFileId
                }

                // 如果有图片元素，返回特殊标记
                return "image_found"
            }
        }
        return null
    }
}