package moe.ono.hooks.base.api

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.children
import moe.ono.R
import moe.ono.hooks._base.ApiHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.reflex.FieldUtils
import moe.ono.util.AppRuntimeHelper
import moe.ono.util.Logger

@HookItem(path = "API/胎记")
class TagIcon : ApiHookItem() {
    override fun entry(classLoader: ClassLoader) {
        QQSendMsgListener.attributeInfoListeners.add { _, attributeInfos ->
            attributeInfos[0 as Integer]?.let {
                it.vasMsgInfo?.bubbleInfo?.bubbleDiyTextId = 114515
                attributeInfos[0 as Integer] = it
            }
        }

        QQMessageViewListener.addMessageViewUpdateListener(
            this,
            object : QQMessageViewListener.OnChatViewUpdateListener {
                override fun onViewUpdateAfter(
                    msgItemView: View,
                    msgRecord: Any
                ) {
                    try {
                        val msgAttrs: HashMap<Integer, Any> = FieldUtils.create(msgRecord)
                            .fieldName("msgAttrs")
                            .fieldType(HashMap::class.java)
                            .firstValue(msgRecord)

                        val msgAttr = msgAttrs[0 as Integer] ?: return

                        val vasMsgInfo: Any = FieldUtils.create(msgAttr)
                            .fieldName("vasMsgInfo")
                            .firstValue(msgAttr)

                        val bubbleInfo: Any = FieldUtils.create(vasMsgInfo)
                            .fieldName("bubbleInfo")
                            .firstValue(vasMsgInfo)

                        val bubbleDiyTextId: Integer? = FieldUtils.create(bubbleInfo)
                            .fieldName("bubbleDiyTextId")
                            .fieldType(Integer::class.java)
                            .firstValue(bubbleInfo)

                        val senderUin: Long = FieldUtils.create(msgRecord)
                            .fieldName("senderUin")
                            .fieldType(Long::class.java)
                            .firstValue(msgRecord)

                        val rootView = msgItemView as ViewGroup
                        if (!QQMsgViewAdapter.hasContentMessage(rootView)) return

                        val targetLayout = rootView.children.find {
                            if (it is FrameLayout) {
                                if (it.childCount == 1 && it.getChildAt(0) is LinearLayout) {
                                    val linearLayout = it.getChildAt(0) as LinearLayout
                                    if (
                                        (linearLayout.childCount == 2 &&
                                                linearLayout.children.find { it1 -> it1.tag == 114514 } != null) ||
                                        (linearLayout.childCount == 1 &&
                                                linearLayout.getChildAt(0) is LinearLayout)
                                    ) {
                                        return@find true
                                    }
                                }
                            }
                            false
                        }

                        if (targetLayout is FrameLayout) {
                            val target = targetLayout.getChildAt(0) as? ViewGroup ?: return

                            if (target.children.find { it.tag == 114514 } != null) {
                                if (bubbleDiyTextId != 114515 as Integer) {
                                    target.children.toList().forEach {
                                        if (it.tag == 114514) {
                                            target.removeView(it)
                                        }
                                    }
                                }
                                return
                            }

                            val size =
                                (16 * target.context.resources.displayMetrics.density).toInt()
                            val margin =
                                (4 * target.context.resources.displayMetrics.density).toInt()

                            fun dp(context: Context, value: Float): Float {
                                return value * context.resources.displayMetrics.density
                            }

                            val icon = ImageView(target.context).apply {
                                setImageResource(R.drawable.ic_ouo)
                                scaleType = ImageView.ScaleType.FIT_CENTER
                                adjustViewBounds = true
                                layoutParams = ViewGroup.MarginLayoutParams(size, size).apply {
                                    leftMargin = margin
                                    rightMargin = margin
                                }
                                tag = 114514
                                clipToOutline = true
                                outlineProvider = ViewOutlineProvider.BACKGROUND
                                background = GradientDrawable().apply {
                                    cornerRadius = dp(context, 3f)
                                }
                            }

                            if (bubbleDiyTextId == 114515 as Integer) {
                                if (senderUin == AppRuntimeHelper.getLongAccountUin()) {
                                    target.addView(icon)
                                } else {
                                    target.addView(icon, 0)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Logger.e(e)
                    }
                }
            }
        )
    }
}