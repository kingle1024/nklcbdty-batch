package com.nklcbdty.batch.nklcbdty.batch.crawler.run;

import java.util.Arrays;
import org.springframework.batch.item.support.ListItemReader;

public class CrawlerDataReader extends ListItemReader<String> {
    public CrawlerDataReader() {
        super(Arrays.asList(
            "https://port-0-nklcbdty-service-m6qh1fte0c037b76.sel4.cloudtype.app/api/crawler?company=naver",
            "https://port-0-nklcbdty-service-m6qh1fte0c037b76.sel4.cloudtype.app/api/crawler?company=kakao",
            "https://port-0-nklcbdty-service-m6qh1fte0c037b76.sel4.cloudtype.app/api/crawler?company=line",
            "https://port-0-nklcbdty-service-m6qh1fte0c037b76.sel4.cloudtype.app/api/crawler?company=coupang",
            "https://port-0-nklcbdty-service-m6qh1fte0c037b76.sel4.cloudtype.app/api/crawler?company=baemin",
            "https://port-0-nklcbdty-service-m6qh1fte0c037b76.sel4.cloudtype.app/api/crawler?company=daangn",
            "https://port-0-nklcbdty-service-m6qh1fte0c037b76.sel4.cloudtype.app/api/crawler?company=toss",
            "https://port-0-nklcbdty-service-m6qh1fte0c037b76.sel4.cloudtype.app/api/crawler?company=yanolja"
        ));
    }

}
