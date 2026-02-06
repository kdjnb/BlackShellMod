package moe.ono.hooks.item.chat

import com.tencent.qqnt.kernel.nativeinterface.EmojiZPlan
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks.base.api.QQSendMsgListener

@HookItem(path = "聊天与消息/发送图片自动转为超表", description = "发送图片自动转为超级QQ秀表情")
class PicAutoQQShowTag : BaseSwitchFunctionHookItem(){
    override fun entry(classLoader: ClassLoader){
        QQSendMsgListener.listeners.add{_,elems ->
            if(this.configIsEnable()){
                if(elems.size==1 && elems[0].picElement != null){
                    val picElement = elems[0].picElement
                    picElement?.emojiZplan = EmojiZPlan(6421, picElement.summary ?: "", 100, 1, 0, "")
                }
            }
        }
    }
}