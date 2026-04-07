package moe.ono.util.analytics;

import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import moe.ono.config.ConfigManager;
import moe.ono.constants.Constants;
import moe.ono.hooks.item.developer.NoReport;
import moe.ono.util.Logger;
import static moe.ono.hooks._core.factory.HookItemFactory.getItem;
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
                        if (conn.getInputStream() != null) {
                            conn.getInputStream().close();
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