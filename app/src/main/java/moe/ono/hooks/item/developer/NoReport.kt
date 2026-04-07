package moe.ono.hooks.item.developer

import android.annotation.SuppressLint
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem

@SuppressLint("DiscouragedApi")
@HookItem(
    path = "开发者选项/关掉操作上报",
    description = "禁止黑色壳子上报你的行为 (不包括卡片) (简单上报而已，不传别的东西，别当回事...)"
)
class NoReport : BaseSwitchFunctionHookItem() {
    override fun entry(classLoader: ClassLoader) {}
}