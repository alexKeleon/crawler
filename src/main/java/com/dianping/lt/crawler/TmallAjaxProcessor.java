package com.dianping.lt.crawler;

import com.dianping.cache.util.JsonUtils;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by yichaowu on 16/10/29.
 */
public class TmallAjaxProcessor {
    private final static String ajaxuri = "https://mdskip.taobao.com/core/initItemDetail.htm?isApparel=true&service3C=false&offlineShop=false&isSecKill=false&tryBeforeBuy=false&sellerPreview=false&isForbidBuyItem=false&isAreaSell=false&queryMemberRight=true&tmallBuySupport=true&cartEnable=false&showShopProm=false&isRegionLevel=false&isUseInventoryCenter=false&addressLevel=2&household=false&isPurchaseMallPage=false&callback=setMdskip";

    private static ExecutorService executorService = Executors.newFixedThreadPool(10);
    private static ConcurrentHashMap<String, String> URLUCMAP = new ConcurrentHashMap<String, String>();

    public static String doGet(String url) {
        HttpClient httpClient = new DefaultHttpClient();
        HttpGet httpGet = new HttpGet(url);
        httpGet.addHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.71 Safari/537.36");
        httpGet.addHeader(HttpHeaders.REFERER, "https://detail.tmall.com/item.htm?spm=a1z10.3745-b-s.w4948-15207243429.6.1Ajk0o&id=36568777129");
        String tmallDetail = "";
        try {
            HttpResponse httpResponse = httpClient.execute(httpGet);
            tmallDetail = EntityUtils.toString(httpResponse.getEntity());
            System.out.println(tmallDetail);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (StringUtils.isEmpty(tmallDetail)) {
            System.out.println("没能获取到货品详情");
            return "0";
        }
        String skuDetailJson = StringUtils.substring(tmallDetail, tmallDetail.indexOf("(") + 1, tmallDetail.indexOf(")"));
        Map<String, Object> map = null;
        try {
            map = JsonUtils.fromStr(skuDetailJson, Map.class);
            Map<String, Object> wrtInfo = null;
            try {
                Map<String, Object> defaultModelMap = (Map<String, Object>)map.get("defaultModel");
                Map<String, Object> itemPriceResultDO = (Map<String, Object>)defaultModelMap.get("itemPriceResultDO");
                Map<String, Object> priceInfo = (Map<String, Object>)itemPriceResultDO.get("priceInfo");
                Map.Entry<String, Object> entry = priceInfo.entrySet().iterator().next();
                Map<String, Object> pricechild = (Map<String, Object>)entry.getValue();
                wrtInfo = (Map<String, Object>)pricechild.get("wrtInfo");
            } catch (Exception e) {

            }

            String groupUC = "0";
            if (wrtInfo != null) {
                groupUC = (String) wrtInfo.get("groupUC");
                if (StringUtils.isEmpty(groupUC)) {
                    groupUC = "0";
                }
            }
            System.out.println("itemid=" + ",预定个数:" + groupUC);
            return groupUC;
        } catch (Exception e) {
            e.printStackTrace();
            return "0";
        }

    }

    public static void genExcelData(String path) throws Exception {
        FileInputStream file = null;
        try {
            file = new FileInputStream(path);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }


        XSSFWorkbook xssfWorkbook = null;
        try {
            xssfWorkbook = new XSSFWorkbook(file);
        } catch (Exception e) {
            e.printStackTrace();
        }

        XSSFWorkbook writenWorkBook = new XSSFWorkbook();

        final XSSFWorkbook workbook = xssfWorkbook;
        List<String> sheetnameList = Lists.newArrayList("ecco男鞋list", "ecco女鞋list", "geox男鞋list", "geox女鞋list");
        for (final String sheetName : sheetnameList) {
            try {
                System.out.println("开始处理" + sheetName);
                processSheet(sheetName, workbook, writenWorkBook);
                System.out.println("结束处理" + sheetName);

            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }

        writeExcel(writenWorkBook);
        System.out.println("excel 处理完了。。。");
    }

    private static void processSheet(String sheetName, XSSFWorkbook xssfWorkbook, XSSFWorkbook wirtenWorkBook) throws Exception {
        XSSFSheet eccoManSheet = xssfWorkbook.getSheet(sheetName);
        List<String> eccoManList = processRow(eccoManSheet);
        final CountDownLatch urllatch = new CountDownLatch(eccoManList.size());
        for (final String url : eccoManList) {
            final String ajax = genAjax(url);
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        String groupUC = doGet(ajax);
                        URLUCMAP.put(url, groupUC);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    urllatch.countDown();
                }
            });


        }
        urllatch.await();

        genWritenExcel(wirtenWorkBook, sheetName, URLUCMAP);
        URLUCMAP.clear();

    }

//    public static class SheetThread implements Runnable {
//        private String sheetName;
//        private XSSFWorkbook xssfWorkbook;
//        public SheetThread(String sheetName, XSSFWorkbook xssfWorkbook) {
//            this.sheetName = sheetName;
//            this.xssfWorkbook = xssfWorkbook;
//        }
//        @Override
//        public void run() {
//            processSheet(sheetName, xssfWorkbook);
//        }
//    }

    private static String genAjax(String detailUrl) {

        try {
            URL url = new URL(detailUrl);
            String query = url.getQuery();
            String[] kv = query.split("&");
            for (int i = 0; i < kv.length; ++i) {
                if (kv[i].startsWith("id=")) {
                    String[] values = kv[i].split("=");
                    return ajaxuri + "&itemId=" + values[1];
                }
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return "";
    }

    private static List<String> processRow(XSSFSheet xssfSheet) {
        List<String> urlList = new ArrayList<String>();
        int rowstart = xssfSheet.getFirstRowNum();
        int rowEnd = xssfSheet.getLastRowNum();
        for (int i = rowstart + 1; i <= rowEnd; i++) {
            XSSFRow row = xssfSheet.getRow(i);
            if(null == row) continue;
            XSSFCell cell = row.getCell(1);
            String url = "";
            if (cell != null && cell.getStringCellValue() != null) {
                url = cell.getStringCellValue();
            } else {
                continue;
            }
            urlList.add(url);
            System.out.println(url);

        }
        return urlList;

    }

    public static void genWritenExcel(XSSFWorkbook xssfWorkbook, String sheetName, Map<String, String> map) throws IOException {

        XSSFSheet xssfSheet = xssfWorkbook.createSheet(sheetName);
        int rownum = 0;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            XSSFRow row = xssfSheet.createRow(rownum);
            XSSFCell cell0 = row.createCell(0);
            cell0.setCellValue(entry.getKey());
            XSSFCell cell1 = row.createCell(1);
            cell1.setCellValue(entry.getValue());
            ++rownum;
        }
    }

    public static void writeExcel(XSSFWorkbook xssfWorkbook) throws IOException {
        FileOutputStream os= null;
        try {
            os = new FileOutputStream("天猫生成预定数据.xlsx");
            xssfWorkbook.write(os);
            os.flush();
            os.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void main(String args[]) {
        String readExcelpath = args[0];
        try {
            TmallAjaxProcessor.genExcelData(readExcelpath);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.exit(0);

    }




}

