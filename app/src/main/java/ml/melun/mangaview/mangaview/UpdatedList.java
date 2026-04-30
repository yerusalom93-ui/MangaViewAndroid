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
                Response response= client.mget(url + requestedPage,true,null);
                int code = response == null ? 500 : response.code();
                String body = CustomHttpClient.readBody(response);
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
                        String img = item.selectFirst("img").attr("src");
                        String name = item.selectFirst("div.post-subject").selectFirst("a").ownText();
                        int id = Integer.parseInt(item
                                .selectFirst("div.pull-left")
                                .selectFirst("a")
                                .attr("href")
                                .split("comic/")[1]);

                        Elements rightInfo = item.selectFirst("div.pull-right").select("p");

                        int tid = Integer.parseInt(rightInfo
                                .get(0)
                                .selectFirst("a")
                                .attr("href")
                                .split("comic/")[1]);

                        String date = rightInfo.get(1).selectFirst("span").ownText();


                        String at = item.selectFirst("div.post-text").ownText();
                        //작가 작가 태그1,태그2,태그3
                        String author = at.substring(0,at.lastIndexOf(' '));

                        List<String> tags = Arrays.asList(at.substring(at.lastIndexOf(' ')).split(","));

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

    public ArrayList<UpdatedManga> getResult() {
        return result;
    }
    public boolean isLast(){return last;}


}
