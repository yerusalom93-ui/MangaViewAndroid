package ml.melun.mangaview;

import android.content.Context;
import android.webkit.CookieManager;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.multidex.MultiDexApplication;

import org.acra.ACRA;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.DialogConfigurationBuilder;
import org.acra.config.MailSenderConfigurationBuilder;
import org.acra.data.StringFormat;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

import ml.melun.mangaview.mangaview.CustomHttpClient;



//@AcraCore(reportContent = { APP_VERSION_NAME, ANDROID_VERSION, PHONE_MODEL, STACK_TRACE, REPORT_ID})


public class MainApplication extends MultiDexApplication {
    public static CustomHttpClient httpClient;
    public static Preference p;
    private static MainApplication app;
    private static final Map<String, String> verifiedPageHtml = new HashMap<>();

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        System.out.println("main app start");
        ACRA.init(this, new CoreConfigurationBuilder()
                .withBuildConfigClass(BuildConfig.class)
                .withReportFormat(StringFormat.JSON)
                .withPluginConfigurations(
                        new MailSenderConfigurationBuilder().withMailTo("mangaview@protonmail.com").build(),
                        new DialogConfigurationBuilder()
                                .withTitle("MangaView")
                                .withText(getResources().getText(R.string.acra_dialog_text).toString())
                                .withPositiveButtonText("확인")
                                .withNegativeButtonText("취소")
                                .build()
                ));
    }

    @Override
    public void onCreate() {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        app = this;
        p = new Preference(this);
        httpClient = new CustomHttpClient();
        restoreWebViewCookies();
        super.onCreate();
    }

    public static void saveVerifiedPageHtml(String url, String html) {
        if (url == null || html == null || html.length() == 0)
            return;
        String key = normalizeUrl(url);
        synchronized (verifiedPageHtml) {
            verifiedPageHtml.put(key, html);
        }
        if (app == null)
            return;
        try (FileOutputStream output = app.openFileOutput(cacheName(key), Context.MODE_PRIVATE)) {
            output.write(html.getBytes("UTF-8"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getVerifiedPageHtml(String url) {
        if (url == null)
            return null;
        String key = normalizeUrl(url);
        synchronized (verifiedPageHtml) {
            String html = verifiedPageHtml.get(key);
            if (html != null)
                return html;
        }
        if (app == null)
            return null;
        try (FileInputStream input = app.openFileInput(cacheName(key))) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) > 0) {
                output.write(buffer, 0, read);
            }
            String html = output.toString("UTF-8");
            synchronized (verifiedPageHtml) {
                verifiedPageHtml.put(key, html);
            }
            return html;
        } catch (Exception e) {
            return null;
        }
    }

    public static String normalizeUrl(String url) {
        int query = url.indexOf('?');
        if (query > -1)
            url = url.substring(0, query);
        while (url.endsWith("/") && url.length() > 1) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static String cacheName(String key) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(key.getBytes("UTF-8"));
        StringBuilder builder = new StringBuilder("verified_page_");
        for (byte b : hash) {
            String hex = Integer.toHexString(b & 0xff);
            if (hex.length() == 1)
                builder.append('0');
            builder.append(hex);
        }
        builder.append(".html");
        return builder.toString();
    }

    private void restoreWebViewCookies() {
        try {
            String url = p.getUrl();
            if(url == null || url.length() == 0)
                url = p.getDefUrl();
            if(url == null || !url.startsWith("http"))
                return;
            httpClient.setCookies(p.getSavedCookies());
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            String cookies = cookieManager.getCookie(url);
            httpClient.setCookies(cookies);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
