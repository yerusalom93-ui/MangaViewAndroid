package ml.melun.mangaview;

import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static ml.melun.mangaview.MainApplication.httpClient;
import static ml.melun.mangaview.MainApplication.p;

public class UrlUpdater extends AsyncTask<Void, Void, Boolean> {
    private static final int MAX_INCREMENT_SCAN = 20;
    private static final int PARALLEL_SCAN_SIZE = 6;
    private static final int FAST_TIMEOUT_SECONDS = 1;
    private static final Pattern MANATOKI_NUMBERED_URL = Pattern.compile("^(https?://manatoki)(\\d+)(\\.net)(/.*)?$");

    String result;
    String fetchUrl;
    boolean silent = false;
    Context c;
    UrlUpdaterCallback callback;
    public static volatile boolean running = false;

    public UrlUpdater(Context c){
        this.c = c;
        this.fetchUrl = p.getDefUrl();
    }

    public UrlUpdater(Context c, boolean silent, UrlUpdaterCallback callback, String defUrl){
        this.c = c;
        this.silent = silent;
        this.callback = callback;
        this.fetchUrl = defUrl;
    }

    protected void onPreExecute() {
        running = true;
        if(!silent)
            Toast.makeText(c, "Finding current site URL...", Toast.LENGTH_SHORT).show();
    }

    protected Boolean doInBackground(Void... params) {
        return fetch();
    }

    protected Boolean fetch(){
        try {
            String numberedUrl = findLatestNumberedManatoki();
            if(numberedUrl != null){
                result = numberedUrl;
                return true;
            }

            Response r = requestUrl(fetchUrl, false);
            if (r == null)
                return false;

            if (r.code() == 302) {
                result = normalizeUrl(r.header("Location"));
                r.close();
                return result != null;
            }
            r.close();
            return false;

        }catch (Exception e){
            e.printStackTrace();
            return false;
        }
    }

    private Response requestUrl(String url, boolean fast) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", httpClient.agent);
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        headers.put("Referer", p.getUrl());
        String cookie = httpClient.getCookieHeader();
        if(cookie.length() > 0)
            headers.put("Cookie", cookie);
        if(!fast)
            return httpClient.get(normalizeInputUrl(url), headers);

        try {
            OkHttpClient fastClient = httpClient.client.newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .connectTimeout(FAST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .readTimeout(FAST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .build();
            Request.Builder builder = new Request.Builder()
                    .url(normalizeInputUrl(url))
                    .get();
            for(String k : headers.keySet()){
                builder.addHeader(k, headers.get(k));
            }
            return fastClient.newCall(builder.build()).execute();
        } catch (Exception e){
            if(!fast)
                e.printStackTrace();
            return null;
        }
    }

    private String findLatestNumberedManatoki() {
        Matcher matcher = MANATOKI_NUMBERED_URL.matcher(normalizeInputUrl(fetchUrl));
        if(!matcher.matches())
            return null;
        int number = Integer.parseInt(matcher.group(2));
        return findLatestNumberedManatoki(matcher.group(1), matcher.group(3), number);
    }

    private String findLatestNumberedManatoki(String prefix, String suffix, int seedNumber) {
        String seedUrl = prefix + seedNumber + suffix;
        CheckResult seed = checkCandidate(seedUrl, seedNumber);
        if(seed.usable)
            return seed.url;

        ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_SCAN_SIZE);
        List<Future<CheckResult>> futures = new ArrayList<>();
        try {
            for(int number = seedNumber + 1; number <= seedNumber + MAX_INCREMENT_SCAN; number++){
                final int candidateNumber = number;
                final String candidate = prefix + number + suffix;
                futures.add(executor.submit(new Callable<CheckResult>() {
                    @Override
                    public CheckResult call() {
                        return checkCandidate(candidate, candidateNumber);
                    }
                }));
            }

            CheckResult best = null;
            for(Future<CheckResult> future : futures){
                CheckResult result = future.get();
                if(result.usable && (best == null || result.number > best.number))
                    best = result;
            }
            return best == null ? null : best.url;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            executor.shutdownNow();
        }
    }

    private CheckResult checkCandidate(String candidate, int fallbackNumber) {
        Response response = requestUrl(candidate, true);
        if(response == null)
            return new CheckResult(candidate, fallbackNumber, false);
        try {
            int code = response.code();
            String location = response.header("Location");
            if(location != null && location.contains("manatoki")){
                String url = normalizeUrl(location);
                return new CheckResult(url, extractNumber(url, fallbackNumber), true);
            }
            if(!isUsableStatus(code))
                return new CheckResult(candidate, fallbackNumber, false);
            String body = response.body().string();
            boolean usable = isManatokiMainPage(body) || looksLikeCloudflareChallenge(body);
            return new CheckResult(candidate, fallbackNumber, usable);
        } catch (Exception e) {
            return new CheckResult(candidate, fallbackNumber, false);
        } finally {
            response.close();
        }
    }

    private boolean isManatokiMainPage(String body) {
        return body != null && (body.contains("miso-post-gallery")
                || body.contains("miso-post-list")
                || body.contains("/comic/")
                || body.contains("/webtoon/"));
    }

    private boolean looksLikeCloudflareChallenge(String body) {
        if(body == null)
            return false;
        String lower = body.toLowerCase();
        return lower.contains("<title>just a moment")
                || lower.contains("cf_chl_opt")
                || lower.contains("__cf_chl_rt_tk")
                || lower.contains("enable javascript and cookies to continue")
                || lower.contains("performing security verification");
    }

    private boolean isUsableStatus(int code) {
        return code >= 200 && code < 400;
    }

    private String normalizeInputUrl(String url) {
        if(url == null || url.trim().length() == 0)
            return "";
        url = url.trim();
        int queryStart = url.indexOf("?");
        if(queryStart > -1)
            url = url.substring(0, queryStart);
        if(!url.startsWith("http://") && !url.startsWith("https://"))
            url = "https://" + url;
        return normalizeUrl(url);
    }

    private String normalizeUrl(String url) {
        if(url == null)
            return null;
        int protocolEnd = url.indexOf("://");
        int searchStart = protocolEnd >= 0 ? protocolEnd + 3 : 0;
        int pathStart = url.indexOf("/", searchStart);
        if(pathStart > 0)
            return url.substring(0, pathStart);
        return url;
    }

    private int extractNumber(String url, int fallback) {
        if(url == null)
            return fallback;
        Matcher matcher = MANATOKI_NUMBERED_URL.matcher(normalizeInputUrl(url));
        if(matcher.matches())
            return Integer.parseInt(matcher.group(2));
        return fallback;
    }

    private static class CheckResult {
        String url;
        int number;
        boolean usable;

        CheckResult(String url, int number, boolean usable) {
            this.url = url;
            this.number = number;
            this.usable = usable;
        }
    }

    protected void onPostExecute(Boolean r) {
        running = false;
        if(r && result !=null){
            p.setUrl(result);
            if(!silent)
                Toast.makeText(c, "Site URL set: " + result, Toast.LENGTH_SHORT).show();
            if(callback!=null)
                callback.callback(true);
        }else{
            if(!silent)
                Toast.makeText(c, "Could not find the current site URL. Try again later.", Toast.LENGTH_LONG).show();
            if(callback!=null)
                callback.callback(false);
        }
    }

    public interface UrlUpdaterCallback{
        void callback(boolean success);
    }
}
