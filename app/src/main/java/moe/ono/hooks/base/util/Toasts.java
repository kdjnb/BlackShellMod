package moe.ono.hooks.base.util;

import static moe.ono.util.Initiator.loadClass;
import static moe.ono.util.SyncUtils.runOnUiThread;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;

import moe.ono.hooks._base.ApiHookItem;
import moe.ono.hooks._core.annotation.HookItem;
import moe.ono.util.AppRuntimeHelper;
import moe.ono.util.Logger;

@HookItem(path = "API/Toasts")
public class Toasts extends ApiHookItem {
    private static Method showQQToastInUiThreadMethod;

    public static final int TYPE_INFO = 0;
    public static final int TYPE_ERROR = 1;
    public static final int TYPE_SUCCESS = 2;

    public static void show(Context ctx, int type, @NonNull CharSequence text) {
        runOnUiThread(() -> {
            try {
                showQQToastInUiThreadMethod.invoke(null, type, text);
            } catch (Throwable e) {
                Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show();
            }
        });

    }

    public static void info(Context ctx, @NonNull CharSequence text) {
        show(ctx, TYPE_INFO, text);
    }

    public static void success(Context ctx, @NonNull CharSequence text) {
        show(ctx, TYPE_SUCCESS, text);
    }

    public static void error(Context ctx, @NonNull CharSequence text) {
        show(ctx, TYPE_ERROR, text);
    }

    public static void popup(@NonNull CharSequence text){
        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        mainHandler.post(() -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("mqqapi://relation/deleteFriends?src_type=app&version=1&uins=%s,%s&title=" + text));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            AppRuntimeHelper.getAppRuntime().getApplication().startActivity(intent);
        });
    }

    @Override
    public void entry(@NonNull ClassLoader classLoader) throws Throwable {
        try {
            Class<?> QQToastUtilClass = loadClass("com.tencent.util.QQToastUtil");
            showQQToastInUiThreadMethod = QQToastUtilClass.getDeclaredMethod("showQQToastInUiThread", int.class, String.class);
        } catch (Exception e) {
            Logger.e("QQToastUtil Not Found : ", e);
        }
    }
}