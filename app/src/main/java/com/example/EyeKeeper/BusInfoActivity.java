package com.example.EyeKeeper;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

//refresh 추가로 구현할 예정
public class BusInfoActivity extends AppCompatActivity {


    String nodeid=null;
    String citycode=null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();

        //Intent로 받은 nodeid, citycode
        Intent busInfoIntent=getIntent();
        nodeid=busInfoIntent.getStringExtra("nodeid");
        citycode=busInfoIntent.getStringExtra("citycode");

        Log.i("BusInfoActivity",nodeid+", "+citycode);

        //nodeid="DJB8001793"; citycode="25";

        //http 통신
        BusInfoThread busInfoThread=new BusInfoThread();
        busInfoThread.start();

    }

    public class BusInfoThread extends Thread{
        @Override
        public void run() {
            if(nodeid==null){
                Log.e("run()","nodeid에 null object");
                return;
            }
            try {
                StringBuilder urlBuilder = new StringBuilder("http://apis.data.go.kr/1613000/ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList"); /*URL*/
                urlBuilder.append("?" + URLEncoder.encode("serviceKey", "UTF-8") + "="+BuildConfig.BUSINFO_API_KEY); /*Service Key*/
                urlBuilder.append("&" + URLEncoder.encode("pageNo", "UTF-8") + "=" + URLEncoder.encode("1", "UTF-8")); /*페이지번호*/
                urlBuilder.append("&" + URLEncoder.encode("numOfRows", "UTF-8") + "=" + URLEncoder.encode("10", "UTF-8")); /*한 페이지 결과 수*/
                urlBuilder.append("&" + URLEncoder.encode("_type", "UTF-8") + "=" + URLEncoder.encode("json", "UTF-8")); /*데이터 타입(xml, json)*/
                urlBuilder.append("&" + URLEncoder.encode("cityCode", "UTF-8") + "=" + URLEncoder.encode(citycode, "UTF-8")); /*도시코드 [상세기능3 도시코드 목록 조회]에서 조회 가능*/
                urlBuilder.append("&" + URLEncoder.encode("nodeId", "UTF-8") + "=" + URLEncoder.encode(nodeid, "UTF-8")); /*정류소ID [국토교통부(TAGO)_버스정류소정보]에서 조회가능*/
                URL url = new URL(urlBuilder.toString());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Content-type", "application/json");
                System.out.println("Response code: " + conn.getResponseCode());
                BufferedReader rd;
                if (conn.getResponseCode() >= 200 && conn.getResponseCode() <= 300) {
                    rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                } else {
                    rd = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                }
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = rd.readLine()) != null) {
                    sb.append(line);
                }
                rd.close();
                conn.disconnect();

                String data=sb.toString();
                Log.i("Response",data);

                JSONObject jsonObject=new JSONObject(data);
                int totalCnt=jsonObject.getJSONObject("response").getJSONObject("body").getInt("totalCount");
                Log.i("totalCnt", String.valueOf(totalCnt));

                List<Bus> busList=new ArrayList<>();

                if(totalCnt==1){
                    JSONObject jo = jsonObject.getJSONObject("response").getJSONObject("body").getJSONObject("items").getJSONObject("item");
                    Bus bus=new Bus(jo.getInt("arrprevstationcnt"),jo.getInt("arrtime"),jo.getInt("routeno"),jo.getString("vehicletp"));
                    busList.add(bus);
                }else{
                    JSONArray jsonArray = jsonObject.getJSONObject("response").getJSONObject("body").getJSONObject("items").getJSONArray("item");

                    for(int i=0;i<jsonArray.length();i++){
                        jsonObject=jsonArray.getJSONObject(i);

                        int arrprevstationcnt=jsonObject.getInt("arrprevstationcnt");
                        int arrtime=jsonObject.getInt("arrtime");
                        int routeno=jsonObject.getInt("routeno");
                        String vehicletp=jsonObject.getString("vehicletp");

                        busList.add(new Bus(arrprevstationcnt,arrtime,routeno,vehicletp));
                    }
                }

                for(Bus bus:busList)
                    Log.i("버스 정보",bus.getStr());

            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    public class Bus{
        int arrprevstationcnt;//남은 버스정류장 개수
        int arrtime;//도착 예상시간(초)
        int routeno;//버스번호
        String vehicletp; //차량 종류(일반 차량 ... )

        public Bus(int arrprevstationcnt, int arrtime, int routeno, String vehicletp) {
            this.arrprevstationcnt = arrprevstationcnt;
            this.arrtime = arrtime;
            this.routeno = routeno;
            this.vehicletp = vehicletp;
        }

        public String getStr(){
            return Integer.toString(arrprevstationcnt)+", "+Integer.toString(arrtime)+", "+Integer.toString(routeno)+", "+vehicletp;
        }
    }
}
