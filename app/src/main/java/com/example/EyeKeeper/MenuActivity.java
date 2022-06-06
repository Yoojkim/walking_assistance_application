package com.example.EyeKeeper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.Locale;
import static android.speech.tts.TextToSpeech.ERROR;


public class MenuActivity extends AppCompatActivity {
    private final int PERMISSION_CAMERA=1001;
    private final int PERMISSION_STORAGE=1002;
    static final int PERMISSION_REQUEST = 0x0000001;
    public static final int TYPE_WIFI = 1;
    public static final int TYPE_MOBILE = 2;
    public static final int TYPE_NOT_CONNECTED = 3;
    private PermissionSupport permission;
    private TextToSpeech tts;
    private long time = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        permissionCheck();
        LinearLayout layout01 = (LinearLayout) findViewById(R.id.tutorial);
        LinearLayout layout02 = (LinearLayout) findViewById(R.id.trafficlight);
        LinearLayout layout03 = (LinearLayout) findViewById(R.id.object);
        LinearLayout layout04 = (LinearLayout) findViewById(R.id.bus);

        View.OnClickListener clickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (v.getId()){
                    case R.id.tutorial:
                        Intent intent_tutorial=new Intent(getApplicationContext(),TutorialActivity.class);
                        startActivity(intent_tutorial);
                        break;
                    case R.id.trafficlight:
                        Intent intent_traffic=new Intent(getApplicationContext(),TrafficActivity.class);
                        startActivity(intent_traffic);
                        break;
                    case R.id.object:
                        Intent intent_walking = new Intent(getApplicationContext(), WalkingActivity.class);
                        startActivity(intent_walking);
                        break;
                    case R.id.bus:
                        int status = getConnectivityStatus(getApplicationContext());
                        if((status == TYPE_MOBILE) || (status == TYPE_WIFI)){
                            Intent intent_bus = new Intent(getApplicationContext(), BusActivity.class);
                            startActivity(intent_bus);
                            break;
                        }else{
                            AlertDialog.Builder msg = new AlertDialog.Builder(MenuActivity.this)
                                    .setTitle("인터넷 연결이 되어있지 않습니다.")
                                    .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            Intent intent = new Intent(getApplicationContext(), MenuActivity.class);
                                            startActivity(intent);
                                        }
                                    });
                            AlertDialog msgDlg = msg.create();
                            msgDlg.show();
                        }

                }
            }
        };

        layout01.setOnClickListener(clickListener);
        layout02.setOnClickListener(clickListener);
        layout03.setOnClickListener(clickListener);
        layout04.setOnClickListener(clickListener);
    }

    private void permissionCheck(){
        permission = new PermissionSupport(this, this);
        if(!permission.checkPermission()){
            permission.requestPermission();
        }
    }

    // 두번 누르면 앱 종료
    public void onBackPressed(){
        //super.onBackPressed(); 뒤로가기 버튼 막기
        if(System.currentTimeMillis() - time >= 2000){
            time = System.currentTimeMillis();
            Toast.makeText(getApplicationContext(),"한번 더 누르면 종료됩니다.",Toast.LENGTH_LONG).show();
        }else if(System.currentTimeMillis() - time < 2000){
            finishAffinity();
        }
    }

    public static int getConnectivityStatus(Context context){
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(context.CONNECTIVITY_SERVICE);

        NetworkInfo networkInfo = manager.getActiveNetworkInfo();
        if(networkInfo != null){
            int type = networkInfo.getType();
            if(type == ConnectivityManager.TYPE_MOBILE){//쓰리지나 LTE로 연결
                return TYPE_MOBILE;
            }else if(type == ConnectivityManager.TYPE_WIFI){//와이파이 연결
                return TYPE_WIFI;
            }
        }
        return TYPE_NOT_CONNECTED;  //연결이 되지않은 상태
    }

}