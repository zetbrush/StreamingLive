package com.streaming.streaminglive;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.hardware.Camera.CameraInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import net.majorkernelpanic.streaming.Session;
import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.audio.AudioQuality;
import net.majorkernelpanic.streaming.gl.SurfaceView;
import net.majorkernelpanic.streaming.rtsp.RtspClient;
import net.majorkernelpanic.streaming.video.VideoQuality;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity implements 
OnClickListener, 
RtspClient.Callback, 
Session.Callback, 
SurfaceHolder.Callback, 
OnCheckedChangeListener {

	public final static String TAG = "MainActivity";

	private Button mButtonSave;
	private Button mButtonVideo;
	private ImageButton mButtonStart;
	private ImageButton mButtonFlash;
	private ImageButton mButtonCamera;
	private ImageButton mButtonSettings;
	private RadioGroup mRadioGroup;
	private FrameLayout mLayoutVideoSettings; 
	private FrameLayout mLayoutServerSettings;
	private SurfaceView mSurfaceView;
	private TextView mTextBitrate;
	private EditText mEditTextURI;
	private EditText mEditTextPassword;
	private EditText mEditTextUsername;
	private ProgressBar mProgressBar;
	private Session mSession;
	private RtspClient mClient;
	private Surface mSurface;
	private MediaProjection mMediaProjection;
	private VirtualDisplay mVirtualDisplay;
	private MediaProjectionManager mMediaProjectionManager;
	private int mScreenDensity;
	private int mResultCode;
	private Intent mResultData;
	private static final String STATE_RESULT_CODE = "result_code";
	private static final String STATE_RESULT_DATA = "result_data";
	private static final int REQUEST_MEDIA_PROJECTION = 1;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState); 
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.main);
		if (savedInstanceState != null) {
			mResultCode = savedInstanceState.getInt(STATE_RESULT_CODE);
			mResultData = savedInstanceState.getParcelable(STATE_RESULT_DATA);
		}

		mButtonVideo = (Button) findViewById(R.id.video);
		mButtonSave = (Button) findViewById(R.id.save);
		mButtonStart = (ImageButton) findViewById(R.id.start);
		mButtonFlash = (ImageButton) findViewById(R.id.flash);
		mButtonCamera = (ImageButton) findViewById(R.id.camera);
		mButtonSettings = (ImageButton) findViewById(R.id.settings);
		mSurfaceView = (SurfaceView) findViewById(R.id.surface);
		mEditTextURI = (EditText) findViewById(R.id.uri);
		mEditTextUsername = (EditText) findViewById(R.id.username);
		mEditTextPassword = (EditText) findViewById(R.id.password);
		mTextBitrate = (TextView) findViewById(R.id.bitrate);
		mLayoutVideoSettings = (FrameLayout) findViewById(R.id.video_layout);
		mLayoutServerSettings = (FrameLayout) findViewById(R.id.server_layout);
		mRadioGroup =  (RadioGroup) findViewById(R.id.radio);
		mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);
		
		mRadioGroup.setOnCheckedChangeListener(this);
		mRadioGroup.setOnClickListener(this);

		mButtonStart.setOnClickListener(this);
		mButtonSave.setOnClickListener(this);
		mButtonFlash.setOnClickListener(this);
		mButtonCamera.setOnClickListener(this);
		mButtonVideo.setOnClickListener(this);
		mButtonSettings.setOnClickListener(this);
		mButtonFlash.setTag("off");

		mEditTextURI.setText("rtsp://a5f64c.entrypoint.cloud.wowza.com:1935/app-2e0d/9da37462");
		mEditTextPassword.setText("f71aab17");
		mEditTextUsername.setText("client3473");
		Activity activity = this;
		DisplayMetrics metrics = new DisplayMetrics();
		activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
		mScreenDensity = metrics.densityDpi;
		mMediaProjectionManager = (MediaProjectionManager)
				activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);



		// Configures the SessionBuilder

		mSession = SessionBuilder.getInstance()

				.setContext(getApplicationContext())
				.setAudioEncoder(SessionBuilder.AUDIO_AAC)
				.setAudioQuality(new AudioQuality(8000,16000))
				.setVideoEncoder(SessionBuilder.VIDEO_H264)
				.setSurfaceView(mSurfaceView)
				.setPreviewOrientation(0)
				.setCallback(this)
				.build();
		// Configures the RTSP client
		mClient = new RtspClient();
		mClient.setSession(mSession);
		mClient.setCallback(this);

		// Use this to force streaming with the MediaRecorder API
		//mSession.getVideoTrack().setStreamingMethod(MediaStream.MODE_MEDIARECORDER_API);

		// Use this to stream over TCP, EXPERIMENTAL!
		//mClient.setTransportMode(RtspClient.TRANSPORT_TCP);

		// Use this if you want the aspect ratio of the surface view to 
		// respect the aspect ratio of the camera preview
		//mSurfaceView.setAspectRatioMode(SurfaceView.ASPECT_RATIO_PREVIEW);

		mSurfaceView.getHolder().addCallback(this);

		selectQuality();

	}

	@Override
	public void onCheckedChanged(RadioGroup group, int checkedId) {
		mLayoutVideoSettings.setVisibility(View.GONE);
		mLayoutServerSettings.setVisibility(View.VISIBLE);
		selectQuality();
	}
	private void startScreenCapture() {
		Activity activity = this;
		if (mSurface == null || activity == null) {
			return;
		}
		if (mMediaProjection != null) {
			setUpVirtualDisplay();
		} else if (mResultCode != 0 && mResultData != null) {
			setUpMediaProjection();
			setUpVirtualDisplay();
		} else {
			Log.i(TAG, "Requesting confirmation");
			// This initiates a prompt dialog for the user to confirm screen projection.
			startActivityForResult(
					mMediaProjectionManager.createScreenCaptureIntent(),
					REQUEST_MEDIA_PROJECTION);
		}
	}

	private void setUpMediaProjection() {
		mMediaProjection = mMediaProjectionManager.getMediaProjection(mResultCode, mResultData);
	}

	private void tearDownMediaProjection() {
		if (mMediaProjection != null) {
			mMediaProjection.stop();
			mMediaProjection = null;
		}
	}
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_MEDIA_PROJECTION) {
			if (resultCode != Activity.RESULT_OK) {
				Log.i(TAG, "User cancelled");

				return;
			}
			Activity activity = this;
			if (activity == null) {
				return;
			}
			Log.i(TAG, "Starting screen capture");
			mResultCode = resultCode;
			mResultData = data;
			setUpMediaProjection();
			setUpVirtualDisplay();
		}
	}

	private void setUpVirtualDisplay() {
		Log.i(TAG, "Setting up a VirtualDisplay: " +
				mSurfaceView.getWidth() + "x" + mSurfaceView.getHeight() +
				" (" + mScreenDensity + ")");
		mVirtualDisplay = mMediaProjection.createVirtualDisplay("ScreenCapture",
				mSurfaceView.getWidth(), mSurfaceView.getHeight(), mScreenDensity,
				DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
				mSurfaceView.getHolder().getSurface(), null, null);

	}

	private void stopScreenCapture() {
		if (mVirtualDisplay == null) {
			return;
		}
		mVirtualDisplay.release();
		mVirtualDisplay = null;
	}
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (mResultData != null) {
			outState.putInt(STATE_RESULT_CODE, mResultCode);
			outState.putParcelable(STATE_RESULT_DATA, mResultData);
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.start:
			mLayoutServerSettings.setVisibility(View.GONE);

			toggleStream();
			break;
		case R.id.flash:
			if (mButtonFlash.getTag().equals("on")) {
				mButtonFlash.setTag("off");
				mButtonFlash.setImageResource(R.drawable.ic_flash_on_holo_light);
			} else {
				mButtonFlash.setImageResource(R.drawable.ic_flash_off_holo_light);
				mButtonFlash.setTag("on");
			}
			mSession.toggleFlash();
			break;
		case R.id.camera:
			mSession.switchCamera();
			break;
		case R.id.settings:
			if (mLayoutVideoSettings.getVisibility() == View.GONE &&
			mLayoutServerSettings.getVisibility() == View.GONE) {
				mLayoutServerSettings.setVisibility(View.VISIBLE);
			} else {
				mLayoutServerSettings.setVisibility(View.GONE);
				mLayoutVideoSettings.setVisibility(View.GONE);
			}
			break;
		case R.id.video:
			mRadioGroup.clearCheck();
			mLayoutServerSettings.setVisibility(View.GONE);
			mLayoutVideoSettings.setVisibility(View.VISIBLE);
			break;
		case R.id.save:
			mLayoutServerSettings.setVisibility(View.GONE);
			break;
		}
	}

	@Override
	public void onDestroy(){
		super.onDestroy();
		mClient.release();
		mSession.release();
		stopScreenCapture();
		mSurfaceView.getHolder().removeCallback(this);

	}

	private void selectQuality() {
		int id = mRadioGroup.getCheckedRadioButtonId();
		RadioButton button = (RadioButton) findViewById(id);
		if (button == null) return;
		
		String text = button.getText().toString();
		Pattern pattern = Pattern.compile("(\\d+)x(\\d+)\\D+(\\d+)\\D+(\\d+)");
		Matcher matcher = pattern.matcher(text);

		matcher.find();
		int width = 800;
		int height = 640;
		int framerate = 30;
		int bitrate = 600000;

		mSession.setVideoQuality(new VideoQuality(800, 640, 30, 600000));
		Toast.makeText(this, ((RadioButton)findViewById(id)).getText(), Toast.LENGTH_SHORT).show();

		Log.d(TAG, "Selected resolution: "+width+"x"+height);
	}

	private void enableUI() {
		mButtonStart.setEnabled(true);
		mButtonCamera.setEnabled(true);
	}

	// Connects/disconnects to the RTSP server and starts/stops the stream
	public void toggleStream() {
		mProgressBar.setVisibility(View.VISIBLE);
		if (!mClient.isStreaming()) {
			String ip,port,path;
			startScreenCapture();
			// We save the content user inputs in Shared Preferences
			SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
			Editor editor = mPrefs.edit ();
			editor.putString("uri", "rtsp://a5f64c.entrypoint.cloud.wowza.com:1935/app-2e0d/9da37462");
			editor.putString("password", "f71aab17");
			editor.putString("username", "client3473");
			editor.commit();
			// We parse the URI written in the Editext
			Pattern uri = Pattern.compile("rtsp://(.+):(\\d*)/(.+)");
			Matcher m = uri.matcher(mEditTextURI.getText()); m.find();
			ip = m.group(1);
			port = m.group(2);
			path = m.group(3);

			mClient.setCredentials(mEditTextUsername.getText().toString(), mEditTextPassword.getText().toString());
			mClient.setServerAddress("a5f64c.entrypoint.cloud.wowza.com", 1935);
			mClient.setStreamPath("" + path);

			mClient.startStream();

		} else {
			// Stops the stream and disconnects from the RTSP server
			mClient.stopStream();
		}
	}

	private void logError(final String msg) {
		final String error = (msg == null) ? "Error unknown" : msg; 
		// Displays a popup to report the eror to the user
		AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
		builder.setMessage(msg).setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {}
		});
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	@Override
	public void onBitrateUpdate(long bitrate) {
		mTextBitrate.setText(""+bitrate/1000+" kbps");
	}

	@Override
	public void onPreviewStarted() {
		if (mSession.getCamera() == CameraInfo.CAMERA_FACING_FRONT) {
			mButtonFlash.setEnabled(false);
			mButtonFlash.setTag("off");
			mButtonFlash.setImageResource(R.drawable.ic_flash_on_holo_light);
		}
		else {
			mButtonFlash.setEnabled(true);
		}
	}	

	@Override
	public void onSessionConfigured() {

	}

	@Override
	public void onSessionStarted() {
		enableUI();
		mButtonStart.setImageResource(R.drawable.ic_switch_video_active);
		mProgressBar.setVisibility(View.GONE);
	}

	@Override
	public void onSessionStopped() {
		enableUI();
		mButtonStart.setImageResource(R.drawable.ic_switch_video);
		mProgressBar.setVisibility(View.GONE);
	}

	@Override
	public void onSessionError(int reason, int streamType, Exception e) {
		mProgressBar.setVisibility(View.GONE);
		switch (reason) {
		case Session.ERROR_CAMERA_ALREADY_IN_USE:
			break;
		case Session.ERROR_CAMERA_HAS_NO_FLASH:
			mButtonFlash.setImageResource(R.drawable.ic_flash_on_holo_light);
			mButtonFlash.setTag("off");
			break;
		case Session.ERROR_INVALID_SURFACE:
			break;
		case Session.ERROR_STORAGE_NOT_READY:
			break;
		case Session.ERROR_CONFIGURATION_NOT_SUPPORTED:
			VideoQuality quality = mSession.getVideoTrack().getVideoQuality();
			logError("The following settings are not supported on this phone: "+
			quality.toString()+" "+
			"("+e.getMessage()+")");
			e.printStackTrace();
			return;
		case Session.ERROR_OTHER:
			break;
		}

		if (e != null) {
			logError(e.getMessage());
			e.printStackTrace();
		}
	}

	@Override
	public void onRtspUpdate(int message, Exception e) {
		switch (message) {
		case RtspClient.ERROR_CONNECTION_FAILED:
		case RtspClient.ERROR_WRONG_CREDENTIALS:
			mProgressBar.setVisibility(View.GONE);
			enableUI();
			logError(e.getMessage());
			e.printStackTrace();
			break;
		}
	}


	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		mSession.startPreview();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		mClient.stopStream();
	}
}
