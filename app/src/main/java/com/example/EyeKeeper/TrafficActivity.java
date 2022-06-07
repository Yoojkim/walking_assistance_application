package com.example.EyeKeeper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class TrafficActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {
    final List<String> cocoNames = Arrays.asList("빨간불","초록불","검은불");
    
    //timer 사용 final val
    private static final int TRAFFIC_START=100;
    private static final int TRAFFIC_STOP=-1;

    AlertDialog msgDlg;
    CameraBridgeViewBase cameraBridgeViewBase;
    BaseLoaderCallback baseLoaderCallback;
    Net tinyYolo;

    private static TextToSpeech tts;

    TimerHandler trafficHandler=null;

    //traffic detection val
    boolean traffic =false; //신호등 인식 중 여부
    boolean trafficFlag=false; //true-green, false-red

    String objectName=null;

    //frame 2초마다 탐지
    boolean detection=false;
    int trafficCount =0; //인식 불가 횟수 10회 넘어가면 (20초) 인식 해제

    //모드 시작 알림
    boolean firstTime=true;

    private static String getPath(String file, Context context) {
        AssetManager assetManager = context.getAssets();
        BufferedInputStream inputStream = null;
        try {
            // Read data from assets.
            inputStream = new BufferedInputStream(assetManager.open(file));
            byte[] data = new byte[inputStream.available()];
            inputStream.read(data);
            inputStream.close();
            // Create copy file in storage.
            File outFile = new File(context.getFilesDir(), file);
            FileOutputStream os = new FileOutputStream(outFile);
            os.write(data);
            os.close();
            return outFile.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        tts=new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status!=TextToSpeech.ERROR)
                    tts.setLanguage(Locale.KOREAN);
            }
        });
        tts.setPitch(1.0f);
        tts.setSpeechRate(2.0f);

        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_traffic);

        cameraBridgeViewBase = (CameraBridgeViewBase) findViewById(R.id.TrafficView);
        cameraBridgeViewBase.setVisibility(SurfaceView.VISIBLE);
        cameraBridgeViewBase.setCvCameraViewListener(this);
        cameraBridgeViewBase.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK); //후면카메라

        baseLoaderCallback = new BaseLoaderCallback(this) {
            @Override
            public void onManagerConnected(int status) {
                super.onManagerConnected(status);

                switch(status){
                    case BaseLoaderCallback.SUCCESS:
                        cameraBridgeViewBase.enableView();
                        break;
                    default:
                        super.onManagerConnected(status);
                        break;
                }
            }
        };

        trafficHandler=new TimerHandler(); //신호등 인식(2분마다 재인식)
    }

    private class TimerHandler extends Handler {
        public void handleMessage(Message msg) {
            switch (msg.what) {

                //2초마다 신호 파악
                case TRAFFIC_START:
                    //신호등 관련 변수 초기화
                    detection=true;
                    this.sendEmptyMessageDelayed(TRAFFIC_START,2000); //2초마다 신호 인식
                    break;

                case TRAFFIC_STOP:
                    detection=false;
                    this.removeMessages(TRAFFIC_START);
                    break;
            }
        }
    }

    //신호등 감지
    public void trafficDetect(){
        //신호등 탐지 x
        if (objectName == null) {
            //인식 중
            if (traffic) {
                if (trafficCount == 10) {
                    trafficFlag=false;
                    traffic = false; //인식 해제

                    //10 frame동안 신호등 탐지 x -> 신호등이 없다는 판단의 근거
                    tts.speak("신호등을 재인식합니다.", TextToSpeech.QUEUE_FLUSH, null);
                    trafficCount = 0;
                } else
                    trafficCount++; //기존 신호 변수 red,green은 유지 (잠시 프레임 아웃으로 감지되지 않을 수도 있음)
            }
        }

        //신호등 탐지 o
        //trafficFlag: true-green, false-red
        else {
            traffic=true;
            trafficCount = 0;

            if (objectName == "빨간불") {
                if (trafficFlag) {
                    tts.speak("빨간불로 변경되었습니다.", TextToSpeech.QUEUE_FLUSH, null);
                    trafficFlag = false;
                }else
                    tts.speak("빨간불입니다.", TextToSpeech.QUEUE_FLUSH, null);
            }
            else if (objectName == "초록불") {
                if(!trafficFlag) {
                    tts.speak("초록불로 변경되었습니다.", TextToSpeech.QUEUE_FLUSH, null);
                    trafficFlag = true;
                }else
                    tts.speak("초록불입니다.", TextToSpeech.QUEUE_FLUSH, null);
            }
            else {
                trafficFlag=true;
                tts.speak("곧 빨간불로 변경됩니다.", TextToSpeech.QUEUE_FLUSH, null);
            }
        }
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame){
        Mat frame=inputFrame.rgba();
        Imgproc.cvtColor(frame, frame, Imgproc.COLOR_RGB2BGR);

        if(firstTime){
            tts.speak("횡단모드가 실행됩니다.", TextToSpeech.QUEUE_FLUSH, null);
            firstTime = false;
            return frame;
        }

        if(!detection)
            return frame;

        detection=false;

        Mat imageBlob = Dnn.blobFromImage(frame, 0.00392, new Size(416,416),new Scalar(0, 0, 0),false, false);

        tinyYolo.setInput(imageBlob);

        java.util.List<Mat> result = new java.util.ArrayList<Mat>(2);

        List<String> outBlobNames = new java.util.ArrayList<>();
        outBlobNames.add(0, "yolo_16");
        outBlobNames.add(1, "yolo_23");
        outBlobNames.add(2, "yolo_30");

        tinyYolo.forward(result,outBlobNames);

        float confThreshold = 0.3f;

        List<Integer> clsIds = new ArrayList<>();
        List<Float> confs = new ArrayList<>();
        List<Rect> rects = new ArrayList<>();

        for (int i = 0; i < result.size(); ++i)
        {
            Mat level = result.get(i);
            for (int j = 0; j < level.rows(); ++j)
            {
                Mat row = level.row(j);
                Mat scores = row.colRange(5, level.cols());

                Core.MinMaxLocResult mm = Core.minMaxLoc(scores);

                float confidence = (float)mm.maxVal;

                Point classIdPoint = mm.maxLoc;

                if (confidence > confThreshold)
                {
                    int centerX = (int)(row.get(0,0)[0] * frame.cols());
                    int centerY = (int)(row.get(0,1)[0] * frame.rows());
                    int width   = (int)(row.get(0,2)[0] * frame.cols());
                    int height  = (int)(row.get(0,3)[0] * frame.rows());

                    int left    = centerX - width  / 2;
                    int top     = centerY - height / 2;

                    clsIds.add((int)classIdPoint.x);
                    confs.add((float)confidence);

                    rects.add(new Rect(left, top, width, height));
                }
            }
        }
        int ArrayLength = confs.size();
        if (ArrayLength>=1) {
            // Apply non-maximum suppression procedure.
            float nmsThresh = 0.1f;

            MatOfFloat confidences = new MatOfFloat(Converters.vector_float_to_Mat(confs));
            Rect[] boxesArray = rects.toArray(new Rect[0]);
            MatOfRect boxes = new MatOfRect(boxesArray);
            MatOfInt indices = new MatOfInt();

            //nms 수행
            Dnn.NMSBoxes(boxes, confidences, confThreshold, nmsThresh, indices);

            // Draw result boxes:
            int[] ind = indices.toArray();
            int heightTemp=-1;
            Rect boxMax=null;
            for (int i = 0; i < ind.length; ++i) {
                int idx = ind[i];
                Rect box = boxesArray[idx];
                int idGuy = clsIds.get(idx);

                //제일 큰 객체 저장
                if(heightTemp<box.height){
                    boxMax=box;
                    objectName =cocoNames.get(idGuy);
                }
            }
            Imgproc.rectangle(frame, boxMax.tl(), boxMax.br(), new Scalar(255, 0, 0), 2);
        }else
            objectName = null; //Detection x

        trafficDetect();
        return frame;
    }

    @Override
    public void onCameraViewStarted(int width, int height) {

        String tinyYoloCfg = getPath("yolov3-tiny_3l.cfg",this);
        String tinyYoloWeights = getPath("yolov3-tiny_3l_final.weights",this);

        tinyYolo = Dnn.readNetFromDarknet(tinyYoloCfg, tinyYoloWeights);
    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    protected void onResume() {
        super.onResume();

        //권한 코드
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            AlertDialog.Builder msg = new AlertDialog.Builder(TrafficActivity.this)
                    .setTitle("권한을 허락해주세요.")
                    .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            onBackPressed();
                        }
                    });
            msgDlg = msg.create();
            msgDlg.show();
        }

        if (!OpenCVLoader.initDebug()){
            AlertDialog.Builder msg = new AlertDialog.Builder(TrafficActivity.this)
                    .setTitle("현재 네트워크가 불안정합니다. 잠시후 접속해주세요.")
                    .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            onBackPressed();
                        }
                    });
            msgDlg = msg.create();
            msgDlg.show();
        }

        else
        {
            baseLoaderCallback.onManagerConnected(baseLoaderCallback.SUCCESS);
            trafficHandler.sendEmptyMessage(TRAFFIC_START);
        }
    }

    public void onBackPressed(){
        super.onBackPressed();
    }


    @Override
    protected void onPause() {
        trafficHandler.sendEmptyMessage(TRAFFIC_STOP);

        super.onPause();
        if(cameraBridgeViewBase!=null){
            cameraBridgeViewBase.disableView();
        }

        //관련 변수 초기화
        firstTime=true;
        traffic =false;
        trafficCount =0;
        objectName=null;
        detection=false;
        trafficFlag=false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraBridgeViewBase!=null){
            cameraBridgeViewBase.disableView();
        }

        if(tts!=null){
            tts.stop();
            tts.shutdown();
            tts=null;
        }
    }
}