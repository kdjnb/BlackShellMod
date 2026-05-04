package moe.ono.util.analytics;

import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import moe.ono.config.ConfigManager;
import moe.ono.constants.Constants;
import moe.ono.hooks.base.util.Toasts;
import moe.ono.util.AppRuntimeHelper;
import moe.ono.util.Logger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

public class ActionReporter {

    public static void setCurrentActivity(Activity activity) {
        currentActivity = activity;
    }

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static Activity currentActivity = null;

    // 在模块初始化时调用一次，注册 Activity 生命周期监听
    public static void init() {
        Application app = AppRuntimeHelper.getAppRuntime().getApplication();
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity activity) { currentActivity = activity; }
            @Override public void onActivityPaused(Activity activity) { if (currentActivity == activity) currentActivity = null; }
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) { if (currentActivity == a) currentActivity = null; }
        });
    }

    public static CompletableFuture<Boolean> showEulaDialog() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        new Handler(Looper.getMainLooper()).post(() -> {
            Activity activity = currentActivity;
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                // 没有可用 Activity，跳过弹窗，视为拒绝
                future.complete(true);
                return;
            }
            new AlertDialog.Builder(activity)
                    .setTitle("用户协议 / 隐私说明")
                    .setMessage("本模块会收集部分不匿名使用数据（如操作行为、时间等）用于功能优化与安全分析，本模块不会泄露您的使用信息与情况。是否同意？如果拒绝，模块将不会再向服务器传输数据。")
                    .setCancelable(false)
                    .setPositiveButton("同意", (dialog, which) -> {
                        ConfigManager.getDefaultConfig().putString(Constants.PrekXXX + "eula_status", "accepted");
                        future.complete(true);
                    })
                    .setNegativeButton("拒绝", (dialog, which) -> {
                        ConfigManager.getDefaultConfig().putString(Constants.PrekXXX + "eula_status", "rejected");
                        future.complete(false);
                    })
                    .show();
        });
        return future;
    }

    public static void reportVisitor(String qq, String action) {
        executor.execute(() -> {
            String eulaStatus = ConfigManager.dGetString(Constants.PrekXXX + "eula_status", "not_shown");

            if (eulaStatus.equals("not_shown")) {
                try {
                    boolean accepted = showEulaDialog().get();
                    if (!accepted) return;
                } catch (Exception ignored) {
                    return;
                }
            } else if (eulaStatus.equals("rejected")) {
                return;
            }

            HttpURLConnection conn = null;
            try {
                URL url = new URL("https://service.blackshellx.org/api/v1/bsmReport");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                JSONObject json = new JSONObject();
                json.put("uin", qq);
                json.put("action", action);
                json.put("time", System.currentTimeMillis() / 1000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    JSONObject resp = new JSONObject(sb.toString());
                    if (resp.optBoolean("notice", false)) {
                        Toasts.popup(resp.optString("notice_msg", ""));
                    }
                } else {
                    if (conn.getErrorStream() != null) conn.getErrorStream().close();
                }

                Logger.d("ActionReporter", "report: " + code);
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }
}