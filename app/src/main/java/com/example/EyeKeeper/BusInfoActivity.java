package com.example.EyeKeeper;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

//refresh 추가로 구현할 예정
public class BusInfoActivity extends AppCompatActivity {

    SwipeRefreshLayout pullToRefresh;
    ListView listView;

    String nodeid = null;
    String citycode = null;
    boolean infoExist = true;

    List<Bus> buses=null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();

        setContentView(R.layout.activity_businfo);
        listView = findViewById(R.id.Businfo_list);
        InfoAdapter infoAdapter = new InfoAdapter();
        //Intent로 받은 nodeid, citycode
        Intent busInfoIntent = getIntent();
        nodeid = busInfoIntent.getStringExtra("nodeid");
        citycode = busInfoIntent.getStringExtra("citycode");

        Log.i("BusInfoActivity", nodeid + ", " + citycode);


        //http 통신
        BusInfoThread busInfoThread = new BusInfoThread();
        busInfoThread.start();

        try {
            busInfoThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (infoExist == true) {
            for (Bus b : buses) {
                Log.i("버스", b.getStr());
                String[] businfo = b.getStr().split(", ");
                infoAdapter.addItem(new Bus(Integer.parseInt(businfo[0]), Integer.parseInt(businfo[1]), businfo[2], businfo[3]));
            }
            listView.setAdapter(infoAdapter);

            pullToRefresh = (SwipeRefreshLayout) findViewById(R.id.SwipeRefreshLayout_businfo);
            pullToRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    listView = findViewById(R.id.Businfo_list);
                    InfoAdapter infoAdapter = new InfoAdapter();
                    BusInfoThread busInfoThread = new BusInfoThread();
                    busInfoThread.start();

                    try {
                        busInfoThread.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    for (Bus b : buses) {
                        Log.i("버스", b.getStr());
                        String[] businfo = b.getStr().split(", ");
                        infoAdapter.addItem(new Bus(Integer.parseInt(businfo[0]), Integer.parseInt(businfo[1]), businfo[2], businfo[3]));
                    }
                    listView.setAdapter(infoAdapter);

                    pullToRefresh.setRefreshing(false);
                }
            });
        } else {
            //if문으로 xss, 0의 경우 나눠서 처리

            AlertDialog.Builder msg = new AlertDialog.Builder(BusInfoActivity.this)
                    .setTitle("현재 도착예정인 버스가 없습니다.")
                    .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            //메뉴말고 BusActivity로 가도록 변경
                            Intent intent = new Intent(getApplicationContext(), BusActivity.class);
                            startActivity(intent);
                        }
                    });
            AlertDialog msgDlg = msg.create();
            msgDlg.show();
        }
    }

    public class BusInfoThread extends Thread {
        @Override
        public void run() {
            if (nodeid == null) {
                Log.e("run()", "nodeid에 null object");
                return;
            }
            try {
                StringBuilder urlBuilder = new StringBuilder("http://apis.data.go.kr/1613000/ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList"); /*URL*/
                urlBuilder.append("?" + URLEncoder.encode("serviceKey", "UTF-8") + "=" + BuildConfig.BUSINFO_API_KEY); /*Service Key*/
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

                String data = sb.toString();
                Log.i("Response", data);

                JSONObject jsonObject = new JSONObject(data);
                int totalCnt = jsonObject.getJSONObject("response").getJSONObject("body").getInt("totalCount");
                Log.i("totalCnt", String.valueOf(totalCnt));

                List<Bus> busList = new ArrayList<>();

                //xss 공격 처리
                for (int i = 0; i < data.length(); i++) {
                    if (data.charAt(i) == '<') {
                        infoExist = false;
                        //busList.add ("xss","xss" ... )
                    }
                }

                if (totalCnt == 1) {
                    JSONObject jo = jsonObject.getJSONObject("response").getJSONObject("body").getJSONObject("items").getJSONObject("item");
                    Bus bus = new Bus(jo.getInt("arrprevstationcnt"), jo.getInt("arrtime"), jo.getString("routeno"), jo.getString("vehicletp"));
                    busList.add(bus);
                } else if (totalCnt == 0) {
                    infoExist = false;
                    //busList.add ("0","0" ... )
                } else {
                    JSONArray jsonArray = jsonObject.getJSONObject("response").getJSONObject("body").getJSONObject("items").getJSONArray("item");

                    for (int i = 0; i < jsonArray.length(); i++) {
                        jsonObject = jsonArray.getJSONObject(i);

                        int arrprevstationcnt = jsonObject.getInt("arrprevstationcnt");
                        int arrtime = jsonObject.getInt("arrtime");
                        String routeno = jsonObject.getString("routeno");
                        String vehicletp = jsonObject.getString("vehicletp");

                        arrtime /= 60;

                        busList.add(new Bus(arrprevstationcnt, arrtime, routeno, vehicletp));
                    }
                }

                buses = busList; //totalCnt==0인 경우 빈 arrayList 들어가서 상관 x
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    public class Bus {
        int arrprevstationcnt;//남은 버스정류장 개수
        int arrtime;//도착 예상시간(초)
        String routeno;//버스번호
        String vehicletp; //차량 종류(일반 차량 ... )

        public Bus(int arrprevstationcnt, int arrtime, String routeno, String vehicletp) {
            this.arrprevstationcnt = arrprevstationcnt;
            this.arrtime = arrtime;
            this.routeno = routeno;
            this.vehicletp = vehicletp;
        }

        public String getRouteno() {
            return routeno;
        }
        public String getVehicletp() {
            return vehicletp;
        }
        public int getArrtime() {
            return arrtime;
        }
        public int getArrprevstationcnt() {
            return arrprevstationcnt;
        }
        public String getStr() {
            return Integer.toString(arrprevstationcnt) + ", " + Integer.toString(arrtime) + ", " + routeno + ", " + vehicletp;
        }
    }

    class InfoAdapter extends BaseAdapter {
        private ArrayList<Bus> info = new ArrayList<>();
        public void addItem(Bus buses) {
            info.add(buses);
        }
        public int getCount() {
            return info.size();
        }
        public Object getItem(int position) {
            return info.get(position);
        }
        public long getItemId(int position) {
            return position;
        }
        public View getView(int position, View view, ViewGroup viewGroup) {
            BusInfoView v = new BusInfoView(getApplicationContext());

            Bus buses = info.get(position);
            v.setArrt(buses.getArrtime());
            v.setRout(buses.getRouteno());
            v.setStatcnt(buses.getArrprevstationcnt());
            v.setVeichle(buses.getVehicletp());

            return v;
        }
    }

    // 버스스탑 뷰
    public class BusInfoView extends LinearLayout {
        TextView route, vtp, arrt, statcnt;

        public BusInfoView(Context context) {
            super(context);
            init(context);
        }

        public BusInfoView(Context context, AttributeSet attrs) {
            super(context, attrs);
            init(context);
        }

        private void init(Context context) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            inflater.inflate(R.layout.listitem_businfo, this, true);

            route = findViewById(R.id.routeno);
            vtp = findViewById(R.id.vehicletp);
            arrt = findViewById(R.id.arrtime);
            statcnt = findViewById(R.id.stationcnt);
        }

        public void setRout(String rout) {
            route.setText(rout);
        }

        public void setVeichle(String veicle) {
            vtp.setText(veicle);
        }

        public void setArrt(int art) {
            arrt.setText("남은시간 : " + String.valueOf(art) + "분");
        }

        public void setStatcnt(int stacnt) {
            statcnt.setText("남은정거장 : " + String.valueOf(stacnt));
        }

    }


}