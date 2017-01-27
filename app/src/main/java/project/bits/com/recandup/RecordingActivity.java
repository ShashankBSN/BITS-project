package project.bits.com.recandup;

import android.content.Context;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import project.bits.com.recandup.api.FileUploadService;
import project.bits.com.recandup.api.ServiceGenerator;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static android.media.MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED;

public class RecordingActivity extends AppCompatActivity implements View.OnClickListener{

    private Camera camera;
    private LinearLayout showCase;
    private Button start;
    private CameraPreview cameraPreview;
    private boolean isRecording = false;
    private MediaRecorder mMediaRecorder;
    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;
    private static final String TAG = "Recording Activity";

    DBManager manager;
    /** Create a file Uri for saving an image or video */
    private Uri getOutputMediaFileUri(int type){
        return Uri.fromFile(this.getOutputMediaFile(type));
    }

    /** Create a File for saving an image or video */
    private File getOutputMediaFile(int type){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.
        manager = new DBManager(this);

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "MyCameraApp");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d("MyCameraApp", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if(type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_"+ timeStamp + ".mp4");
        } else {
            return null;
        }

        manager.addVideoAddress(mediaFile.getPath());
        return mediaFile;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //dim the screen
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        params.screenBrightness = 0;
        getWindow().setAttributes(params);

        camera = getCameraInstance();
        cameraPreview = new CameraPreview(this,camera);

        start = (Button) findViewById(R.id.start_video);

        showCase = (LinearLayout) findViewById(R.id.video_showcase_layout);
        showCase.addView(cameraPreview);

        // Add a listener to the Capture button
        start.setOnClickListener(this);
    }

    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(0); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private boolean prepareVideoRecorder(){
        mMediaRecorder = new MediaRecorder();

        // Step 1: Unlock and set camera to MediaRecorder
        camera.unlock();

        mMediaRecorder.setCamera(camera);
        // Step 2: Set sources
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
        mMediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_LOW));

        //setting capture rate
        mMediaRecorder.setCaptureRate(10);

        mMediaRecorder.setMaxDuration(2000);

        // Step 4: Set output file
        mMediaRecorder.setOutputFile(this.getOutputMediaFile(MEDIA_TYPE_VIDEO).toString());

        // Step 5: Set the preview output
        mMediaRecorder.setPreviewDisplay(cameraPreview.getHolder().getSurface());

        mMediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
            @Override
            public void onInfo(MediaRecorder mediaRecorder, int i, int i1) {
                if (i == MEDIA_RECORDER_INFO_MAX_DURATION_REACHED){
                    Toast.makeText(RecordingActivity.this,"Video Time Done",Toast.LENGTH_SHORT).show();
                    releaseMediaRecorder();
                    if (prepareVideoRecorder()) {
                        // Camera is available and unlocked, MediaRecorder is prepared,
                        // now you can start recording
                        mMediaRecorder.start();
                        // inform the user that recording has started
                        start.setText("STOP");
                        Toast.makeText(RecordingActivity.this,"Video Started",Toast.LENGTH_SHORT).show();
                        isRecording = true;
                    } else {
                        // prepare didn't work, release the camera
                        releaseMediaRecorder();
                        // inform user
                    }
                }
            }
        });

        // Step 6: Prepare configured MediaRecorder
        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException e) {
            Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            mMediaRecorder.release();
            return false;
        } catch (IOException e) {
            Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseMediaRecorder();       // if you are using MediaRecorder, release it first
        releaseCamera();              // release the camera immediately on pause event
    }

    private void releaseMediaRecorder(){
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();   // clear recorder configuration
            mMediaRecorder.release(); // release the recorder object
            mMediaRecorder = null;
            camera.lock();           // lock camera for later use
        }
    }

    private void releaseCamera(){
        if (camera != null){
            camera.release();        // release the camera for other applications
            camera = null;
        }
    }

    @Override
    public void onClick(View view) {
        manager = new DBManager(this);
        ArrayList<String> notUpload = manager.getNotUploadedVideos();
        if (notUpload.size()!=0) {
            Log.e(TAG, notUpload.get(0));
        }
        notUpload = manager.getToBeDeleted();
        if (notUpload.size()!=0){
            Log.e(TAG,notUpload.get(0));
        }
        if (view.getId() == R.id.start_video){
            if (isRecording) {
                // stop recording and release camera
                mMediaRecorder.stop();  // stop the recording
                releaseMediaRecorder();// release the MediaRecorder object
                camera.lock();         // take camera access back from MediaRecorder
                // inform the user that recording has stopped
                start.setText("START");
                isRecording = false;
            } else {
                // initialize video camera
                if (prepareVideoRecorder()) {
                    // Camera is available and unlocked, MediaRecorder is prepared,
                    // now you can start recording
                    mMediaRecorder.start();
                    // inform the user that recording has started
                    start.setText("STOP");
                    isRecording = true;
                } else {
                    // prepare didn't work, release the camera
                    releaseMediaRecorder();
                    // inform user
                }
            }
        }
    }

    private void uploadFile(Uri fileUri) {
        // create upload service client
        FileUploadService service = ServiceGenerator.createService(FileUploadService.class);

        File file = new File(fileUri.getPath());

        // create RequestBody instance from file
        RequestBody requestFile = RequestBody.create(MediaType.parse(getContentResolver().getType(fileUri)), file);

        // MultipartBody.Part is used to send also the actual file name
        MultipartBody.Part body = MultipartBody.Part.createFormData("video", file.getName(), requestFile);

        // add another part within the multipart request
        String descriptionString = "hello, this is description speaking";
        RequestBody description = RequestBody.create(okhttp3.MultipartBody.FORM, descriptionString);

        // finally, execute the request
        Call<ResponseBody> call = service.upload(description, body);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                Log.v("Upload", "success");
            }
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e("Upload error:", t.getMessage());
            }
        });
    }
}