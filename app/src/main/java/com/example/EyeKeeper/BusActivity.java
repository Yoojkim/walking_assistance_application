package com.example.EyeKeeper;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class BusActivity extends AppCompatActivity {
    LocationManager lm = null;

    SwipeRefreshLayout pullToRefresh;
    ListView listview;

    //Location
    Location location = null;

    AlertDialog msgDlg;
    //GPS
    private double longitude;
    private double latitude;

    List<BusStop> busStops;

    // 초기 gps와 버스 정류장 정보 알려주기
    protected void onResume() {
        super.onResume();
        setContentView(R.layout.activity_bus);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            //현재 권한이 없다는 popup 후 menu로 돌아가도록 intent 넣어주세요.
            AlertDialog.Builder msg = new AlertDialog.Builder(BusActivity.this)
                    .setTitle("권한을 허락해주세요.")
                    .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Intent intent = new Intent(getApplicationContext(), MenuActivity.class);
                            startActivity(intent);
                        }
                    });
            msgDlg = msg.create();
            msgDlg.show();
        }
        else{
            listview = findViewById(R.id.busstop);

            Myadapter adapter = new Myadapter();

            busStops=getBusstopinfo();

            for (BusStop bs : busStops) {
                Log.i("busStop", bs.getStr());
                String[] info = bs.getStr().split(",");
                if(info[0] == "0"){
                    AlertDialog.Builder msg = new AlertDialog.Builder(BusActivity.this)
                            .setTitle("현재 근처에 버스정류장이 없습니다.")
                            .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    Intent intent = new Intent(getApplicationContext(), MenuActivity.class);
                                    startActivity(intent);
                                }
                            });
                    msgDlg = msg.create();
                    msgDlg.show();
                }else if(info[0] == "xss" || busStops == null){
                    AlertDialog.Builder msg = new AlertDialog.Builder(BusActivity.this)
                            .setTitle("현재 네트워크가 불안정합니다. 잠시후 접속해주세요.")
                            .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    Intent intent = new Intent(getApplicationContext(), MenuActivity.class);
                                    startActivity(intent);
                                }
                            });
                    msgDlg = msg.create();
                    msgDlg.show();
                }else{
                    adapter.addItem(new BusStop(info[1], info[2]));
                }
            }
            listview.setAdapter(adapter);
            listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView parent, View v, int position, long id) {
                    Intent busInfoIntent = new Intent(BusActivity.this, BusInfoActivity.class);
                    busInfoIntent.putExtra("citycode", busStops.get(position).citycode);
                    busInfoIntent.putExtra("nodeid", busStops.get(position).nodeid);
                    startActivity(busInfoIntent);
                }
            });

            pullToRefresh = (SwipeRefreshLayout) findViewById(R.id.SwipeRefreshLayout_busstop);
            pullToRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    listview = findViewById(R.id.busstop);

                    Myadapter adapter = new Myadapter();
                    getBusstopinfo();

                    for (BusStop bs : busStops) {
                        Log.i("busStop", bs.getStr());
                        String[] info = bs.getStr().split(",");
                        adapter.addItem(new BusStop(info[1], info[2]));
                    }
                    listview.setAdapter(adapter);

                    pullToRefresh.setRefreshing(false);
                }
            });
        }

    }


    public List<BusStop> getBusstopinfo() {
        setLoc();
        longitude = location.getLongitude();
        latitude = location.getLatitude();
        Log.d("gps", "경도: " + longitude + ", 위도: " + latitude);

        ExecutorService executorService = Executors.newSingleThreadExecutor();

        BusStopThread busStopThread = new BusStopThread();
        Future<List<BusStop>> future = executorService.submit(busStopThread);

        try {
            return (List<BusStop>) future.get();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return null;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i("onCreate", "실행");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bus);

        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.i("Loc", "not permission");
            return;
        }

        //lm.getLstKnownLocation이 null 받아오지 않을 때까지 반복
        while (lm.getLastKnownLocation(lm.NETWORK_PROVIDER) == null && lm.getLastKnownLocation(lm.GPS_PROVIDER) == null) {
            Log.e("getLastKnownLocation","null");
            Location networkLoc = lm.getLastKnownLocation(lm.NETWORK_PROVIDER);
            Location gpsLoc = lm.getLastKnownLocation(lm.GPS_PROVIDER);

            if (gpsLoc != null)
                break;

            if (networkLoc != null)
                break;
        }
        
        setLoc();
    }

    private void setLoc() {
        @SuppressLint("MissingPermission") Location networkLoc = lm.getLastKnownLocation(lm.NETWORK_PROVIDER);
        @SuppressLint("MissingPermission") Location gpsLoc= lm.getLastKnownLocation(lm.GPS_PROVIDER);

        if(gpsLoc!=null)
            location = gpsLoc;

        if(networkLoc!=null)
            location=networkLoc;
    }

    //api 이용
    public class BusStopThread implements Callable<List<BusStop>>{

        @Override
        public List<BusStop> call() throws Exception {
            StringBuilder urlBuilder = new StringBuilder("http://apis.data.go.kr/1613000/BusSttnInfoInqireService/getCrdntPrxmtSttnList");

            //페이지 문제
            urlBuilder.append("?" + URLEncoder.encode("serviceKey","UTF-8") +"="+ BuildConfig.BUSSTOP_API_KEY); /*Service Key*/
            urlBuilder.append("&" + URLEncoder.encode("pageNo","UTF-8") + "=" + URLEncoder.encode("1", "UTF-8")); /*페이지번호*/
            urlBuilder.append("&" + URLEncoder.encode("numOfRows","UTF-8") + "=" + URLEncoder.encode("5", "UTF-8")); /*한 페이지 결과 수*/
            urlBuilder.append("&" + URLEncoder.encode("_type","UTF-8") + "=" + URLEncoder.encode("json", "UTF-8")); /*데이터 타입(xml, json)*/
            urlBuilder.append("&" + URLEncoder.encode("gpsLati","UTF-8") + "=" + URLEncoder.encode(Double.toString(latitude), "UTF-8"));
            urlBuilder.append("&" + URLEncoder.encode("gpsLong","UTF-8") + "=" + URLEncoder.encode(Double.toString(longitude), "UTF-8"));

            URL url = new URL(urlBuilder.toString());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-type", "application/json");

            BufferedReader rd;
            Log.e("Response code:", String.valueOf(conn.getResponseCode()));

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

            String data=sb.toString();
            rd.close();
            conn.disconnect();

            Log.e("BUS_API_TEST",data);

            //data-> html 코드 반환시 대처
            //현재 네트워크가 정상적이지 않다는 popup 출력
            for(int i=0;i<data.length();i++){
                if(data.charAt(i)=='<'){
                    //팝업 출력
                    return new ArrayList<BusStop>(){{
                        add(new BusStop("xss","xss","xss","xss"));
                    }};
                }
            }

            //json Parsing (refactoring 필요)
            JSONObject jsonObject=new JSONObject(data);
            int totalCnt=jsonObject.getJSONObject("response").getJSONObject("body").getInt("totalCount");
            Log.i("totalCnt", String.valueOf(totalCnt));

            if(totalCnt==0) {
                return new ArrayList<BusStop>(){{
                    add(new BusStop("0","0","0","0"));
                }};
            }

            List<BusStop> busStopList=new ArrayList<>();
            //여기에도 동일하게 totalCnt==0 코드 작성
            if(totalCnt==1) {
                JSONObject jo = jsonObject.getJSONObject("response").getJSONObject("body").getJSONObject("items").getJSONObject("item");
                BusStop bs=new BusStop(jo.getString("nodeid"),jo.getString("nodenm"),jo.getString("nodeno"),jo.getString("citycode"));
                busStopList.add(bs);
            }
            else {
                JSONArray jsonArray = jsonObject.getJSONObject("response").getJSONObject("body").getJSONObject("items").getJSONArray("item");

                for(int i=0;i<jsonArray.length();i++){
                    jsonObject=jsonArray.getJSONObject(i);

                    //BusStop 객체에 넣기
                    String nodeid=jsonObject.getString("nodeid");
                    String nodenm=jsonObject.getString("nodenm");
                    String nodeno=jsonObject.getString("nodeno");
                    String citycode=jsonObject.getString("citycode");

                    busStopList.add(new BusStop(nodeid,nodenm,nodeno,citycode));
                }
            }

            for(BusStop bs:busStopList)
                Log.i("버스정류장 정보",bs.getStr());

            return busStopList;
        }
    }

    public class BusStop{
        public String nodeid;
        public String nodenm;
        public String nodeno;
        public String citycode;

        public BusStop(String nodeid, String nodenm, String nodeno, String citycode) {
            this.nodeid = nodeid;
            this.nodenm = nodenm;
            this.nodeno = nodeno;
            this.citycode = citycode;
        }

        public BusStop(String nodenm, String nodeno){
            this.nodenm = nodenm;
            this.nodeno = nodeno;
        }
        public String getNodenm(){ return nodenm; }
        public String getNodeno(){ return nodeno; }
        public String getStr(){ return nodeid+", "+nodenm+", "+nodeno+", "+citycode; }
    }

    class Myadapter extends BaseAdapter{
        private ArrayList<BusStop> busitem = new ArrayList<>();

        public void addItem(BusStop bus){
            busitem.add(bus);
        }

        @Override
        public int getCount(){
            return busitem.size();
        }

        @Override
        public BusStop getItem(int position){
            return busitem.get(position);
        }

        @Override
        public long getItemId(int position){
            return position;
        }

        @Override
        public View getView(final int position, final View view, ViewGroup parent){
            BusStopView v = new BusStopView(getApplicationContext());

            BusStop bus = busitem.get(position);
            v.setNm(bus.getNodenm());
            v.setNo(bus.getNodeno());

            return v;
        }

    }

    public class BusStopView extends LinearLayout{
        TextView textView, textView2;

        public BusStopView(Context context){
            super (context);
            init(context);
        }

        public BusStopView(Context context, AttributeSet attrs){
            super(context, attrs);
            init(context);
        }

        private void init(Context context){
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            inflater.inflate(R.layout.listitem_busstop, this, true);

            textView = findViewById(R.id.busnm);
            textView2 = findViewById(R.id.busno);
        }

        public void setNo(String nodeno){ textView.setText(nodeno); }
        public void setNm(String nodenm){ textView2.setText(nodenm); }
    }

    public void onDestroy(){
        super.onDestroy();
        if(msgDlg!=null)
        {
            msgDlg.dismiss();
            msgDlg=null;
        }
    }


}
