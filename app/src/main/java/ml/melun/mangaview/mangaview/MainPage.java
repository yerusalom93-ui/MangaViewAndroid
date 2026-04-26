package ml.melun.mangaview.mangaview;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import okhttp3.Response;

import ml.melun.mangaview.MainApplication;

import static ml.melun.mangaview.mangaview.MTitle.base_comic;


public class MainPage {
    private static final String MAIN_PAGE_CACHE_SUFFIX = "#main-page";
    List<Manga> recent, favUpdate, onlineRecent;
    List<RankingTitle> ranking;
    boolean needsCaptcha;

    public List<RankingManga> getWeeklyRanking() {
        return weeklyRanking;
    }

    List<RankingManga> weeklyRanking;

    void fetch(CustomHttpClient client) {
        initLists();

        try{
            Response r = client.mget("",true,null);
            if(r == null || r.body() == null)
                return;
            String body = r.body().string();
            if(body.contains("Connect Error: Connection timed out")){
                //adblock : try again
                r.close();
                fetch(client);
                return;
            }
            r.close();
            if(parseBody(body)){
                MainApplication.saveCachedText(cacheKey(client), body);
            }else{
                needsCaptcha = looksLikeCaptcha(body);
                return;
            }
        }catch(Exception e){
            e.printStackTrace();
        }

/*
        try{
            Response response = client.mget("");
            Document doc = Jsoup.parse(response.body().string());

            Elements list = doc.selectFirst("div.msm-post-gallery").select("div.post-row");
            for(Element e:list){
                String[] tmp_idStr = e.selectFirst("a").attr("href").toString().split("=");
                int tmp_id = Integer.parseInt(tmp_idStr[tmp_idStr.length-1]);
                String tmp_thumb = e.selectFirst("img").attr("src").toString();
                String tmp_title = e.selectFirst("img").attr("alt").toString();
                Manga tmp = new Manga(tmp_id,tmp_title,"");
                tmp.addThumb(tmp_thumb);
                recent.add(tmp);
            }
            Elements rankingWidgets = doc.select("div.rank-manga-widget");

            // online data
            Elements fav= rankingWidgets.get(0).select("li");
            rankingWidgetLiParser(fav, favUpdate);

            Elements rec = rankingWidgets.get(1).select("li");
            rankingWidgetLiParser(rec, onlineRecent);

            // ranking
            Elements rank = rankingWidgets.get(2).select("li");
            rankingWidgetLiParser(rank, ranking);

            //close response
            response.close();


        }catch (Exception e){
            e.printStackTrace();
        }
*/
    }

    private void initLists() {
        recent = new ArrayList<>();
        ranking = new ArrayList<>();
        weeklyRanking = new ArrayList<>();

        favUpdate = new ArrayList<>();
        onlineRecent = new ArrayList<>();
    }

    private boolean parseBody(String body) {
        try {
            Document d = Jsoup.parse(body);
            Elements galleries = d.select("div.miso-post-gallery");
            Elements postLists = d.select("div.miso-post-list");
            if(galleries.size() == 0 || postLists.size() == 0)
                return false;

            //recent
            int id;
            String name;
            String thumb;
            Manga mtmp;
            Element infos;
            Title ttmp;

            for(Element e : galleries.first().select("div.post-row")){
                id = Integer.parseInt(e.selectFirst("a").attr("href").split("comic/")[1]);
                infos = e.selectFirst("div.img-item");
                thumb = infos.selectFirst("img").attr("src");
                name = infos.selectFirst("b").ownText();

                mtmp = new Manga(id, name, "", base_comic);
                mtmp.addThumb(thumb);
                recent.add(mtmp);
            }

            int i=1;
            for(Element e : galleries.last().select("div.post-row")){
                id = Integer.parseInt(e.selectFirst("a").attr("href").split("comic/")[1]);
                infos = e.selectFirst("div.img-item");
                thumb = infos.selectFirst("img").attr("src");
                name = infos.selectFirst("div.in-subject").ownText();

                ranking.add(new RankingTitle(name, thumb, "", null, "", id, base_comic, i++));
            }

            i=1;
            for(Element e : postLists.last().select("li.post-row")){
                infos = e.selectFirst("a");
                id = Integer.parseInt(infos.attr("href").split("comic/")[1]);
                name = infos.ownText();

                System.out.println(name);
                weeklyRanking.add(new RankingManga(id, name, "", base_comic, i++));
            }
            return true;
        }catch(Exception e){
            e.printStackTrace();
            return false;
        }
    }

    public static MainPage fromCache(CustomHttpClient client) {
        String body = MainApplication.getCachedText(cacheKey(client));
        if(body == null || body.length() == 0)
            return null;
        MainPage cached = new MainPage();
        if(cached.parseBody(body))
            return cached;
        return null;
    }

    private static String cacheKey(CustomHttpClient client) {
        return client.getUrl() + MAIN_PAGE_CACHE_SUFFIX;
    }

    public static class RankingTitle extends Title{
        int ranking;
        public RankingTitle(String n, String t, String a, List<String> tg, String r, int id, int baseMode, int ranking) {
            super(n, t, a, tg, r, id, baseMode);
            this.ranking = ranking;
        }

        public int getRanking() {
            return ranking;
        }
    }
    public static class RankingManga extends Manga{
        int ranking;
        public RankingManga(int i, String n, String d, int baseMode, int ranking) {
            super(i, n, d, baseMode);
            this.ranking = ranking;
        }

        public int getRanking() {
            return ranking;
        }
    }

    void rankingWidgetLiParser(Elements input, List output){
        for(Element e: input){
            String[] tmp_link = e.selectFirst("a").attr("href").split("=");
            int tmp_id = Integer.parseInt(tmp_link[tmp_link.length-1]);
            String tmp_title = e.selectFirst("div.subject").ownText();
            output.add(new Manga(tmp_id, tmp_title,"", base_comic));
        }
    }
    public MainPage(CustomHttpClient client) {
        fetch(client);
    }

    private MainPage() {
        initLists();
    }

    private boolean looksLikeCaptcha(String body) {
        if(body == null)
            return false;
        String lower = body.toLowerCase();
        return lower.contains("<title>just a moment")
                || lower.contains("cf_chl_opt")
                || lower.contains("__cf_chl_rt_tk")
                || lower.contains("enable javascript and cookies to continue")
                || (lower.contains("captcha") && !lower.contains("miso-post-gallery"));
    }

    public boolean needsCaptcha() {
        return needsCaptcha;
    }

    public List<Manga> getRecent() {
        return recent;
    }

    public List<Manga> getFavUpdate() {
        return favUpdate;
    }

    public List<Manga> getOnlineRecent() {
        return onlineRecent;
    }

    public List<RankingTitle> getRanking() { return ranking; }
}
