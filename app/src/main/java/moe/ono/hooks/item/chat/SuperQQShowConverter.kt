package moe.ono.hooks.item.chat

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.widget.EditText
import com.tencent.qqnt.kernel.nativeinterface.*
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import moe.ono.R
import moe.ono.bridge.ntapi.ChatTypeConstants
import moe.ono.bridge.ntapi.MsgServiceHelper
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks._core.factory.HookItemFactory.getItem
import moe.ono.hooks.dispatcher.OnMenuBuilder
import moe.ono.hooks.protocol.sendMessage
import moe.ono.reflex.Reflex
import moe.ono.reflex.invoke
import moe.ono.util.*

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

        // Extract the file ID from the message
        val fileId = handleMsg(aioMsgItem)
        
        if (!fileId.isNullOrEmpty()) {
            // Create a menu item for converting to Super QQ Show
            val item = CustomMenu.createItemIconNt(
                aioMsgItem,
                "转超表",
                R.drawable.ic_sticker, // Use an existing icon
                R.id.item_super_qq_show_convert
            ) {
                Logger.i("SuperQQShowConverter", "用户点击转超表菜单，fileId=$fileId")
                // Here you can implement the actual conversion logic
                // For now, just log that it was clicked
            }

            // Add the item to the existing menu
            param.result = listOf(item) + param.result as List<*>
        }
    }

    /**
     * Main entry: process a message
     */
    private fun handleMsg(msgRecord: Any): String? {
        val fileId = extractFileIdFromMsg(msgRecord)

        if (fileId.isNullOrEmpty()) {
            Logger.i("SuperQQShowConverter", "no fileId found, skip")
            return null
        }

        Logger.i("SuperQQShowConverter", "fileId = $fileId")
        return fileId
    }

    /**
     * ✅ Core function:
     * Extract fileId from 【original message content】
     *
     * Compatible with:
     * - NT MsgRecord
     * - Rich text elems
     * - Elem 53 file structure
     */
    private fun extractFileIdFromMsg(msgRecord: Any): String? {
        return runCatching {
            // Try elements first (most common)
            val elements = msgRecord.invoke("getElements") as? List<*>
            if (!elements.isNullOrEmpty()) {
                extractFromElements(elements)?.let { return it }
            }

            // Fallback: try msgBody / rawMsg / map (different versions use different names)
            val body =
                msgRecord.invoke("getMsgBody")
                    ?: msgRecord.invoke("getRawMsg")
                    ?: msgRecord.invoke("getMsgBodyMap")

            extractFromMap(body)
        }.onFailure {
            Logger.e("SuperQQShowConverter", it)
        }.getOrNull()
    }

    /**
     * Extract fileId from NT elements
     */
    private fun extractFromElements(elements: List<*>): String? {
        for (elem in elements) {
            if (elem == null) continue

            // type == 53 → file element
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
        }
        return null
    }

    /**
     * Extract fileId from Map / ProtoMap structure
     * Exactly corresponds to the JSON structure you provided
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractFromMap(msg: Any?): String? {
        if (msg !is Map<*, *>) return null

        /*
         * Corresponding path:
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