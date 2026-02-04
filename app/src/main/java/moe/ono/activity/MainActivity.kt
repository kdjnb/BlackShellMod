package moe.ono.activity

import android.content.ComponentName
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.flow.onEach
import moe.ono.BuildConfig
import moe.ono.R
import moe.ono.constants.PackageConstants
import moe.ono.databinding.ActivityMainBinding
import moe.ono.ui.view.BgEffectPainter
import moe.ono.util.CheckAbiVariantModel
import moe.ono.util.HostInfo
import moe.ono.util.Logger
import moe.ono.util.SyncUtils
import moe.ono.util.Utils.convertTimestampToDate
import moe.ono.util.Utils.jump
import moe.ono.util.analytics.ActionReporter.reportVisitor
import moe.ono.util.getEnable
import moe.ono.util.hookstatus.AbiUtils
import moe.ono.util.hookstatus.HookStatus
import moe.ono.util.setEnable

class MainActivity : AppCompatTransferActivity() {

    private lateinit var mBgEffectView: View
    private lateinit var mBgEffectPainter: BgEffectPainter
    private val startTime = System.nanoTime().toFloat()
    private val mHandler = Handler(Looper.getMainLooper())
    private lateinit var binding: ActivityMainBinding

    private val mHostAppPackages = setOf(
        PackageConstants.PACKAGE_NAME_QQ,
    )

    private val runnableBgEffect = object : Runnable {
        override fun run() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mBgEffectPainter.setAnimTime(((System.nanoTime() - startTime) / 1.0E9f) % 62.831852f)
                mBgEffectPainter.setResolution(floatArrayOf(mBgEffectView.width.toFloat(), mBgEffectView.height.toFloat()))
                mBgEffectPainter.updateMaterials()
                mBgEffectView.setRenderEffect(mBgEffectPainter.renderEffect)
                mHandler.postDelayed(this, 16L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HookStatus.init(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)

        binding.topAppBar.subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        binding.buildUUID.text = BuildConfig.BUILD_UUID
        binding.buildTime.text = convertTimestampToDate(BuildConfig.BUILD_TIMESTAMP)

        setHyperBackground()
        try {
            updateActivationStatus()
            SyncUtils.postDelayed(3000) { this.updateActivationStatus() }
        } catch (_: Exception) { }

    }

    override fun onResume() {
        Logger.d("MainActivity onResume")
        updateMenuItems()
        HookStatus.getXposedService().onEach {
            updateActivationStatus()
        }
        super.onResume()
    }

    private fun updateActivationStatus() {
        val isHookEnabledByLegacyApi = HookStatus.isModuleEnabled() || HostInfo.isInHostProcess()
        val xposedService: XposedService? = HookStatus.getXposedService().value
        val isHookEnabledByLibXposedApi = if (xposedService != null) {
            val scope = xposedService.scope.toSet()
            // check intersection
            mHostAppPackages.intersect(scope).isNotEmpty()
        } else false
        val isHookEnabled = isHookEnabledByLegacyApi || isHookEnabledByLibXposedApi
        var isAbiMatch = CheckAbiVariantModel.collectAbiInfo(this).isAbiMatch
        if ((isHookEnabled && HostInfo.isInModuleProcess() && !HookStatus.isZygoteHookMode()
                    && HookStatus.isTaiChiInstalled(this)) && HookStatus.getHookType() == HookStatus.HookType.APP_PATCH && "armAll" != AbiUtils.getModuleFlavorName()
        ) {
            isAbiMatch = false
        }
        val frameStatus = binding.cvActivationStatus
        val frameIcon = binding.activationStatusIcon
        val statusTitle = binding.activationStatusTitle
        val tvStatus = binding.activationStatusDesc
        if (isAbiMatch) {
            val color = ResourcesCompat.getColor(
                resources,
                if (isHookEnabled) R.color.usableColor else R.color.unusableColor,
                theme
            )

            frameStatus.setCardBackgroundColor(color)
            frameIcon.setImageDrawable(
                ResourcesCompat.getDrawable(
                    resources,
                    if (isHookEnabled) R.drawable.ic_check_circle_24 else R.drawable.ic_module_unavailable, theme
                )
            )
            statusTitle.text = if (isHookEnabled) "已激活" else "未激活"
            if (HostInfo.isInHostProcess()) {
                tvStatus.text = HostInfo.getPackageName()
            } else {
                tvStatus.text = if (isHookEnabledByLibXposedApi) {
                    val xp = xposedService!!
                    xp.frameworkName + " " + xp.frameworkVersion + " (" + xp.frameworkVersionCode + "), API " + xp.apiVersion
                } else {
                    HookStatus.getHookProviderNameForLegacyApi()
                }
            }
        } else {
            statusTitle.text = if (isHookEnabled) "未完全激活" else "未激活"
            tvStatus.text = "原生库不完全匹配"
        }
        try {
            val xp = xposedService!!
            reportVisitor("null", "UpdateActivationStatus-$-" + statusTitle.text + "("+xp.frameworkName + " " + xp.frameworkVersion + " (" + xp.frameworkVersionCode + "), API " + xp.apiVersion + ")")
        } catch (_: Exception) { }
    }


    /*
    * Hyper OS 2.0 !
    * */
    private fun setHyperBackground(){
        binding.cvActivationStatus.transitionAlpha = 0.8F
        binding.cvBuildInfo.transitionAlpha = 0.7F
        binding.cvOuompub.transitionAlpha = 0.85F
        binding.cvGithub.transitionAlpha = 0.85F

        DynamicColors.applyToActivitiesIfAvailable(application)

        val contentView = findViewById<ViewGroup>(android.R.id.content)

        mBgEffectView = LayoutInflater.from(this).inflate(R.layout.layout_effect_bg, contentView, false)
        contentView.addView(mBgEffectView, 0)
        mBgEffectView = contentView.findViewById(R.id.bgEffectView)

        mBgEffectView.post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mBgEffectPainter = BgEffectPainter(applicationContext)
                mBgEffectPainter.showRuntimeShader(applicationContext, mBgEffectView, binding.topAppBar, getResources().getBoolean(R.bool.is_night_mode))
            }

            mHandler.post(runnableBgEffect)
        }
    }

    fun handleClickEvent(view: View) {
        val id = view.id
        when (id) {
            R.id.cv_ouompub -> {
                jump(this, "https://t.me/bsmodx")
            }
            R.id.cv_github -> {
                jump(this, "https://github.com/kdjnb/BlackShellMod")
            }
        }
    }

    fun updateMenuItems() {
        if (HostInfo.isInHostProcess()) {
            return
        }
        val menu = binding.topAppBar.menu
        if (menu != null) {
            menu.removeItem(R.id.main_activity_menuItem_toggleDesktopIcon)
            menu.add(
                Menu.CATEGORY_SYSTEM, R.id.main_activity_menuItem_toggleDesktopIcon, 0,
                if (isLauncherIconEnabled) "隐藏桌面图标" else "显示桌面图标"
            )
        }
    }

    @set:UiThread
    @get:UiThread
    var isLauncherIconEnabled: Boolean
        get() {
            val componentName = ComponentName(this, ALIAS_ACTIVITY_NAME)
            return componentName.getEnable(this)
        }
        set(enabled) {
            val componentName = ComponentName(this, ALIAS_ACTIVITY_NAME)
            componentName.setEnable(this, enabled)
        }

    companion object {
        private const val ALIAS_ACTIVITY_NAME = "moe.ono.activity.MainActivityAlias"
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        when (id){
            R.id.main_activity_menuItem_toggleDesktopIcon -> {
                if (isLauncherIconEnabled){
                    MaterialAlertDialogBuilder(this)
                        .setTitle("你知道如何恢复桌面图标吗？")
                        .setMessage("隐藏桌面图标后你可以通过 Xposed 框架重新打开该模块的主界面，在了解这一点后，你现在可以选择是否隐藏桌面图标。")
                        .setPositiveButton("确认") { _: DialogInterface?, _: Int ->
                            isLauncherIconEnabled = !isLauncherIconEnabled
                            SyncUtils.postDelayed({ this.updateMenuItems() }, 500)
                        }
                        .setNegativeButton("取消") { _: DialogInterface, _: Int -> }
                        .show()
                } else {
                    isLauncherIconEnabled = !isLauncherIconEnabled
                    SyncUtils.postDelayed({ this.updateMenuItems() }, 500)
                }


            }
            R.id.menu_main_about -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle("ono")
                    .setMessage("一个 QQ 功能增强模块\n源代码：https://github.com/cwuom/ono")
                    .show()
            }
            else -> {
                return super@MainActivity.onOptionsItemSelected(item)
            }
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        if (HostInfo.isInModuleProcess()) {
            menuInflater.inflate(R.menu.ouo_main_activity_menu, menu)
            updateMenuItems()
        }
        return true
    }

}
