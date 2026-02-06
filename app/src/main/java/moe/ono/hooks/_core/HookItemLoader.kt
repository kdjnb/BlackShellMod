package moe.ono.hooks._core

import moe.ono.config.ConfigManager
import moe.ono.constants.Constants.PrekClickableXXX
import moe.ono.constants.Constants.PrekXXX
import moe.ono.hooks._base.ApiHookItem
import moe.ono.hooks._base.BaseClickableFunctionHookItem
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.factory.HookItemFactory
import moe.ono.loader.hookapi.IShortcutMenu
import moe.ono.util.Logger


class HookItemLoader {
    /**
     * 加载并判断哪些需要加载
     */
    fun loadHookItem(process: Int) {
        val allHookItems = HookItemFactory.getAllItemList()
        allHookItems.forEach { hookItem ->
            val path = hookItem.path
            if (hookItem is BaseSwitchFunctionHookItem) {
                hookItem.isEnabled = ConfigManager.getDefaultConfig().getBooleanOrFalse("$PrekXXX${hookItem.path}")
                if (hookItem.isEnabled && process == hookItem.targetProcess) {
                    Logger.i("[BaseSwitchFunctionHookItem] Initializing $path...")
                    hookItem.startLoad()
                }
            }
            else if (hookItem is BaseClickableFunctionHookItem) {
                hookItem.isEnabled = ConfigManager.getDefaultConfig().getBooleanOrFalse("$PrekClickableXXX${hookItem.path}")
                if (hookItem.isEnabled && process == hookItem.targetProcess) {
                    Logger.i("[BaseClickableFunctionHookItem] Initializing $path...")
                    hookItem.startLoad()
                }

                if (hookItem.alwaysRun) {
                    Logger.i("[BaseClickableFunctionHookItem-AlwaysRun] Initializing $path...")
                    hookItem.startLoad()
                }
            }
            else {
                if (hookItem is ApiHookItem && process == hookItem.targetProcess){
                    Logger.i("[API] Initializing $path...")
                    hookItem.startLoad()
                }
            }

            // 检查是否是菜单项并添加到BottomShortcutMenu中
            if (hookItem is IShortcutMenu) {
                try {
                    // 获取BottomShortcutMenu的companion object类
                    val companionClass = Class.forName("moe.ono.hooks.item.chat.BottomShortcutMenu\$Companion")
                    
                    // 获取companion object实例
                    val companionInstanceField = companionClass.getDeclaredField("INSTANCE")
                    companionInstanceField.isAccessible = true
                    val companionInstance = companionInstanceField.get(null)

                    // 获取menus字段并添加菜单项
                    val menusField = companionClass.getDeclaredField("menus")
                    menusField.isAccessible = true
                    val menusList = menusField.get(companionInstance) as java.util.ArrayList<IShortcutMenu>
                    menusList.add(hookItem as IShortcutMenu)
                } catch (e: Exception) {
                    Logger.e("Failed to add menu item to BottomShortcutMenu", e)
                }
            }
        }
    }

}