package com.example.EyeKeeper;

import static android.os.SystemClock.sleep;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SizeF;
import android.view.Display;
import android.view.SurfaceView;
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
import org.opencv.dnn.Net;
import org.opencv.imgproc.Imgproc;

import org.opencv.dnn.Dnn;
import org.opencv.utils.Converters;


import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;


public class WalkingActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {
    final List<String> cocoNames = Arrays.asList("자전거","킥보드");

    //timer 사용 final val
    private static final int MESSAGE_TIMER_START=100;
    private static final int MESSAGE_TIMER_STOP=-1;

    CameraBridgeViewBase cameraBridgeViewBase;
    BaseLoaderCallback baseLoaderCallback;
    Net tinyYolo;

    private static TextToSpeech tts;
    AlertDialog msgDlg;

    CameraManager manager;

    //distance calc val
    double focalLength;
    float sensor_height;
    int preview_height; //화면 높이
    Rect boxMax;
    TimerHandler timerHandler=null;

    //objectDetection global val
    int object_height =-1; String objectName =null;

    boolean firstTime=true;//모드 시작 알림
    boolean detection=false;

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
        setContentView(R.layout.activity_walking);

        cameraBridgeViewBase = (CameraBridgeViewBase) findViewById(R.id.CameraView);
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

        //get Focal length, Sensor_height
        manager=(CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics=manager.getCameraCharacteristics("0");
            //초점거리 얻기
            float[] maxFocus = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            focalLength =maxFocus[0];

            //image sensor
            SizeF size = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
            sensor_height=size.getHeight();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        timerHandler=new TimerHandler();
    }

    private class TimerHandler extends Handler{
        public void handleMessage(Message msg){
            switch(msg.what){
                case MESSAGE_TIMER_START:
                    detection=true;
                    this.sendEmptyMessageDelayed(MESSAGE_TIMER_START,5000);
                    break;

                case MESSAGE_TIMER_STOP:
                    detection=false;
                    this.removeMessages(MESSAGE_TIMER_START);
                    break;
            }
        }
    }



    public void objectDetect(){
        //거리 측정
        double dist = 0;
        int realSize = 0;
        int arr[] = cameraBridgeViewBase.getFrameSize();
        
        if (objectName == "킥보드")
            realSize = 1310;
        else if (objectName == "자전거")
            realSize = 970;

        dist = focalLength * realSize * preview_height / (object_height * sensor_height);
        dist /= 10;

        int distance = (int) Math.round(dist);
            
        //m 단위로 변경
        int meter = distance / 100;
        int cm = distance % 100;


        int fWidth = arr[0];
        Log.d("카메라", String.valueOf(arr[0])+String.valueOf(arr[1]));
        double boxCenterX = (boxMax.tl().x + boxMax.br().x)/2;

        String dir = null;

        if(boxCenterX <= (fWidth * (1.0/3.0))){
            dir = "왼쪽";
        }else if(boxCenterX > fWidth * (2.0/3.0) && boxCenterX <= fWidth){
            dir = "오른쪽";
        }else{
            dir = "정면";
        }

        String msg = dir + Integer.toString(meter) + "m" + Integer.toString(cm) + "cm에" + objectName + "가 있습니다";
        tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null);

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat frame = inputFrame.rgba();
        Imgproc.cvtColor(frame, frame, Imgproc.COLOR_RGB2BGR);

        if(firstTime){
            preview_height = frame.height();
            tts.speak("보행모드가 실행됩니다.", TextToSpeech.QUEUE_FLUSH, null);
            firstTime=false;
            return frame;
        }

        if (!detection)
            return frame;

        detection=false;

        //Dnn, NMS
        Mat imageBlob = Dnn.blobFromImage(frame, 0.00392, new Size(416,416),new Scalar(0, 0, 0),false, false);

        tinyYolo.setInput(imageBlob);

        java.util.List<Mat> result = new java.util.ArrayList<Mat>(2);

        List<String> outBlobNames = new java.util.ArrayList<>();
        outBlobNames.add(0, "yolo_16");
        outBlobNames.add(1, "yolo_23");

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
             boxMax=null;
            for (int i = 0; i < ind.length; ++i) {

                int idx = ind[i];
                Rect box = boxesArray[idx];
                int idGuy = clsIds.get(idx);

                //제일 큰 객체 저장
                if(heightTemp<box.height){
                    heightTemp=box.height;
                    boxMax=box;
                    objectName=cocoNames.get(idGuy);

                }
                object_height=heightTemp;
            }
            Imgproc.rectangle(frame, boxMax.tl(), boxMax.br(), new Scalar(255, 0, 0), 2);
        }else{
            //Detection x
            object_height =-1; objectName=null;
        }

        if(objectName!=null)
            objectDetect();

        return frame;
    }


    @Override
    public void onCameraViewStarted(int width, int height) {

        String tinyYoloCfg = getPath("yolov3-tiny_obj(walking).cfg",this);
        String tinyYoloWeights = getPath("yolov3-tiny_obj(walking).weights",this);

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
            AlertDialog.Builder msg = new AlertDialog.Builder(WalkingActivity.this)
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

        //popup 따로
        if (!OpenCVLoader.initDebug()){
            AlertDialog.Builder msg = new AlertDialog.Builder(WalkingActivity.this)
                    .setTitle("현재 네트워크가 불안정합니다. 잠시후 접속해주세요.")
                    .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            onBackPressed();
                        }
                    });
            msgDlg = msg.create();
            msgDlg.show();
        } else
        {
            baseLoaderCallback.onManagerConnected(baseLoaderCallback.SUCCESS);
            timerHandler.sendEmptyMessage(MESSAGE_TIMER_START);
        }
    }

    public void onBackPressed(){
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        timerHandler.sendEmptyMessage(MESSAGE_TIMER_STOP);

        super.onPause();
        if(cameraBridgeViewBase!=null){
            cameraBridgeViewBase.disableView();
        }

        //관련 변수 초기화
        firstTime=true; detection=false;
        object_height =-1; objectName =null;
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

        if(msgDlg!=null)
        {
            msgDlg.dismiss();
            msgDlg=null;
        }
    }
}