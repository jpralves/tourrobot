package net.jpralves.nxt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

/**
 * NXTControllerActivity Object.
 * 
 * <P>
 * Main Activity of the program
 * 
 * @author Joao Alves
 * @version 1.0
 */
public class NXTControllerActivity extends Activity implements TextToSpeech.OnInitListener,
		OnSharedPreferenceChangeListener, SurfaceHolder.Callback {

	private static final String TAG = NXTControllerActivity.class.getSimpleName();

	private static final int BESTIMAGEHEIGHT = 240;
	private static final int BESTIMAGEWIDTH = 320;

	private static final int MAXZIPFILE = 512000;

	private int port = 0;
	private InetAddress serverAddress;
	private String serverURL = "";
	private String calculatedSalt = "";

	private static final int REQUEST_ENABLE_BT = 1;

	private BluetoothAdapter bluetoothAdapter;
	private boolean askfordisableatend = false;

	private static TextToSpeech myTTS;

	private NXTControl nxtControl;

	private SharedPreferences prefs;

	private Intent service = null;

	private WebServer strServer = null;

	private boolean isServer = false;

	private Thread requestsThread = null;

	private final String resourceDirectory = Environment.getExternalStorageDirectory().getPath()
			+ File.separator + "nxtcontroller";

	private NXTControllerApp app = null;

	private boolean isNXT;

	private boolean stopRequestsThread;

	private SurfaceView surfaceView;

	private SurfaceHolder surfaceHolder;

	private Camera mCamera = null;

	private int jpegCompression = 50;

	/**
	 * Uses Text-to-Speech interface to talk
	 * 
	 * @param textToBeSpoken
	 *            must be in english
	 */
	public void speak(String textToBeSpoken) {

		if (myTTS != null) {
			if (BuildConfig.DEBUG)
				Log.d(TAG, "Speak - " + textToBeSpoken);
			myTTS.speak(textToBeSpoken, TextToSpeech.QUEUE_FLUSH, null);
		}
	}

	public void setLanguage(Context context, String languageToLoad) {
		if (BuildConfig.DEBUG)
			Log.d(TAG, "setting language");
		Locale locale;
		if (languageToLoad.equals(""))
			locale = new Locale(app.getTableString("android.systemlocale"));
		else
			locale = new Locale(languageToLoad);
		Locale current = getResources().getConfiguration().locale;
		if (current != null && current.equals(locale)) {
			if (BuildConfig.DEBUG)
				Log.d(TAG, "Already correct language set");
			return;
		}

		Locale.setDefault(locale);
		android.content.res.Configuration config = new android.content.res.Configuration();
		config.locale = locale;
		context.getResources().updateConfiguration(config,
				context.getResources().getDisplayMetrics());
		if (BuildConfig.DEBUG)
			Log.d(TAG, "Language set");
	}

	public String getLangString() {
		String lang = prefs.getString("locale", "");

		return lang.isEmpty() ? app.getTableString("android.systemlocale") : lang;
	}

	/**
	 * Initializes the Text-to-Speech layer
	 * 
	 * @param status
	 *            the status of initialization (must be TextToSpeech.SUCCESS)
	 * 
	 * @see <a
	 *      href="http://www.androidhive.info/2012/01/android-text-to-speech-tutorial/">http://www.androidhive.info/2012/01/android-text-to-speech-tutorial/</a>
	 */
	private void initSpeech(int status) {
		if (status == TextToSpeech.SUCCESS) {

			int result = myTTS.setLanguage(Locale.US);

			if (result == TextToSpeech.LANG_MISSING_DATA
					|| result == TextToSpeech.LANG_NOT_SUPPORTED) {
				if (BuildConfig.DEBUG)
					Log.e(TAG, "This Language is not supported");
			} else {
				if (BuildConfig.DEBUG)
					Log.d(TAG, "TTS Initilization completed!");
			}

		} else {
			if (BuildConfig.DEBUG)
				Log.e(TAG, "Initilization Failed!");
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.speech.tts.TextToSpeech.OnInitListener#onInit(int)
	 */
	public void onInit(int status) {
		initSpeech(status);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (isDebug()) {
			VersionedStrictModeWrapper.getInstance().init(this);
		}

		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

		if (prefs.getBoolean("textToSpeech", false)) {
			myTTS = new TextToSpeech(this, this);
		}
		app = (NXTControllerApp) getApplication();

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		app.setTableString("android.systemlocale", Locale.getDefault().getLanguage());
		setLanguage(getApplicationContext(), prefs.getString("locale", ""));

		setContentView(R.layout.activity_main);

		checkBlueToothState();
		resetAndroidButtonsState(false);
	}

	@SuppressLint("NewApi")
	@Override
	public void onStart() {
		super.onStart();

		TextView txtMenu = (TextView) findViewById(R.id.callMenu);
		boolean showMenu = true;

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			showMenu = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB);
		} else {
			showMenu = !ViewConfiguration.get(this).hasPermanentMenuKey();
		}

		if (showMenu) {
			ActionBar actionBar = getActionBar();
			if (actionBar != null) {
				txtMenu.setVisibility(TextView.GONE);
			} else {
				txtMenu.setOnTouchListener(new OnTouchListener() {
					public boolean onTouch(View arg0, MotionEvent arg1) {
						openOptionsMenu();
						return false;
					}
				});
			}
		} else {
			txtMenu.setVisibility(TextView.GONE);
		}
		startSensorService();
	}

	@Override
	public void onPause() {
		// Clean up
		if (BuildConfig.DEBUG)
			Log.d(TAG, "onPause");
		if (isServer) {
			stopPreview();
			stopVisualCounters();
		}
		super.onPause();
	}

	public void onDestroy() {
		if (BuildConfig.DEBUG)
			Log.d(TAG, "onDestroy");
		stopSensorService();
		stopPreview();
		stopVisualCounters();
		if (myTTS != null) {
			myTTS.stop();
			myTTS.shutdown();
		}
		super.onDestroy();
	}

	/**
	 * returns the value of debug state
	 * 
	 * @return the value of the debugging flag from
	 *         ApplicationInfo.FLAG_DEBUGGABLE
	 */
	private boolean isDebug() {
		try {
			PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			return (packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) == ApplicationInfo.FLAG_DEBUGGABLE;
		} catch (PackageManager.NameNotFoundException e) {
			if (BuildConfig.DEBUG)
				Log.e(TAG, e.getMessage(), e);
		}
		return false;
	}

	protected void onResume() {
		super.onResume();
		if (BuildConfig.DEBUG)
			Log.d(TAG, "onResume");
		if (isServer) {
			startPreview();
			startVisualCounters();
		}
	}

	protected void onStop() {
		if (BuildConfig.DEBUG)
			Log.d(TAG, "onStop");
		super.onStop();
	}

	/**
	 * Checks the state of bluetooth subinterface and tries to enable it, if it
	 * off
	 */
	private void checkBlueToothState() {
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter == null) {
			// stateBluetooth.setText("Bluetooth NOT support");
		} else {
			if (bluetoothAdapter.isEnabled()) {
				if (bluetoothAdapter.isDiscovering()) {
					// stateBluetooth.setText("Bluetooth is currently in device discovery process.");
				} else {
					// stateBluetooth.setText("Bluetooth is Enabled.");
				}
			} else {
				// Bluetooth is NOT Enabled
				askfordisableatend = true;
				Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
			}
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_ENABLE_BT) {
			checkBlueToothState();
		}
	}

	/**
	 * Asks for a confirmation when is finishing activity calls quit() if user
	 * says Yes.
	 * 
	 * @see android.app.Activity#finish()
	 */
	public void finish() {
		new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_alert)
				.setMessage(getString(R.string.exitprompt)).setTitle(getString(R.string.exittitle))
				.setCancelable(false)
				.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						quit();
					}
				}).setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
					}
				}).show();
	}

	/**
	 * Closes all remaining connections and services
	 */
	public void quit() {
		clearBTstate();
		stopNXT();
		stopWebServer();
		stopSensorService();
		super.finish();
	}

	/**
	 * Puts the bluetooth state back to what it was
	 */
	protected void clearBTstate() {
		if (askfordisableatend) {
			if (bluetoothAdapter.isEnabled()) {
				Toast.makeText(this, getString(R.string.disable_bluetooth), Toast.LENGTH_LONG)
						.show();
				bluetoothAdapter.disable();
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.mainmenu, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@SuppressLint("NewApi")
	@Override
	public boolean onMenuOpened(int featureId, Menu menu) {

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			invalidateOptionsMenu();
		} else {
			menu.clear();
			onCreateOptionsMenu(menu);
		}
		return super.onMenuOpened(featureId, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.prefsMenu:
			showSettings();
			break;
		case R.id.diagsMenu:
			showDiags();
			break;
		case R.id.fsbrowserMenu:
			showFSBrowser();
			break;
		case R.id.aboutMenu:
			showAbout();
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * Sets the visibility of Android control buttons according to preferences
	 * "androidButtons" and sets them clickable according to parameter
	 * clickState
	 * 
	 * @param clickState
	 *            makes buttons clickable
	 */
	private void resetAndroidButtonsState(boolean clickState) {

		if (BuildConfig.DEBUG)
			Log.d(TAG, "resetAndroidButtons(" + clickState + ")");

		boolean androidButtons = prefs.getBoolean("androidButtons", true);

		ImageButton btnForward = (ImageButton) findViewById(R.id.buttonForward);
		ImageButton btnLeft = (ImageButton) findViewById(R.id.buttonLeft);
		ImageButton btnRight = (ImageButton) findViewById(R.id.buttonRight);
		ImageButton btnStop = (ImageButton) findViewById(R.id.buttonStop);
		int vis;
		if (androidButtons) {
			// Put buttons visible and clickable
			vis = View.VISIBLE;
		} else {
			// Put buttons invisible
			vis = View.INVISIBLE;
		}
		btnForward.setVisibility(vis);
		btnLeft.setVisibility(vis);
		btnRight.setVisibility(vis);
		btnStop.setVisibility(vis);

		btnForward.setClickable(clickState);
		btnLeft.setClickable(clickState);
		btnRight.setClickable(clickState);
		btnStop.setClickable(clickState);
	}

	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		if (key.equals("androidButtons"))
			resetAndroidButtonsState(false);
		// TODO: Será que está correcto ?
		if (key.equals("locale")) {
			setLanguage(getBaseContext(), prefs.getString("locale", ""));
			setContentView(R.layout.activity_main);
		}
	}

	/**
	 * Calls settingsActivity
	 */
	private void showSettings() {
		Intent settingsActivity = new Intent(getBaseContext(), PreferencesActivity.class);
		startActivity(settingsActivity);
		prefs.registerOnSharedPreferenceChangeListener(this);
	}

	/**
	 * Calls diagnisticsActivity
	 */
	private void showDiags() {
		Intent intent = new Intent(this, DiagnosticsActivity.class);
		startActivity(intent);
	}

	/**
	 * Calls aboutActivity
	 */
	private void showAbout() {
		Intent intent = new Intent(this, AboutActivity.class);
		startActivity(intent);
	}

	/**
	 * Calls fsBrowserActivity
	 */
	private void showFSBrowser() {
		Intent intent = new Intent(this, FSBrowserActivity.class);
		startActivity(intent);
	}

	/**
	 * Registers webserver on the webserver if the android has a non-private
	 * address or a natPort
	 */
	private void registerWebServer() {
		String baseServerURL;
		String internalServerURL = "http://" + serverAddress.getHostAddress() + ":" + port;
		int natPort = Integer.parseInt(prefs.getString("webserverNATPort", "0"));
		if (natPort > 0) {
			baseServerURL = "http://@PUBIP@:" + natPort;
		} else {
			baseServerURL = internalServerURL;
		}
		if (!serverAddress.isSiteLocalAddress() || (natPort > 0)) {
			if (registerSite(baseServerURL)) {
				serverURL = prefs.getString("pubSiteAddress", getString(R.string.pubSite));
			} else {
				serverURL = internalServerURL;
			}
		} else {
			serverURL = internalServerURL;
			// TODO Codigo upnp para registar ....
		}
	}

	/**
	 * unRegisters webserver on the webserver if the android has a non-private
	 * address or a natPort
	 */
	private void unregisterWebServer() {
		int natPort = Integer.parseInt(prefs.getString("webserverNATPort", "0"));
		if (serverAddress != null) {
			if (!serverAddress.isSiteLocalAddress() || (natPort > 0)) {
				unregisterSite();
			}
		}
	}

	/**
	 * Initializes NXT according to preferences
	 */
	private void startNXT() {
		isNXT = false;
		String nxtName = prefs.getString("editBrickName", "NXT");
		int motorLPos = Integer.parseInt(prefs.getString("motorLeft", "1"));
		int motorRPos = Integer.parseInt(prefs.getString("motorRight", "2"));
		int motorFlags = (prefs.getBoolean("nxtBrake", true) ? 0x01 : 0)
				+ (Integer.parseInt(prefs.getString("nxtMotorDirection", "1")) == 1 ? 0x02 : 0);
		int minDistance = prefs.getInt("nxtMinDistance", 20);
		int initSpeed = prefs.getInt("nxtSpeed", 300);
		int touchPos = Integer.parseInt(prefs.getString("touchSensor", "1"));
		int colorPos = Integer.parseInt(prefs.getString("colorSensor", "2"));
		int sonicleftPos = Integer.parseInt(prefs.getString("volumetricSensorLeft", "3"));
		int sonicrightPos = Integer.parseInt(prefs.getString("volumetricSensorRight", "4"));
		int sonicfrontPos = Integer.parseInt(prefs.getString("volumetricSensorFront", "0"));

		nxtControl = new NXTControl(nxtName, motorLPos, motorRPos, motorFlags, initSpeed,
				minDistance, touchPos, colorPos, sonicleftPos, sonicrightPos, sonicfrontPos,
				(NXTControllerApp) getApplication());
		isNXT = nxtControl.connectToNXT();
	}

	/**
	 * Stops all activity of the NXT and closes comunications
	 */
	public void stopNXT() {
		if (nxtControl != null) {
			nxtControl.stopMotors();
			nxtControl.close();
			nxtControl = null;
			isNXT = false;
		}
	}

	private class startConnectionsTask extends AsyncTask<Void, Integer, Void> {

		private ProgressDialog dialog;
		private Context context;
		private String msg = "";

		public startConnectionsTask(Context c) {
			context = c;
		}

		protected void onPreExecute() {
			super.onPreExecute();
			isServer = false;
			dialog = new ProgressDialog(context);
			dialog.setMessage(getString(R.string.msg_loading));
			dialog.setIndeterminate(true);
			dialog.setCancelable(false);
			dialog.show();
		}

		@Override
		protected Void doInBackground(Void... params) {
			msg = getString(R.string.msg_setup_nxt);
			publishProgress(0);
			startNXT();
			msg = getString(R.string.msg_setup_webserver);
			publishProgress(50);
			startWebServer();
			return null;
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			dialog.setMessage(msg);
		}

		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			if (dialog.isShowing()) {
				dialog.dismiss();
			}

			TextView tvNxt = (TextView) findViewById(R.id.NXTConnect);
			tvNxt.setTextColor(isNXT ? getResources().getColor(R.color.GREEN) : getResources()
					.getColor(R.color.RED));
			tvNxt.setTypeface(Typeface.DEFAULT_BOLD);

			resetAndroidButtonsState(isNXT);

			TextView tvWeb = (TextView) findViewById(R.id.WebServer);
			tvWeb.setTextColor(isServer ? getResources().getColor(R.color.GREEN) : getResources()
					.getColor(R.color.RED));
			tvWeb.setTypeface(Typeface.DEFAULT_BOLD);

			if (isServer) {
				TextView tv = (TextView) findViewById(R.id.textInfo);
				tv.setText(getString(R.string.gotourl) + serverURL);
			}
		}
	}

	private class stopConnectionsTask extends AsyncTask<Void, Integer, Void> {

		private ProgressDialog dialog;
		private Context context;
		private String msg = "";

		public stopConnectionsTask(Context c) {
			context = c;
		}

		protected void onPreExecute() {
			super.onPreExecute();
			dialog = new ProgressDialog(context);
			dialog.setMessage(getString(R.string.msg_finishing));
			dialog.setIndeterminate(true);
			dialog.setCancelable(false);
			dialog.show();
		}

		@Override
		protected Void doInBackground(Void... params) {
			if (isNXT) {
				msg = getString(R.string.msg_disable_nxt);
				publishProgress(0);
				stopNXT();
			}
			if (isServer) {
				msg = getString(R.string.msg_disable_webserver);
				publishProgress(50);
				stopWebServer();
			}
			return null;
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			dialog.setMessage(msg);
		}

		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			if (dialog.isShowing()) {
				dialog.dismiss();
			}

			TextView tv = (TextView) findViewById(R.id.textInfo);
			ColorStateList oldColors = tv.getTextColors();

			TextView tvNxt = (TextView) findViewById(R.id.NXTConnect);
			tvNxt.setTextColor(oldColors);
			tvNxt.setTypeface(Typeface.DEFAULT);

			TextView tvWeb = (TextView) findViewById(R.id.WebServer);
			tvWeb.setTextColor(oldColors);
			tvWeb.setTypeface(Typeface.DEFAULT);

			resetAndroidButtonsState(isNXT);

			tv.setText(getString(R.string.disconnected));
		}
	}

	/**
	 * Calls asyncTask to connect to NXT and start webserver
	 */
	private void startConnections() {
		startConnectionsTask myTask = new startConnectionsTask(this);
		myTask.execute();
	}

	/**
	 * Calls asyncTask to disconnect from NXT and stop webserver
	 */
	private void stopConnections() {
		stopConnectionsTask myTask = new stopConnectionsTask(this);
		myTask.execute();
	}

	/**
	 * Handles Clicks to the Android main activity
	 * 
	 * @param arg0
	 *            the called button
	 */
	public void HandleClick(View arg0) {
		switch (arg0.getId()) {
		case R.id.buttonConnect:
			ToggleButton btn = (ToggleButton) arg0;
			if (btn.isChecked()) {
				startConnections();
			} else {
				stopConnections();
			}
			break;
		case R.id.buttonForward:
			processAction("forward");
			break;
		case R.id.buttonLeft:
			processAction("left");
			break;
		case R.id.buttonRight:
			processAction("right");
			break;
		case R.id.buttonStop:
			processAction("stop");
			break;
		}
	}

	/**
	 * Starts the sensorService
	 * <p>
	 * This service is responsible for registering all changes to sensors,
	 * battery and android communications signals
	 */
	private void startSensorService() {
		if (service == null) {
			service = new Intent(this, SensorService.class);
			startService(service);
		}
	}

	/**
	 * Stops sensorService
	 */
	private void stopSensorService() {
		if (service != null) {
			stopService(service);
			service = null;
		}
	}

	/**
	 * Starts the camera preview if enabled in preferences
	 * <p>
	 * this method is responsible for adding the callback to create the
	 * surfaceView
	 */
	@SuppressWarnings("deprecation")
	private void startPreview() {
		if (BuildConfig.DEBUG)
			Log.d(TAG, "startPreview");
		if (prefs.getBoolean("isVideoEnabled", false)) {
			speak("Starting Video");

			jpegCompression = prefs.getInt("jpegCompression", 50);

			surfaceView = (SurfaceView) findViewById(R.id.surface_camera);
			surfaceHolder = surfaceView.getHolder();

			surfaceHolder.addCallback(this);
			surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
			surfaceView.setVisibility(View.VISIBLE);
			// surfaceHolder.setSizeFromLayout();
		}
	}

	/**
	 * Stops camera preview
	 * <p>
	 * It is called whenever the activity changes to background or is ended
	 */
	private void stopPreview() {
		if (BuildConfig.DEBUG)
			Log.d(TAG, "stopPreview");
		if (prefs.getBoolean("isVideoEnabled", false)) {
			SurfaceView sv = (SurfaceView) findViewById(R.id.surface_camera);
			sv.setVisibility(View.INVISIBLE);

			if (mCamera != null) {
				mCamera.setPreviewCallback(null);
				sv.getHolder().removeCallback(this);
				mCamera.stopPreview();
				// mCamera.lock();
				mCamera.release();
				mCamera = null;
			}
		}
	}

	/**
	 * Calls the lower level of NXT to send the instruction
	 * 
	 * @param command
	 *            can be "forward", "left", "right", "stop"
	 * @return the command executed or error
	 */
	private String processAction(String command) {
		String res = command;
		if (nxtControl != null) {
			if (command.compareToIgnoreCase("forward") == 0) {
				nxtControl.moveForward();
			} else if (command.compareToIgnoreCase("left") == 0) {
				nxtControl.moveLeft();
			} else if (command.compareToIgnoreCase("right") == 0) {
				nxtControl.moveRight();
			} else if (command.compareToIgnoreCase("stop") == 0) {
				nxtControl.stopMotors();
			} else {
				res = "Invalid command";
			}
			// Uturn and 90 left and 90 right
		} else {
			res = "Not ready";
		}
		return res;
	}

	/**
	 * Configures dynamic hooks for webserver
	 * <p>
	 * These include:
	 * <ul>
	 * <li>"info.json" - return of information about the android environment</li>
	 * <li>"command.cgi" - receives commands to the NXT robot</li>
	 * <li>"live.jpg" - returns the last image recorded by the android camera</li>
	 * </ul>
	 * 
	 * @param baseaddress
	 *            prefix of url addresses
	 */
	private void configureDynamicHooks(String baseaddress) {
		strServer.addHook(baseaddress + "info.json", new HttpHook() {
			@SuppressLint("SimpleDateFormat")
			public Response execute(String uri, String method, Properties header, Properties parms,
					Properties files) {
				String mimetype = Response.MIME_JSON;
				String newList = "";
				JSONObject jsonNXT = new JSONObject();
				JSONObject jsonAndroid = new JSONObject();
				JSONObject jsonBrowser = new JSONObject();
				JSONObject json = new JSONObject();
				try {
					jsonNXT.put("battery", app.getTableValue("nxt.battery"));

					if (Integer.parseInt(prefs.getString("volumetricSensorFront", "0")) == 0) {
						jsonNXT.put("leftvolsensor",
								app.getTableValue("nxt.sensor.ultrasonic-left"));
						jsonNXT.put("rightvolsensor",
								app.getTableValue("nxt.sensor.ultrasonic-right"));
					} else {
						jsonNXT.put("frontvolsensor",
								app.getTableValue("nxt.sensor.ultrasonic-front"));
					}
					jsonNXT.put("touchsensor", app.getTableValue("nxt.sensor.touch"));
					jsonNXT.put("colorsensor", app.getTableValue("nxt.sensor.color.red"));
					jsonNXT.put("speed", app.getTableValue("nxt.speed"));
					jsonNXT.put("mindistance", prefs.getInt("nxtMinDistance", 20));
					jsonNXT.put("control", app.getTableValue("nxt.control"));
					jsonNXT.put("lefttacho", app.getTableValue("nxt.motor.lefttacho"));
					jsonNXT.put("righttacho", app.getTableValue("nxt.motor.righttacho"));

					SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HHmmss");
					String currentDateandTime = sdf.format(new Date());

					jsonAndroid.put("video", prefs.getBoolean("isVideoEnabled", true) ? 1 : 0);

					// jsonAndroid.put("serverip",
					// serverAddress.getHostAddress());
					// jsonAndroid.put("serverport", port);
					jsonAndroid.put("datetime", currentDateandTime);

					jsonAndroid.put("battery", app.getTableValue("android.battery"));
					jsonAndroid.put("wifi", app.getTableValue("android.wifi.signal.percent"));
					jsonAndroid.put("bssid", app.getTableValue("android.wifi.bssid"));
					jsonAndroid.put("wifispeed", app.getTableValue("android.wifi.linkspeed"));
					jsonAndroid.put("mobile", app.getTableValue("android.phone.signal.percent"));
					jsonAndroid.put("mobiletype", app.getTableValue("android.phone.nettype"));
					jsonAndroid.put("celllocation", app.getTableValue("android.phone.celllocation"));

					jsonAndroid.put("sensor.laccel.x", app.getTableValue("android.sensor.laccel.x"));
					jsonAndroid.put("sensor.laccel.y", app.getTableValue("android.sensor.laccel.y"));
					jsonAndroid.put("sensor.laccel.z", app.getTableValue("android.sensor.laccel.z"));
					jsonAndroid.put("model", Utils.getDeviceModel());

					jsonBrowser.put("timeout", prefs.getInt("browserRefreshRate", 500));

					json.put("nxt", jsonNXT);
					json.put("android", jsonAndroid);
					json.put("browser", jsonBrowser);
				} catch (JSONException e) {
					if (BuildConfig.DEBUG)
						Log.e(TAG, e.getMessage(), e);
				} finally {
					newList = json.toString();
				}
				return new Response(Response.HTTP_OK, mimetype, new ByteArrayInputStream(newList
						.getBytes()));
			}
		});

		strServer.addHook(baseaddress + "command.cgi", new HttpHook() {
			public Response execute(String uri, String method, Properties header, Properties parms,
					Properties files) {
				String mimetype = Response.MIME_JSON;
				String newList = "";
				JSONObject json = new JSONObject();
				try {
					json.put("action", (String) processAction((String) parms.get("action")));
				} catch (JSONException e) {
					if (BuildConfig.DEBUG)
						Log.e(TAG, e.getMessage(), e);
				} finally {
					newList = json.toString();
				}
				return new Response(Response.HTTP_OK, mimetype, new ByteArrayInputStream(newList
						.getBytes()));
			}

		});

		if (prefs.getBoolean("isVideoEnabled", true)) {
			strServer.addHook(baseaddress + "live.jpg", new HttpHook() {
				public Response execute(String uri, String method, Properties header,
						Properties parms, Properties files) {
					String mimetype = Response.MIME_JPEG;
					ByteArrayInputStream bais = app.getLastImage();
					if (bais == null) {
						String error = "SERVER INTERNAL ERROR: Serve() returned a null response.";
						return new Response(Response.HTTP_INTERNALERROR, Response.MIME_PLAINTEXT,
								new ByteArrayInputStream(error.getBytes()));
					} else {
						return new Response(Response.HTTP_OK, mimetype, bais);
					}
				}

			});
		}
	}

	/**
	 * Configures packaged (zip) hooks for webserver
	 * 
	 * @param baseaddress
	 *            prefix of url addresses
	 * @param resourceid
	 *            the raw resource ID (The ZIP)
	 * @param defaultPage
	 *            the page thats is associated with baseaddress
	 */
	private void configureZipHooks(String baseaddress, int resourceid, String defaultPage) {
		final Hashtable<String, byte[]> localzip = new Hashtable<String, byte[]>();

		ZipInputStream zin = new ZipInputStream(getResources().openRawResource(resourceid));
		ZipEntry ze = null;
		try {
			while ((ze = zin.getNextEntry()) != null) {
				if (!ze.isDirectory()) {
					int pos = 0;
					byte[] b = null;
					final String filename = ze.getName();
					try {
						if (ze.getSize() > MAXZIPFILE) {
							if (BuildConfig.DEBUG)
								Log.e(TAG, ze.getName() + " - Maximum extract size oversized! FIX!");

						} else {
							// Log.d(TAG,
							// "Unzipping " + ze.getName() + " Size: " + ((int)
							// ze.getSize()));
							b = new byte[(int) ze.getSize()];
							int size;
							byte[] buffer = new byte[2048];
							while ((size = zin.read(buffer, 0, buffer.length)) != -1) {
								System.arraycopy(buffer, 0, b, pos, size);
								pos += size;
							}
						}
						zin.closeEntry();
						localzip.put(baseaddress + filename, b);
					} catch (IOException e) {
						if (BuildConfig.DEBUG)
							Log.e(TAG, e.getMessage(), e);
					}
				}
			}
		} catch (Exception e) {
			if (BuildConfig.DEBUG)
				Log.e(TAG, e.getMessage(), e);
		}
		try {
			zin.close();
		} catch (IOException e) {
			if (BuildConfig.DEBUG)
				Log.e(TAG, e.getMessage(), e);
		}

		Enumeration<String> reszip = localzip.keys();
		while (reszip.hasMoreElements()) {
			final String k = reszip.nextElement();
			// Log.d(TAG, "addzipHook - " + k);
			if (k.endsWith(defaultPage)) {
				strServer.addHook(baseaddress, new HttpHook() {
					private byte[] raw = localzip.get(k);

					public Response execute(String uri, String method, Properties header,
							Properties parms, Properties files) {

						String buffer = new String(raw);
						buffer = buffer.replace("[SALT]", getSaltValue());
						buffer = buffer.replace("[LANG]", getLangString());

						return new Response(Response.HTTP_OK, Response.MIME_HTML,
								new ByteArrayInputStream(buffer.getBytes()));
					}
				});

			} else {
				strServer.addHook(k, new HttpHook() {
					private byte[] raw = localzip.get(k);

					public Response execute(String uri, String method, Properties header,
							Properties parms, Properties files) {
						if (getMimeType(k).equals(Response.MIME_HTML)
								|| getMimeType(k).equals(Response.MIME_JAVASCRIPT)) {
							String buffer = new String(raw);
							buffer = buffer.replace("[SALT]", getSaltValue());
							buffer = buffer.replace("[LANG]", getLangString());
							raw = buffer.getBytes();
						}
						return new Response(Response.HTTP_OK, getMimeType(k),
								new ByteArrayInputStream(raw));
					}
				});
			}
		}
	}

	/**
	 * Configures default hooks for webserver
	 * 
	 */
	private void configureDefaultHooks() {
		configureZipHooks("/", R.raw.webroot, "login.html");
	}

	/**
	 * Starts the server and configures all hooks
	 */
	private void startServer() {
		try {
			strServer = new WebServer((NXTControllerApp) getApplication(), port, resourceDirectory);

			String secretPrefix = getSecretPrefix();
			configureDynamicHooks(secretPrefix);
			if (Integer.parseInt(prefs.getString("nxtBrowserVersion", "2")) == 1) {
				configureZipHooks(secretPrefix, R.raw.browserv1, "index.html");
			} else {
				configureZipHooks(secretPrefix, R.raw.browserv2, "index.html");
			}
			configureDefaultHooks();
			isServer = true;
		} catch (IOException e) {
			if (BuildConfig.DEBUG)
				Log.e(TAG, e.getMessage(), e);
			isServer = false;
		}
	}

	/**
	 * stops the server
	 */
	private void stopServer() {
		isServer = false;
		if (strServer != null) {
			strServer.stop();
			strServer.clearHooks();
			strServer = null;
		}
	}

	/**
	 * starts the webserver
	 * <p>
	 * <ul>
	 * <li>starts webserver on defined preferences port</li>
	 * <li>starts camera preview</li>
	 * <li>registers webserver starts visualcounters</li>
	 * </ul>
	 */
	public void startWebServer() {
		serverAddress = Networking.getLocalIpAddress();
		if (serverAddress != null) {
			try {
				port = Integer.parseInt(prefs.getString("webserverPort", "8080"));
				startServer();
				runOnUiThread(new Runnable() { // Must Run in the UiThread
					public void run() {
						startPreview();
					}
				});
				registerWebServer();
			} catch (Exception e) {
				if (BuildConfig.DEBUG)
					Log.e(TAG, e.getMessage(), e);
				// tv.setText(e.toString());
			}

		} else {
			serverURL = "";
			TextView tv = (TextView) findViewById(R.id.textInfo);
			tv.setText(getString(R.string.no_network));
			if (BuildConfig.DEBUG)
				Log.d(TAG, "No serverAddress detected.");
		}
		startVisualCounters();
	}

	/**
	 * stops the webserver
	 * <p>
	 * stops server stops camera preview stops visualcounters
	 */
	public void stopWebServer() {

		stopServer();
		unregisterWebServer();
		if (prefs.getBoolean("isVideoEnabled", false)) {
			speak("Stopping Video");
			runOnUiThread(new Runnable() { // Must Run in the UiThread
				public void run() {
					stopPreview();
				}

			});
		}

		stopVisualCounters();
	}

	/**
	 * starts thread to show webserver counters every second
	 */
	public void startVisualCounters() {
		stopRequestsThread = false;
		requestsThread = new Thread(new Runnable() {
			public void run() {
				while (!stopRequestsThread) {
					runOnUiThread(new Runnable() {
						public void run() {
							TextView tv = (TextView) findViewById(R.id.requestsText);
							tv.setText("Reqs: " + app.getTableInt("webserver.requests"));
							tv = (TextView) findViewById(R.id.inoutText);
							tv.setText(Utils.formatMultibyte(app.getTableLong("webserver.inbytes"),
									true)
									+ "/"
									+ Utils.formatMultibyte(app.getTableLong("webserver.outbytes"),
											true));
						}
					});
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						if (BuildConfig.DEBUG)
							Log.e(TAG, e.getMessage(), e);
					}
				}
			}
		});
		requestsThread.setDaemon(true);
		requestsThread.start();
	}

	/**
	 * stops the thread to show webserver counters every second
	 */
	public void stopVisualCounters() {
		if (requestsThread != null) {
			stopRequestsThread = true;
		}
	}

	/**
	 * gets a salt value randomly generated
	 * 
	 * @return generated salt value
	 */
	protected String getSaltValue() {
		if (calculatedSalt.isEmpty()) {

			Random randInt = new Random();
			String toHash = "";
			int c;
			while (toHash.length() < 40) {
				c = randInt.nextInt(255);
				toHash += (char) c;
			}

			String hash = null;
			try {
				MessageDigest digest = MessageDigest.getInstance("SHA-1");
				digest.update(toHash.getBytes(), 0, toHash.length());
				hash = new BigInteger(1, digest.digest()).toString(16);
				while (hash.length() < 40) {
					hash = "0" + hash;
				}
			} catch (NoSuchAlgorithmException e) {
				if (BuildConfig.DEBUG)
					Log.e(TAG, e.getMessage(), e);
			}
			calculatedSalt = hash;
			if (BuildConfig.DEBUG)
				Log.d(TAG, "calculatedSalt: " + calculatedSalt);
		}
		return calculatedSalt;
	}

	/**
	 * obtains the mime type based on uri
	 * 
	 * @param uri
	 *            the uri address
	 * @return string of mime type representation
	 */
	private String getMimeType(String uri) {
		String mime = null;
		int dot = uri.lastIndexOf('.');
		if (dot >= 0)
			mime = (String) NanoHTTPD.theMimeTypes.get(uri.substring(dot + 1).toLowerCase(
					Locale.getDefault()));
		if (mime == null)
			mime = NanoHTTPD.MIME_DEFAULT_BINARY;
		return mime;
	}

	/**
	 * returns the secret prefix
	 * <p>
	 * it is calculated with an hash of the combination of salt value and
	 * password
	 * 
	 * @return the secret prefix (its an SHA-1 hash url)
	 */
	private String getSecretPrefix() {

		String password = prefs.getString("webserverPassword", null);
		if (BuildConfig.DEBUG)
			Log.i(TAG, "Password is: " + password);

		String toHash = getSaltValue() + password;
		String hash = null;
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			digest.update(toHash.getBytes(), 0, toHash.length());
			hash = new BigInteger(1, digest.digest()).toString(16);
			while (hash.length() < 40) {
				hash = "0" + hash;
			}
		} catch (NoSuchAlgorithmException e) {
			if (BuildConfig.DEBUG)
				Log.e(TAG, e.getMessage(), e);
		}
		if (BuildConfig.DEBUG)
			Log.i(TAG, "Hash is: " + hash);

		return "/" + hash + "/";
	}

	/**
	 * Registers the webserver running on android in a public server
	 * <p>
	 * Sends some statistics to the server like the build version and android
	 * Build FINGERPRINT
	 * 
	 * @param url
	 *            the base url (its an url with an ip address)
	 * @return true of the registration worked
	 */
	private boolean registerSite(String url) {
		// Create a new HttpClient and Post Header

		if (BuildConfig.DEBUG)
			Log.i(TAG, "Registering webserver: " + url);
		HttpParams params = new BasicHttpParams();
		params.setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
		HttpClient httpclient = new DefaultHttpClient(params);
		HttpPost httppost = new HttpPost(prefs.getString("pubSiteAddress",
				getString(R.string.pubSite)));
		boolean ret = false;
		String pubsitekey = prefs.getString("pubsiteKey", null);
		try {
			String version = "";

			PackageManager manager = this.getPackageManager();
			try {
				PackageInfo info = manager.getPackageInfo(this.getPackageName(), 0);
				version = info.versionName;
			} catch (NameNotFoundException e) {
				if (BuildConfig.DEBUG)
					Log.e(TAG, e.getMessage(), e);
			}
			// Add parameters
			List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
			nameValuePairs.add(new BasicNameValuePair("sk", pubsitekey));
			nameValuePairs.add(new BasicNameValuePair("nl", url));
			nameValuePairs.add(new BasicNameValuePair("v", version));
			nameValuePairs.add(new BasicNameValuePair("i", android.os.Build.FINGERPRINT));
			httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

			// Execute HTTP Post Request
			HttpResponse response = httpclient.execute(httppost);
			String str = new String(EntityUtils.toByteArray(response.getEntity()), "UTF8");
			if (BuildConfig.DEBUG)
				Log.d(TAG, "register Response:" + str);
			ret = str.equals("OK");
		} catch (ClientProtocolException e) {
			if (BuildConfig.DEBUG)
				Log.e(TAG, "Error registering webserver", e);
			ret = false;
		} catch (IOException e) {
			if (BuildConfig.DEBUG)
				Log.e(TAG, "Error registering webserver", e);
			ret = false;
		}
		return ret;
	}

	/**
	 * clears the registration of the webserver in the public server
	 * 
	 * @return true if it worked
	 */
	private boolean unregisterSite() {
		// Create a new HttpClient and Post Header
		if (BuildConfig.DEBUG)
			Log.i(TAG, "unRegistering webserver");
		HttpClient httpclient = new DefaultHttpClient();
		HttpPost httppost = new HttpPost(prefs.getString("pubSiteAddress",
				getString(R.string.pubSite)));
		boolean ret = false;
		String pubsitekey = prefs.getString("pubsiteKey", null);

		try {
			// Add parameters
			List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
			nameValuePairs.add(new BasicNameValuePair("sk", pubsitekey));
			nameValuePairs.add(new BasicNameValuePair("dl", "1"));
			httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

			// Execute HTTP Post Request
			HttpResponse response = httpclient.execute(httppost);
			String str = new String(EntityUtils.toByteArray(response.getEntity()), "UTF8");
			if (BuildConfig.DEBUG)
				Log.d(TAG, "unregister Response:" + str);
			ret = str.equals("OK");
		} catch (ClientProtocolException e) {
			if (BuildConfig.DEBUG)
				Log.e(TAG, "Error unregistering webserver", e);
			ret = false;
		} catch (IOException e) {
			if (BuildConfig.DEBUG)
				Log.e(TAG, "Error unregistering webserver", e);
			ret = false;
		}
		return ret;
	}

	/**
	 * Changes camera parameters when surfaceChanged is called
	 * 
	 * @see android.view.SurfaceHolder.Callback#surfaceChanged(android.view.SurfaceHolder,
	 *      int, int, int)
	 */
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		if (BuildConfig.DEBUG)
			Log.d(TAG, "surfaceChanged");

		if (mCamera != null) {
			setupCamera();
			mCamera.startPreview();
		}
	}

	/**
	 * setups the camera parameters
	 */
	private void setupCamera() {
		if (mCamera != null) {
			Parameters parameters = mCamera.getParameters();

			List<Camera.Size> previewSizes = parameters.getSupportedPreviewSizes();
			if (previewSizes != null) {
				Camera.Size previewSize = getBestPreviewSize(previewSizes);
				parameters.setPreviewSize(previewSize.width, previewSize.height);
			}

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
				// parameters.set("orientation", "portrait");
				// parameters.set("rotation", 90);
				// mCamera.setDisplayOrientation(90);
				int angle;
				Display display = this.getWindowManager().getDefaultDisplay();
				switch (display.getRotation()) {
				case Surface.ROTATION_0: // This is display orientation
					angle = 90; // This is camera orientation
					break;
				case Surface.ROTATION_90:
					angle = 0;
					break;
				case Surface.ROTATION_180:
					angle = 270;
					break;
				case Surface.ROTATION_270:
					angle = 180;
					break;
				default:
					angle = 90;
					break;
				}
				Log.v(TAG, "Camera preview angle: " + angle);
				mCamera.setDisplayOrientation(angle);
			} else {
				if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
					parameters.set("orientation", "portrait");
					parameters.set("rotation", 90);
				}
				if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
					parameters.set("orientation", "landscape");
					parameters.set("rotation", 90);
				}
			}
			// Set focus mode
			List<String> focus = parameters.getSupportedFocusModes();
			if (focus != null)
				parameters.setFocusMode(focus.get(0));
			// Apply parameters to camera object
			mCamera.setParameters(parameters);

			mCamera.setPreviewCallback(new cam_PreviewCallback());
		} else
			Log.i(TAG, "setupCamera- mCamera=null");
	}

	/**
	 * setups the camera object when surfaceCreated is called
	 * 
	 * @see android.view.SurfaceHolder.Callback#surfaceCreated(android.view.SurfaceHolder)
	 */
	public void surfaceCreated(SurfaceHolder holder) {
		if (BuildConfig.DEBUG)
			Log.d(TAG, "surfaceCreated");

		if (mCamera != null) {
			mCamera.release();
		}
		mCamera = Camera.open();

		if (mCamera == null) { // Try front Facing Camera
			mCamera = Camera.open(2);
		}
		if (mCamera != null) {
			// setupCamera();
			try {
				mCamera.setPreviewDisplay(holder);
			} catch (IOException e) {
				if (BuildConfig.DEBUG)
					Log.e(TAG, e.getMessage(), e);
			}
		}
	}

	/**
	 * Disables camera preview on surfaceDestroyed
	 * 
	 * @see android.view.SurfaceHolder.Callback#surfaceDestroyed(android.view.SurfaceHolder)
	 */
	public void surfaceDestroyed(SurfaceHolder holder) {

		if (mCamera != null) {
			mCamera.setPreviewCallback(null);
			holder.removeCallback(this);
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
		}
	}

	/**
	 * Processes the information returned by the preview callback
	 * 
	 * @author Joao Alves
	 * 
	 */
	private class cam_PreviewCallback implements PreviewCallback {
		public void onPreviewFrame(byte[] data, Camera camera) {

			// Log.d(TAG, "onPreviewFrame");

			Camera.Parameters parameters = camera.getParameters();
			Size size = parameters.getPreviewSize();
			// Log.d(TAG, "Preview size W:" + size.width + " H:" + size.height);

			int[] rgb_rotated = new int[size.width * size.height];

			// Decode Yuv data to integer array
			decode_rotateYUV420SP(rgb_rotated, data, size.width, size.height);

			Bitmap bmp = Bitmap.createBitmap(rgb_rotated, size.height, size.width,
					Bitmap.Config.ARGB_8888);

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			bmp.compress(Bitmap.CompressFormat.JPEG, jpegCompression, baos);
			app.setLastImage(baos);
		}
	}

	/**
	 * Calculates the best preview image size according to available preview
	 * sizes
	 * 
	 * @param previewSizes
	 *            list of available preview sizes
	 * @return the best preview image size
	 */
	private Size getBestPreviewSize(List<Size> previewSizes) {
		boolean isPortrait = false;
		Size currbest = previewSizes.get(0);
		if (previewSizes.get(0).height > previewSizes.get(0).width)
			isPortrait = true;

		for (Iterator<Size> iterator = previewSizes.iterator(); iterator.hasNext();) {

			Size currSize = iterator.next();
			if (isPortrait) {
				if (currbest.width > currSize.width & currSize.width >= BESTIMAGEWIDTH) {
					currbest = currSize;
				}
			} else {
				if (currbest.height > currSize.height & currSize.height >= BESTIMAGEHEIGHT) {
					currbest = currSize;
				}
			}
		}
		if (BuildConfig.DEBUG)
			Log.d(TAG, "Currentbest: W:" + currbest.width + " H:" + currbest.height);
		return currbest;
	}

	/**
	 * Decodes YUV420SP to RBG and rotates image 90 degrees
	 * 
	 * @param rgb
	 *            the resulting image
	 * @param yuv420sp
	 *            the original image
	 * @param width
	 *            width of image
	 * @param height
	 *            height of image
	 */
	static public void decode_rotateYUV420SP(int[] rgb, byte[] yuv420sp, int width, int height) {
		final int frameSize = width * height;

		for (int j = 0, yp = 0; j < height; j++) {
			int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
			for (int i = 0; i < width; i++, yp++) {
				int y = (0xff & ((int) yuv420sp[yp])) - 16;
				if (y < 0)
					y = 0;
				if ((i & 1) == 0) {
					v = (0xff & yuv420sp[uvp++]) - 128;
					u = (0xff & yuv420sp[uvp++]) - 128;
				}

				int y1192 = 1192 * y;
				int r = (y1192 + 1634 * v);
				int g = (y1192 - 833 * v - 400 * u);
				int b = (y1192 + 2066 * u);

				if (r < 0)
					r = 0;
				else if (r > 262143)
					r = 262143;
				if (g < 0)
					g = 0;
				else if (g > 262143)
					g = 262143;
				if (b < 0)
					b = 0;
				else if (b > 262143)
					b = 262143;

				// rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) &
				// 0xff00) | ((b >> 10) & 0xff);

				// rgba, divide 2^10 ( >> 10)
				// rgba[yp] = ((r << 14) & 0xff000000) | ((g << 6) & 0xff0000) |
				// ((b >> 2) | 0xff00);

				rgb[i * height + height - j - 1] = 0xff000000 | ((r << 6) & 0xff0000)
						| ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
			}
		}
	}

}
