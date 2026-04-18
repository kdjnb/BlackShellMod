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
import moe.ono.hooks.item.developer.NoReport;
import moe.ono.util.AppRuntimeHelper;
import moe.ono.util.Logger;
import static moe.ono.hooks._core.factory.HookItemFactory.getItem;

import android.content.Intent;
import android.net.Uri;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ActionReporter {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static void reportVisitor(String qq, String action) {
        boolean noReport = ConfigManager.getDefaultConfig()
                .getBooleanOrFalse(Constants.PrekXXX + getItem(NoReport.class).getPath());

        if (!noReport) {
            executor.execute(() -> {
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
                        while ((line = br.readLine()) != null) {
                            sb.append(line);
                        }
                        br.close();

                        JSONObject resp = new JSONObject(sb.toString());
                        if (resp.optBoolean("notice", false)) {
                            String noticeMsg = resp.optString("notice_msg", "");
                            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                            mainHandler.post(() -> {
                                Intent intent = new Intent(Intent.ACTION_VIEW);
                                intent.setData(Uri.parse("mqqapi://relation/deleteFriends?src_type=app&version=1&uins=%s,%s&title=" + noticeMsg));
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                AppRuntimeHelper.getAppRuntime().getApplication().startActivity(intent);
                            });
                        }
                    } else {
                        if (conn.getErrorStream() != null) {
                            conn.getErrorStream().close();
                        }
                    }

                    Logger.d("ActionReporter", "report: " + code);

                } catch (Exception ignored) {
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            });
        }
    }
}