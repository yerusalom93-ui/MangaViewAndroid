package ml.melun.mangaview.mangaview;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import okhttp3.Response;


public class UpdatedList {
    private static final long PAGE_CACHE_TTL_MS = 30 * 1000L;
    private static final int MAX_TIMEOUT_RETRIES = 2;

    Boolean last = false;
    ArrayList<UpdatedManga> result;
    int page = 1;
    int baseMode;
    int timeoutRetries = 0;

    public UpdatedList(int baseMode){
        this.baseMode = baseMode;
    }

    public int getPage(){
        return this.page;
    }

    public void fetch(CustomHttpClient client){
        //50 items per page
        result = new ArrayList<>();
        String url = "/bbs/page.php?hid=update&page=";
        if(last)
            return;

        int requestedPage = page;
        for(int attempt = 0; attempt <= MAX_TIMEOUT_RETRIES; attempt++) {
            try {
                CustomHttpClient.PageResponse pageResponse = client.mgetCachedPage(url + requestedPage, PAGE_CACHE_TTL_MS);
                int code = pageResponse.code;
                String body = pageResponse.body;
                if(code >= 400)
                    return;
                if(body.contains("Connect Error: Connection timed out")){
                    timeoutRetries = attempt + 1;
                    continue;
                }
                Document document = Jsoup.parse(body);
                Elements items = document.select("div.post-row");
                page = requestedPage + 1;
                if (items == null || items.size() < 70) last = true;
                for(Element item : items){
                    try {
                        Element imgElement = item.selectFirst("img");
                        Element subjectLink = item.selectFirst("div.post-subject a");
                        Element episodeLink = item.selectFirst("div.pull-left a[href*=comic/]");
                        Element right = item.selectFirst("div.pull-right");
                        Element text = item.selectFirst("div.post-text");
                        if(imgElement == null || subjectLink == null || episodeLink == null || right == null || text == null)
                            continue;
                        String img = imgElement.attr("src");
                        String name = subjectLink.ownText();
                        int id = parseComicId(episodeLink.attr("href"));
                        if(id <= 0)
                            continue;

                        Elements rightInfo = right.select("p");
                        if(rightInfo.size() < 2)
                            continue;
                        Element titleLink = rightInfo.get(0).selectFirst("a[href*=comic/]");
                        Element dateSpan = rightInfo.get(1).selectFirst("span");
                        if(titleLink == null || dateSpan == null)
                            continue;
                        int tid = parseComicId(titleLink.attr("href"));
                        if(tid <= 0)
                            continue;

                        String date = dateSpan.ownText();


                        String at = text.ownText();
                        //작가 작가 태그1,태그2,태그3
                        int split = at.lastIndexOf(' ');
                        String author = split > 0 ? at.substring(0, split) : "";

                        List<String> tags = split > 0 ? Arrays.asList(at.substring(split).split(",")) : new ArrayList<>();

                        UpdatedManga tmp = new UpdatedManga(id, name, date, baseMode,author,tags);
                        tmp.setMode(0);
                        tmp.setTitle(new Title(name, img, author, tags, "", tid, MTitle.base_comic));
                        tmp.addThumb(img);
                        result.add(tmp);
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }
                timeoutRetries = 0;
                return;
            } catch (Exception e) {
                e.printStackTrace();
                timeoutRetries = 0;
                return;
            }
        }
        timeoutRetries = 0;
    }

    private int parseComicId(String href) {
        try {
            if(href == null || !href.contains("comic/"))
                return -1;
            return Integer.parseInt(href.split("comic/")[1].split("\\?")[0]);
        } catch (Exception e) {
            return -1;
        }
    }

    public ArrayList<UpdatedManga> getResult() {
        return result;
    }
    public boolean isLast(){return last;}


}
