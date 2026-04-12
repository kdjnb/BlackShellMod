package moe.ono.hooks.item.chat

import com.tencent.qqnt.kernel.nativeinterface.MsgAttributeInfo
import com.tencent.qqnt.kernel.nativeinterface.GroupDragonLadderAttr
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks.base.api.QQSendMsgListener

@HookItem(path = "聊天与消息/消息加接龙TAG", description = "懂你意思，发消息自动闪退")
class AutoDragon : BaseSwitchFunctionHookItem() {

    override fun entry(classLoader: ClassLoader) {

        QQSendMsgListener.listeners.add { param, elems ->

            if (!this.configIsEnable()) return@add

            if (elems.size == 1 && elems[0].textElement != null) {

                val text = elems[0].textElement.content ?: return@add

//                if (!text.contains("#dragon")) return@add

                var attr: MsgAttributeInfo? = null
                var attrIndex = -1

                for (i in param.args.indices) {
                    val arg = param.args[i]
                    if (arg is MsgAttributeInfo) {
                        attr = arg
                        attrIndex = i
                        break
                    }
                }

                if (attr == null) {
                    attr = MsgAttributeInfo()

                    // 👉 尝试塞回原参数位置（优先 index 3）
                    if (param.args.size > 3) {
                        param.args[3] = attr
                    }
                }

                try {
                    val dragon = GroupDragonLadderAttr()

                    fun setField(name: String, value: Any) {
                        try {
                            val f = dragon.javaClass.getDeclaredField(name)
                            f.isAccessible = true
                            f.set(dragon, value)
                        } catch (_: Throwable) {}
                    }

                    setField("dragonLadderId", 9178)
                    setField("selfItemOffsets", intArrayOf())

                    // 注入
                    attr.groupDragonLadder = dragon

                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
    }
}