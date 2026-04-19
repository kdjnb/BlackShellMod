package moe.ono.hooks.item.chat

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem

@HookItem(path = "聊天与消息/绕过群聊不可读封禁", description = "绕过群聊被封禁后不让进聊天页面")
class BypassGroupUnreadableBlock: BaseSwitchFunctionHookItem() {
    override fun entry(classLoader: ClassLoader) {
        XposedHelpers.findAndHookMethod("com.tencent.mobileqq.data.troop.TroopInfo", classLoader, "isUnreadableBlock", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (this@BypassGroupUnreadableBlock.configIsEnable()) param.result = false
            }
        })
    }
}
