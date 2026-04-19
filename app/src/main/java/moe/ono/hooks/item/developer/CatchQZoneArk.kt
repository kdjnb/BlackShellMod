package moe.ono.hooks.item.developer

import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import com.github.kyuubiran.ezxhelper.utils.invokeMethod
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.reflex.ClassUtils.findClass
import moe.ono.reflex.FieldUtils
import moe.ono.ui.CommonContextWrapper
import moe.ono.util.ContextUtils
import moe.ono.util.Logger
import java.lang.reflect.Field

@HookItem(path = "ARK/拦截 QQ 空间 ARK (R.I.P.)", description = "随便一个动态点分享\n* 仅适配 9.2.0")
class CatchQZoneArk : BaseSwitchFunctionHookItem() {
    override fun entry(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod("com.qzone.reborn.feedx.presenter.au", classLoader, "P", object : XC_MethodHook() {
                private val isConfirming = ThreadLocal.withInitial { false }

                override fun beforeHookedMethod(param: MethodHookParam) {
                    try {
                        if (!this@CatchQZoneArk.configIsEnable() || isConfirming.get() == true) return
                        val obj = param.thisObject?:return
                        val ugA = findClass("ug.a")?:return
                        var feedDataField : Field? = null
                        val fields = ugA.declaredFields
                        Logger.d(fields.size.toString())
                        val businessFeedDataClazz = findClass("com.qzone.proxy.feedcomponent.model.BusinessFeedData")?:return
                        for (field in fields) {
                            Logger.d(field.type.name.toString())
                            if (field.type.name.toString() == businessFeedDataClazz.name) {
                                field.isAccessible = true
                                if (field.get(obj) != null) {
                                    feedDataField = field
                                    break
                                }
                            }
                        }
                        if (feedDataField == null) return
                        val feedData = feedDataField.get(obj)?:return
                        val cellOperationInfo = FieldUtils.create(businessFeedDataClazz).fieldName("cellOperationInfo").firstValue<Any?>(feedData)?:return
                        val shareData = FieldUtils.create(cellOperationInfo).fieldName("shareData").firstValue<Any?>(cellOperationInfo)?:return
                        val ark_content = FieldUtils.create(shareData).fieldName("ark_content")
                        val ark_id = FieldUtils.create(shareData).fieldName("ark_id")
                        val sSummary = FieldUtils.create(shareData).fieldName("sSummary")
                        val sTitle = FieldUtils.create(shareData).fieldName("sTitle")
                        val view_id = FieldUtils.create(shareData).fieldName("view_id")
                        param.result = null
                        MaterialAlertDialogBuilder(
                            CommonContextWrapper.createAppCompatContext(ContextUtils.getCurrentActivity())
                        ).apply {
                            setTitle("已拦截")

                            val scrollView = ScrollView(context)

                            val layout = LinearLayout(context).apply {
                                orientation = LinearLayout.VERTICAL
                                setPadding(50, 30, 50, 10)
                            }

                            val arkContentEditText = EditText(context).apply {
                                hint = "ark_content"
                                setText(ark_content.firstValue<String?>(shareData))
                            }
                            val arkIdEditText = EditText(context).apply {
                                hint = "ark_id"
                                setText(ark_id.firstValue<String?>(shareData))
                            }
                            val summaryEditText = EditText(context).apply {
                                hint = "sSummary"
                                setText(sSummary.firstValue<String?>(shareData))
                            }
                            val titleEditText = EditText(context).apply {
                                hint = "sTitle"
                                setText(sTitle.firstValue<String?>(shareData))
                            }
                            val viewIdEditText = EditText(context).apply {
                                hint = "view_id"
                                setText(view_id.firstValue<String?>(shareData))
                            }

                            layout.addView(arkContentEditText)
                            layout.addView(arkIdEditText)
                            layout.addView(summaryEditText)
                            layout.addView(titleEditText)
                            layout.addView(viewIdEditText)

                            scrollView.addView(layout)
                            setView(scrollView)

                            setPositiveButton("确定") { _, _ ->
                                ark_content.setFirst(shareData, arkContentEditText.text.toString())
                                ark_id.setFirst(shareData, arkIdEditText.text.toString())
                                sSummary.setFirst(shareData, summaryEditText.text.toString())
                                sTitle.setFirst(shareData, titleEditText.text.toString())
                                view_id.setFirst(shareData, viewIdEditText.text.toString())
                                isConfirming.set(true)
                                obj.invokeMethod("P")
                                isConfirming.set(false)
                            }
                            show()
                        }
                    } catch (e: Exception) {
                        Logger.e(e)
                    }
                }
            })
        } catch (e: Exception) {
            Logger.e(e)
        }
    }
}
