package moe.ono.hooks.item.chat

import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem

@HookItem(path = "聊天与消息/禁止弹出表情包联想栏", description = "避免误触🍬的钥匙的表情包")
class FuckStickerRecommended : BaseSwitchFunctionHookItem() {
    override fun entry(classLoader: ClassLoader) {
        val targetClassName =
            "com.tencent.qqnt.emotion.stickerrecommended.view.EmotionKeywordLayout"

        try {
            val targetClass = XposedHelpers.findClass(targetClassName, classLoader)

            XposedHelpers.findAndHookMethod(
                targetClass,
                "setVisibility",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (param.args[0] == View.VISIBLE) {
                            param.args[0] = View.GONE
                        }
                    }
                }
            )
        } catch (e: Throwable) {

        }
    }
}