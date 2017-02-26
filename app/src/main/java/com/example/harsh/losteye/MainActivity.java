package com.example.harsh.losteye;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.appindexing.Thing;
import com.google.android.gms.common.api.GoogleApiClient;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements CvCameraViewListener2 , SensorEventListener{
    private static final String TAG = "MainActivity";
    private Mat mRgba;
    private Mat mGray;
    private File mCascadeFile;
    private CascadeClassifier mCatDetector;
    private CascadeClassifier mPlateDetector;
    private float mRelativeFaceSize = 0.2f;
    private int mAbsoluteFaceSize = 0;
    private static final Scalar FACE_RECT_COLOR = new Scalar(0 , 255 , 0 , 255);
    private CameraBridgeViewBase mOpenCvCameraView;
    private SensorManager mSensorManager;
    private boolean spokeForDupCat = false;
    private boolean spokeForDupPlate = false;
    private float[] mGData = new float[3];
    private float[] mMData = new float[3];
    private float[] mR = new float[16];
    private float[] mI = new float[16];
    private TextView debugText;
    private float[] mOrientation = new float[3];
    private boolean foundCat = false;
    private boolean foundPlate = false;
    private TextToSpeech t1;
    private Map<String , Double> foundItems = new HashMap<>();
    private double bearing;
    private final float rad2deg = (float)(180.0f/Math.PI);
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    try {
                        //InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
//                        InputStream is = getResources().openRawResource(R.raw.plate);
                        InputStream is = getResources().openRawResource(R.raw.cat);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                       // mCascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
//                        mCascadeFile = new File(cascadeDir, "plate.xml");
                        mCascadeFile = new File(cascadeDir, "cat.xml");
                        FileOutputStream os = new FileOutputStream(mCascadeFile);
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        is.close();
                        os.close();
                        mCatDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
                        if (mCatDetector.empty()) {
                            mCatDetector = null;
                        }
                        cascadeDir.delete();
                        is = getResources().openRawResource(R.raw.plate);
                        cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        mCascadeFile = new File(cascadeDir, "plate.xml");
                        os = new FileOutputStream(mCascadeFile);
                        buffer = new byte[4096];
                        bytesRead = 0;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        is.close();
                        os.close();
                        mPlateDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
                        if (mPlateDetector.empty()) {
                            mPlateDetector = null;
                        }
                        cascadeDir.delete();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    mOpenCvCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }

            }
        }
    };
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mSensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        setContentView(R.layout.activity_main); //Change to camera layout screen
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.fd_activity_surface_view); //Change to other layout
        debugText = (TextView) findViewById(R.id.textView);
        t1=new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    t1.setLanguage(Locale.US);
                }
            }
        });
        mOpenCvCameraView.setVisibility(CameraBridgeViewBase.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }

    @Override
    public void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        Sensor gsensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor msensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mSensorManager.registerListener(this, gsensor, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, msensor, SensorManager.SENSOR_DELAY_GAME);
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mGray = new Mat();
        mRgba = new Mat();
    }

    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        if (mAbsoluteFaceSize == 0) {
            int height = mGray.rows();
            if (Math.round(height * mRelativeFaceSize) > 0) {
                mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
            }
        }
        MatOfRect faces = new MatOfRect();
        if (mCatDetector != null)
            mCatDetector.detectMultiScale(mGray, faces, 1.1, 4, 2, new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
        Rect[] facesArray = faces.toArray();
        for(int i = 0 ; i < facesArray.length ; i++) {
            Imgproc.rectangle(mRgba , facesArray[i].tl() , facesArray[i].br() , FACE_RECT_COLOR , 3);
        }
        if(facesArray.length > 0) { //THERE IS CAT
            if(!foundCat) {
                foundCat = true;
                if(foundItems.containsKey("CAT") && !spokeForDupCat && (Math.abs(bearing - foundItems.get("CAT")) >= 22)) {
                    t1.speak("Please don't get another cat when you have one", TextToSpeech.QUEUE_ADD, null);
                    spokeForDupCat = true;
                }
                else if(!foundItems.containsKey("CAT"))
                    foundItems.put( "CAT" , bearing);
            }
            else
                Log.d("HI" , "Just another refresh frame with camera still focus on same obj");
        }
        else {
            if(foundCat)
                foundItems.put("CAT" , bearing);
            foundCat = false;
            spokeForDupCat = false;
        }
        faces = new MatOfRect();
        if (mPlateDetector != null)
            mPlateDetector.detectMultiScale(mGray, faces, 1.1, 4, 2, new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
        facesArray = faces.toArray();
        for(int i = 0 ; i < facesArray.length ; i++) {
            Imgproc.rectangle(mRgba , facesArray[i].tl() , facesArray[i].br() , FACE_RECT_COLOR , 3);
        }
        if(facesArray.length > 0) { //THERE IS CAT
            if(!foundPlate) {
                foundPlate = true;
                if(foundItems.containsKey("PLATE") && !spokeForDupPlate && (Math.abs(bearing - foundItems.get("PLATE")) >= 22)) {
                    t1.speak("Please don't get another plate when you have one" , TextToSpeech.QUEUE_ADD , null);
                    spokeForDupPlate = true;
                }
                else if(!foundItems.containsKey("PLATE"))
                    foundItems.put("PLATE" , bearing);
            }
            else
                Log.d("HI" , "Just another refresh frame with camera still focus on same obj");
        }
        else {
            if(foundPlate)
                foundItems.put("PLATE" , bearing);
            foundPlate = false;
            spokeForDupPlate = false;
        }
        return mRgba;
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)){ //cat
            if(!foundItems.containsKey("CAT"))
                t1.speak("Haven't seen a cat" , TextToSpeech.QUEUE_ADD , null);
            else {
                double catHeading = foundItems.get("CAT");
                double delta = bearing - catHeading;
                if(delta < 0)
                    t1.speak(String.format("Turn right %.0f degrees" , Math.abs(delta)), TextToSpeech.QUEUE_ADD , null);
                else
                    t1.speak(String.format("Turn left %.0f degrees" , Math.abs(delta)), TextToSpeech.QUEUE_ADD , null);
            }
        }
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP)){ //plate
            if(!foundItems.containsKey("PLATE"))
                t1.speak("Haven't seen a plate" , TextToSpeech.QUEUE_ADD , null);
            else {
                double plateHeading = foundItems.get("PLATE");
                double delta = bearing - plateHeading;
                if(delta < 0)
                    t1.speak(String.format("Turn right %.0f degrees" , Math.abs(delta)) , TextToSpeech.QUEUE_ADD , null);
                else
                    t1.speak(String.format("Turn left %.0f degrees" , Math.abs(delta)) , TextToSpeech.QUEUE_ADD , null);
            }
        }
        return true;
    }

    public void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();
        float[] data;
        if (type == Sensor.TYPE_ACCELEROMETER) {
            data = mGData;
        } else if (type == Sensor.TYPE_MAGNETIC_FIELD) {
            data = mMData;
        } else {
            // we should not be here.
            return;
        }
        for (int i=0 ; i<3 ; i++)
            data[i] = event.values[i];

        SensorManager.getRotationMatrix(mR, mI, mGData, mMData);
        SensorManager.getOrientation(mR, mOrientation);
        double degree = rad2deg * mOrientation[0];
        if(degree < 0)
           bearing = 360 + degree;
        else
            bearing = degree;
        debugText.setText(String.valueOf(degree));
    }

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    public Action getIndexApiAction() {
        Thing object = new Thing.Builder()
                .setName("Main Page") // TODO: Define a title for the content shown.
                // TODO: Make sure this auto-generated URL is correct.
                .setUrl(Uri.parse("http://[ENTER-YOUR-URL-HERE]"))
                .build();
        return new Action.Builder(Action.TYPE_VIEW)
                .setObject(object)
                .setActionStatus(Action.STATUS_TYPE_COMPLETED)
                .build();
    }

    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        AppIndex.AppIndexApi.start(client, getIndexApiAction());
    }

    @Override
    public void onStop() {
        super.onStop();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        AppIndex.AppIndexApi.end(client, getIndexApiAction());
        client.disconnect();
    }
}
