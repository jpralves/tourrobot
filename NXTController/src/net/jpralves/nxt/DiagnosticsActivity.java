package net.jpralves.nxt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Size;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.CamcorderProfile;
import android.media.MediaRecorder.AudioEncoder;
import android.media.MediaRecorder.OutputFormat;
import android.media.MediaRecorder.VideoEncoder;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.telephony.NeighboringCellInfo;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.text.format.DateUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * Diagnostics Activity
 * <P>
 * Provides information regarding several aspects of the hardware and software.
 * 
 * @author Joao Alves
 * @version 1.0
 */
public class DiagnosticsActivity extends Activity {

	private static final String TAG = DiagnosticsActivity.class.getSimpleName();

	/**
	 * the beginning of an html file
	 */
	private static final String htmlBegin = "<html><body>";
	/**
	 * the end of an html file
	 */
	private static final String htmlEnd = "</body></html>";
	/**
	 * html break
	 */
	private static final String htmlBr = "<br>\n";
	/**
	 * begin of html file to be written
	 */
	private static final String htmlFileBegin = "<!DOCTYPE HTML SYSTEM>\n<html>\n<head>\n"
			+ "<meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\">\n"
			+ "<title>Diag Report</title>\n"
			+ "<script type=\"text/javascript\">\n$TOC_SCRIPT$\n</script>\n" + "</head>\n"
			+ "<body onload=\"generateTOC(document.getElementById('toc'));\">\n"
			+ "Diag Report\n<div id=\"toc\"></div>";

	private String[] names;
	private Integer[] iconsResources;
	private String[] titles;

	private NXTControllerApp app;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_diagnostics);

		names = new String[100];
		names = getResources().getStringArray(R.array.diags_array);
		titles = new String[100];
		titles = getResources().getStringArray(R.array.diags_title_array);
		TypedArray priv_icons = getResources().obtainTypedArray(R.array.diags_array_icon);
		int len = priv_icons.length();
		iconsResources = new Integer[len];
		for (int i = 0; i < len; i++)
			iconsResources[i] = priv_icons.getResourceId(i, 0);
		priv_icons.recycle();

		app = (NXTControllerApp) getApplication();
	}

	@SuppressLint("NewApi")
	@Override
	protected void onStart() {
		super.onStart();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			ActionBar actionBar = getActionBar();
			if (actionBar != null)
				actionBar.setDisplayHomeAsUpEnabled(true);
		}
	}

	protected void onResume() {
		super.onResume();
		if (BuildConfig.DEBUG)
			Log.d(TAG, "onResume");
		Spinner spinner = (Spinner) findViewById(R.id.diags_spinner);
		spinner.setAdapter(new MyCustomAdapter(DiagnosticsActivity.this,
				R.layout.diags_spinner_row, names, iconsResources));

		OnItemSelectedListener myselectedlistener = new OnItemSelectedListener() {

			public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
				if (pos < 12)
					showDiags(pos);
				else
					writeDiagFile();
			}

			@SuppressWarnings("rawtypes")
			public void onNothingSelected(AdapterView parent) {
				// Do nothing.
			}
		};
		spinner.setOnItemSelectedListener(myselectedlistener);
		setWebViewData(htmlBegin + htmlEnd);
	}

	public void onPause() {
		super.onPause();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem menuItem) {
		switch (menuItem.getItemId()) {
		case android.R.id.home:
			finish();
			return true;
		}
		return (super.onOptionsItemSelected(menuItem));
	}

	/**
	 * Returns information about sensors in android
	 * 
	 * @param level
	 *            html header level
	 * @return the information in html format
	 */
	@SuppressWarnings("deprecation")
	protected String getSensorsInformation(int level) {
		String res = "";

		SensorManager sensors = (SensorManager) getApplicationContext().getSystemService(
				Context.SENSOR_SERVICE);
		for (Sensor sensor : sensors.getSensorList(Sensor.TYPE_ALL)) {

			String generichtml = toHtml("Name", sensor.getName())
					+ toHtml("Vendor", sensor.getVendor())
					+ toHtml("Version", sensor.getVersion() + "")
					+ toHtml("Resolution", sensor.getResolution())
					+ toHtml("Power", sensor.getPower() + "mA")
					+ toHtml("Max. Range", sensor.getMaximumRange());

			switch (sensor.getType()) {
			case Sensor.TYPE_ACCELEROMETER:
				res += htmlHeader(level, "Accelerometer") + generichtml
						+ toHtml("X", app.getTableValue("android.sensor.accel.x"))
						+ toHtml("Y", app.getTableValue("android.sensor.accel.y"))
						+ toHtml("Z", app.getTableValue("android.sensor.accel.z"));
				break;
			case Sensor.TYPE_GRAVITY:
				res += htmlHeader(level, "Gravity") + generichtml
						+ toHtml("X", app.getTableValue("android.sensor.grav.x"))
						+ toHtml("Y", app.getTableValue("android.sensor.grav.y"))
						+ toHtml("Z", app.getTableValue("android.sensor.grav.z"));
				break;
			case Sensor.TYPE_GYROSCOPE:
				res += htmlHeader(level, "Gyroscope") + generichtml
						+ toHtml("X", app.getTableValue("android.sensor.gyro.x"))
						+ toHtml("Y", app.getTableValue("android.sensor.gyro.y"))
						+ toHtml("Z", app.getTableValue("android.sensor.gyro.z"));
				break;
			case Sensor.TYPE_MAGNETIC_FIELD:
				res += htmlHeader(level, "Magnetic Field") + generichtml
						+ toHtml("X", app.getTableValue("android.sensor.magnet.x"))
						+ toHtml("Y", app.getTableValue("android.sensor.magnet.y"))
						+ toHtml("Z", app.getTableValue("android.sensor.magnet.z"));
				break;
			case Sensor.TYPE_ORIENTATION:
				res += htmlHeader(level, "Orientation") + generichtml
						+ toHtml("Azimute", app.getTableValue("android.sensor.orient.azim"))
						+ toHtml("Pitch", app.getTableValue("android.sensor.orient.pitch"))
						+ toHtml("Roll", app.getTableValue("android.sensor.orient.roll"));
				break;
			case Sensor.TYPE_TEMPERATURE:
				res += htmlHeader(level, "Temperature") + generichtml
						+ toHtml("Temp", app.getTableValue("android.sensor.temp"));
				break;
			case Sensor.TYPE_LIGHT:
				res += htmlHeader(level, "Light") + generichtml
						+ toHtml("Intensity", app.getTableValue("android.sensor.light"));
				break;
			case Sensor.TYPE_LINEAR_ACCELERATION:
				res += htmlHeader(level, "Linear Acceleration") + generichtml
						+ toHtml("X", app.getTableValue("android.sensor.laccel.x"))
						+ toHtml("Y", app.getTableValue("android.sensor.laccel.y"))
						+ toHtml("Z", app.getTableValue("android.sensor.laccel.z"));
				break;
			case Sensor.TYPE_ROTATION_VECTOR:
				res += htmlHeader(level, "Rotation Vector") + generichtml
						+ toHtml("X", app.getTableValue("android.sensor.rotation.x"))
						+ toHtml("Y", app.getTableValue("android.sensor.rotation.y"))
						+ toHtml("Z", app.getTableValue("android.sensor.rotation.z"));
				break;
			case Sensor.TYPE_PROXIMITY:
				res += htmlHeader(level, "Proximity") + generichtml
						+ toHtml("Proximity", app.getTableValue("android.sensor.proxymity"));
				break;
			}
		}
		return res;
	}

	/**
	 * Returns information about bluetooth stack
	 * 
	 * @param level
	 *            html header level
	 * @return the information in html format
	 */
	protected String getBluetoothInformation(int level) {
		String res = "";
		BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();

		if (btAdapter == null) {
			res += "Bluetooth is Off" + htmlBr;
		} else {
			res += htmlHeader(level, "Device Info");
			String scanMode;

			switch (btAdapter.getScanMode()) {
			case BluetoothAdapter.SCAN_MODE_NONE:
				scanMode = "None";
				break;
			case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
				scanMode = "Connectable";
				break;
			case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
				scanMode = "Connectable & Discoverable";
				break;
			default:
				scanMode = "Unknown";
			}

			String stateMode;
			switch (btAdapter.getState()) {
			case BluetoothAdapter.STATE_OFF:
				stateMode = "Off";
				break;
			case BluetoothAdapter.STATE_ON:
				stateMode = "On";
				break;
			case BluetoothAdapter.STATE_TURNING_OFF:
				stateMode = "Turning Off";
				break;
			case BluetoothAdapter.STATE_TURNING_ON:
				stateMode = "Turning On";
				break;
			default:
				stateMode = "Unknown";
			}

			res += toHtml("Name", btAdapter.getName()) + toHtml("Address", btAdapter.getAddress())
					+ toHtml("Scan Mode", scanMode) + toHtml("State", stateMode);

			res += htmlHeader(level, "Paired Devices");
			Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();

			if (pairedDevices.size() > 0) {
				res += beginTable()
						+ tableLine(tableCell("Name") + tableCell("Address") + tableCell("Class"));

				for (BluetoothDevice device : pairedDevices) {
					String btName = device.getName();
					String btAddress = device.getAddress();
					String btClass = device.getBluetoothClass().toString();
					res += tableLine(tableCell(btName) + tableCell(btAddress) + tableCell(btClass));

				}
				res += endTable();

			} else {
				res += "No Devices" + htmlBr;
			}

			if (BuildConfig.DEBUG)
				Log.d(TAG, "Bluetooth Address: " + btAdapter.getAddress());
		}
		return res;
	}

	/**
	 * Returns information about battery
	 * 
	 * @param level
	 *            html header level
	 * @return the information in html format
	 */
	protected String getBatteryInformation(int level) {

		Intent intent = this.getApplicationContext().registerReceiver(null,
				new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		int scale = -1;
		int batLevel = -1;
		int voltage = -1;
		int temp = -1;
		int plugged = -1;
		String res = "";
		String pluggedString = "";

		batLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
		scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
		temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
		voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
		plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);

		switch (plugged) {
		case 0:
			pluggedString = "unplugged";
			break;
		case BatteryManager.BATTERY_PLUGGED_AC:
			pluggedString = "Plugged into AC";
			break;
		case BatteryManager.BATTERY_PLUGGED_USB:
			pluggedString = "Plugged into USB";
			break;
		default:
			pluggedString = "unknown (" + plugged + ")";
		}

		float levelCalc = (float) (batLevel * 100 / scale);
		if (BuildConfig.DEBUG)
			Log.d(TAG, "BatteryManager - level is " + batLevel + "/" + scale + ", temp is " + temp
					+ ", voltage is " + voltage);
		res += toHtml("Level", levelCalc + "%25")
				+ toHtml("Temp", temp / 10.0 + " C")
				+ toHtml("voltage", voltage / 1000.0 + " V")
				+ toHtml("plugged", pluggedString)
				+ toHtml("Technology", intent.getExtras()
						.getString(BatteryManager.EXTRA_TECHNOLOGY))
				+ toHtml("Health", "" + intent.getIntExtra(BatteryManager.EXTRA_HEALTH, 0));

		return res;
	}

	/**
	 * Returns information about network - wifi and mobile
	 * 
	 * @param level
	 *            html header level
	 * @return the information in html format
	 */
	protected String getNetworkInformation(int level) {
		String res = "";

		// NXTControllerApp app = (NXTControllerApp) getApplication();

		ConnectivityManager cm = (ConnectivityManager) this
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		// To fetch the state of the Wi-Fi network in the device

		NetworkInfo wifiCMInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		Boolean isWifi = false;
		if (wifiCMInfo != null)
			isWifi = wifiCMInfo.isConnectedOrConnecting();

		NetworkInfo mobileCMInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
		Boolean isMobile = mobileCMInfo != null;
		// mobileCMInfo.isConnectedOrConnecting();

		res += htmlHeader(level, "Wifi");

		NetworkInfo[] info = cm.getAllNetworkInfo();

		if (isWifi) {

			WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
			WifiInfo wifiInfo = wifiManager.getConnectionInfo();
			res += toHtml("BSSID", wifiInfo.getBSSID())
					+ toHtml("LinkSpeed", wifiInfo.getLinkSpeed() + " Mbps")
					+ toHtml("IP Address", formatIPv4Address(wifiInfo.getIpAddress()))
					+ toHtml("MAC.Address", wifiInfo.getMacAddress())
					+ toHtml("SSID", wifiInfo.getSSID())
					+ toHtml("Signal", wifiInfo.getRssi() + " dBm");
			DhcpInfo d = wifiManager.getDhcpInfo();
			res += toHtml("DNS1", formatIPv4Address(d.dns1))
					+ toHtml("DNS2", formatIPv4Address(d.dns2))
					+ toHtml("GW", formatIPv4Address(d.gateway))
					+ toHtml("Mask", formatIPv4Address(d.netmask))
					+ toHtml("Lease", d.leaseDuration + " min")
					+ toHtml("Server", formatIPv4Address(d.serverAddress));
		} else {
			res += "Wifi is Off" + htmlBr;
		}

		res += htmlHeader(level, "Mobile");
		if (isMobile) {

			TelephonyManager telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

			GsmCellLocation cellLoc = (GsmCellLocation) telephonyManager.getCellLocation();
			List<NeighboringCellInfo> neighboorCellInfo = telephonyManager.getNeighboringCellInfo();

			int rss = 0;
			if (!neighboorCellInfo.isEmpty()) {
				res += htmlHeader(level + 1, "Neighboor Cell Information");
				res += beginTable()
						+ tableLine(tableCell("CellId") + tableCell("NetType")
								+ tableCell("Signal Power (dBm)") + tableCell("Lac")
								+ tableCell("Psc") + tableCell("Rssi"));

				for (NeighboringCellInfo nci : neighboorCellInfo) {
					rss = -113 + 2 * nci.getRssi();
					res += tableLine(tableCell("" + nci.getCid())
							+ tableCell("" + nci.getNetworkType()) + tableCell("" + rss)
							+ tableCell("" + nci.getLac()) + tableCell("" + nci.getPsc())
							+ tableCell("" + nci.getRssi()));
				}
				res += endTable();

			}

			res += toHtml("SIM Operator", telephonyManager.getSimOperatorName());
			if (telephonyManager.getSimSerialNumber() != null) {
				res += toHtml("SIM Serial Number", telephonyManager.getSimSerialNumber());
			}
			res += toHtml("Network Operator", telephonyManager.getNetworkOperator());
			res += toHtml("Network Operator Name", telephonyManager.getNetworkOperatorName());
			res += toHtml("Signal Power", app.getTableValue("android.phone.signal.dbm") + " dBm "
					+ app.getTableValue("android.phone.signal.asu") + " asu");
			if (telephonyManager.getLine1Number() != null) {
				res += toHtml("MSISDN", telephonyManager.getLine1Number());
			}
			res += toHtml("IMEI Software Version", telephonyManager.getDeviceSoftwareVersion());
			res += toHtml("IMEI", telephonyManager.getDeviceId());
			if (telephonyManager.getSubscriberId() != null) {
				res += toHtml("IMSI", telephonyManager.getSubscriberId());
			}
			res += toHtml("Data State",
					(telephonyManager.getDataState() == TelephonyManager.DATA_CONNECTED ? "On"
							: "Off"));

			if (mobileCMInfo.isConnectedOrConnecting()) {
				for (NetworkInfo inf : info) {
					if (inf.getState() == State.CONNECTED) {
						res += toHtml("NetType", Utils.getMobileType(inf))
								+ toHtml("Extra", inf.getExtraInfo())
								+ toHtml("Roaming", inf.isRoaming() ? "Yes" : "No");
						String ipAddress = "";
						try {
							for (Enumeration<NetworkInterface> en = NetworkInterface
									.getNetworkInterfaces(); en.hasMoreElements();) {
								NetworkInterface intf = en.nextElement();
								for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr
										.hasMoreElements();) {
									InetAddress inetAddress = enumIpAddr.nextElement();
									if (!inetAddress.isLoopbackAddress()) {
										ipAddress = inetAddress.getHostAddress();
									}
								}
							}
						} catch (SocketException ex) {
						}
						if (!ipAddress.isEmpty())
							res += toHtml("IP Address", ipAddress);
					}
				}
			}
			res += htmlHeader(level, "Cell Location");

			String netOperator = telephonyManager.getNetworkOperator();
			if (netOperator != null && netOperator.length() > 0) {
				int mcc = Integer.parseInt(netOperator.substring(0, 3));
				int mnc = Integer.parseInt(netOperator.substring(3));
				res += toHtml("MCC", "" + mcc);
				res += toHtml("MNC", "" + mnc);
			}

			if (cellLoc != null) {
				res += toHtml("CellID", "" + cellLoc.getCid());
				res += toHtml("Lac", "" + cellLoc.getLac());
				res += toHtml("Psc", "" + cellLoc.getPsc());

				int cid = cellLoc.getCid();
				int lac = cellLoc.getLac();
				try {

					String coords = getLatLong(cid, lac);
					res += toHtml("Lat,Long", coords);
					if (!coords.contentEquals("")) {
						res += " <a href=\"javascript:void(0)\" onclick=\"js2Java.callGeoIntent('"
								+ coords + "')\">Mapa</a>";
					}
				} catch (Exception e) {
					if (BuildConfig.DEBUG)
						Log.e(TAG, "Unable to get google information", e);
				}
			}
		} else {
			res += "Mobile is Off" + htmlBr;
		}
		return res;
	}

	/**
	 * Returns information about camera
	 * 
	 * @param level
	 *            html header level
	 * @return the information in html format
	 */
	protected String getCameraInformation(int level) {
		String res = "";

		if (Camera.getNumberOfCameras() == 0) {
			res = "No Cameras Found" + htmlBr;
		}
		String facingCamera = "";
		for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
			CameraInfo info = new Camera.CameraInfo();
			Camera.getCameraInfo(i, info);
			if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
				facingCamera = "Front (" + i + ")";
			} else {
				facingCamera = "Back (" + i + ")";
			}

			res += htmlHeader(level, facingCamera + " Camera");

			res += toHtml("Orientation", info.orientation);

			try {
				Camera mCamera = Camera.open(i);
				Camera.Parameters params = mCamera.getParameters();
				mCamera.release();
				res += toHtml("Flash Mode", params.getFlashMode())
						+ toHtml("Antibanding", params.getAntibanding())
						+ toHtml("ColorEffect", params.getColorEffect())
						+ toHtml("VerticalViewAngle", params.getVerticalViewAngle())
						+ toHtml("FocalLength", params.getFocalLength())
						+ toHtml("FocusMode", params.getFocusMode())
						+ toHtml("HorizontalViewAngle", params.getHorizontalViewAngle());

				List<Size> sizes = params.getSupportedPictureSizes();
				Camera.Size result = null;
				res += htmlHeader(level + 1, "Supported Picture Sizes");
				for (int j = 0; j < sizes.size(); j++) {
					result = (Size) sizes.get(j);
					res += result.width + "x" + result.height + htmlBr;
				}
			} catch (Exception e) {
				if (BuildConfig.DEBUG)
					Log.e(TAG, "failed to open Camera");
			}
		}

		int[] constCamcorderProfile = { CamcorderProfile.QUALITY_HIGH, CamcorderProfile.QUALITY_LOW };

		String[] strCamcorderProfile = { "QUALITY_HIGH", "QUALITY_LOW" };
		for (int i = 0; i < constCamcorderProfile.length; i++) {
			CamcorderProfile camcorderProfile = CamcorderProfile.get(constCamcorderProfile[i]);
			if (camcorderProfile != null) {
				res += htmlHeader(level, "Profile " + strCamcorderProfile[i])
						+ toHtml("audioBitRate", String.valueOf(camcorderProfile.audioBitRate))
						+ toHtml("audioChannels", String.valueOf(camcorderProfile.audioChannels))
						+ toHtml("audioCodec", getAudioCodec(camcorderProfile.audioCodec))
						+ toHtml("audioSampleRate",
								String.valueOf(camcorderProfile.audioSampleRate))
						+ toHtml("duration", String.valueOf(camcorderProfile.duration))
						+ toHtml("fileFormat", getFileFormat(camcorderProfile.fileFormat))
						+ toHtml("quality", String.valueOf(camcorderProfile.quality))
						+ toHtml("videoBitRate", String.valueOf(camcorderProfile.videoBitRate))
						+ toHtml("videoCodec", getVideoCodec(camcorderProfile.videoCodec))
						+ toHtml("videoFrameRate", String.valueOf(camcorderProfile.videoFrameRate))
						+ toHtml("videoFrameWidth",
								String.valueOf(camcorderProfile.videoFrameWidth))
						+ toHtml("videoFrameHeight",
								String.valueOf(camcorderProfile.videoFrameHeight));
			}
		}

		return res;
	}

	private Locale toLocale(String language) {
		Locale locale;
		if (language.equals(""))
			locale = Locale.getDefault();
		else
			locale = new Locale(language);
		return locale;
	}

	/**
	 * Returns information about the android system
	 * 
	 * @param level
	 *            html header level
	 * @return the information in html format
	 */
	protected String getSystemInformation(int level) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		String res = toHtml("PhoneModel", android.os.Build.MODEL)
				+ toHtml("Manufacturer", android.os.Build.MANUFACTURER)
				+ toHtml("Brand", android.os.Build.BRAND)
				+ toHtml("Display", android.os.Build.DISPLAY)
				+ toHtml("Hardware", android.os.Build.HARDWARE)
				+ toHtml("ID", android.os.Build.ID)
				+ toHtml("Version", android.os.Build.VERSION.RELEASE)
				+ toHtml("Device", android.os.Build.DEVICE)
				+ toHtml("Serial", android.os.Build.SERIAL)
				+ toHtml("Type", android.os.Build.TYPE)
				+ toHtml("Board", android.os.Build.BOARD)
				+ toHtml("FingerPrint", android.os.Build.FINGERPRINT)
				+ toHtml("CPU ABI", android.os.Build.CPU_ABI)
				+ toHtml("CPU ABI2", android.os.Build.CPU_ABI2)
				+ toHtml("Bootloader", android.os.Build.BOOTLOADER)
				+ toHtml("SDK Version", android.os.Build.VERSION.SDK_INT)
				+ toHtml("RealUptime",
						"" + DateUtils.formatElapsedTime(SystemClock.elapsedRealtime() / 1000))
				+ toHtml("Uptime",
						"" + DateUtils.formatElapsedTime(SystemClock.uptimeMillis() / 1000))
				+ toHtml("Number of Cores", Utils.getNumCores())
				+ toHtml("CPU Speed", Utils.getCPUSpeed() / 1000 + " Mhz")
				+ toHtml("CPU Usage", String.format("%.2f", Utils.readUsage()) + "%25")
				+ toHtml("display Metrics", getDisplayMetrics())
				+ toHtml("System Language", app.getTableString("android.systemlocale"))
				+ toHtml("Application Language", toLocale(prefs.getString("locale", ""))
						.getLanguage());

		return res;
	}

	/**
	 * Returns information about android storage system
	 * 
	 * @param level
	 *            html header level
	 * @return the information in html format
	 */
	protected String getStorageInformation(int level) {
		StatFs statFsRD = new StatFs(Environment.getRootDirectory().getAbsolutePath());
		int totalMemory = (statFsRD.getBlockCount() * statFsRD.getBlockSize());
		int freeMemory = (statFsRD.getAvailableBlocks() * statFsRD.getBlockSize());

		long availableSpace = -1L;
		long totalSpace = -1L;
		try {
			StatFs statFsDD = new StatFs(Environment.getDataDirectory().getPath());
			statFsDD.restat(Environment.getDataDirectory().getPath());
			availableSpace = (long) statFsDD.getAvailableBlocks() * (long) statFsDD.getBlockSize();
			totalSpace = (long) statFsDD.getBlockCount() * (long) statFsDD.getBlockSize();
		} catch (Exception e) {
			if (BuildConfig.DEBUG)
				Log.e(TAG, "Error getting Storage", e);
		}
		String res = toHtml("Total Memory", Utils.formatMultibyte(totalMemory, true))
				+ toHtml("Free Memory", Utils.formatMultibyte(freeMemory, true))
				+ toHtml("Total Space", Utils.formatMultibyte(totalSpace, true))
				+ toHtml("Free Space", Utils.formatMultibyte(availableSpace, true));
		return res;
	}

	/**
	 * Returns information about the installed packages
	 * 
	 * @param level
	 *            html header level
	 * @return the information in html format
	 */
	protected String getPackagesInformation(int level) {
		String res = "";

		ArrayList<PInfo> apps = getInstalledApps(true); // false = no system
														// packages

		final int max = apps.size();
		if (max > 0) {
			res += beginTable() + tableLine(tableCell("appName") + tableCell("Version"));

			for (int i = 0; i < max; i++) {

				PInfo appi = apps.get(i);
				res += tableLine(tableCell((appi.issystem ? "*" : "") + appi.appname)
						+ tableCell(appi.versionName));
			}
			res += endTable();
		} else {
			res += "No Packages" + htmlBr;
		}
		return res;
	}

	/**
	 * Returns information about volatile information - stored in App object
	 * 
	 * @param level
	 *            html header level
	 * @return the information in html format
	 */
	protected String getVolatileDataInformation(int level) {

		NXTControllerApp app = (NXTControllerApp) getApplication();
		Iterator<String> i;

		String res = htmlHeader(level, "Strings");
		i = app.getTableStringKeys();
		while (i.hasNext()) {
			String str = i.next();
			res += toHtml(str, app.getTableString(str));
		}

		res += htmlHeader(level, "Floats");
		i = app.getTableFloatKeys();
		while (i.hasNext()) {
			String str = i.next();
			res += toHtml(str, app.getTableFloat(str));
		}

		res += htmlHeader(level, "Integers");
		i = app.getTableIntKeys();
		while (i.hasNext()) {
			String str = i.next();
			res += toHtml(str, app.getTableInt(str));
		}

		res += htmlHeader(level, "Longs");
		i = app.getTableLongKeys();
		while (i.hasNext()) {
			String str = i.next();
			res += toHtml(str, app.getTableLong(str));
		}

		return res;
	}

	/**
	 * Returns information about wifi networks
	 * 
	 * @param level
	 *            html header level
	 * @return the information in html format
	 */
	protected String getWifiNetworksInformation(int level) {

		final WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
		ConnectivityManager cm = (ConnectivityManager) this
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		// To fetch the state of the Wi-Fi network in the device

		Boolean isWifi = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnectedOrConnecting();

		String res = "";

		if (isWifi) {

			List<ScanResult> wifiList;

			wifiList = wifiManager.getScanResults();

			if (wifiList.size() > 0) {
				res += beginTable()
						+ tableLine(tableCell("BSSID") + tableCell("SSID") + tableCell("Freq")
								+ tableCell("Level") + tableCell("Capabilities"));

				for (int i = 0; i < wifiList.size(); i++) {
					ScanResult sr = wifiList.get(i);
					res += tableLine(tableCell(sr.BSSID) + tableCell(sr.SSID)
							+ tableCell("" + sr.frequency) + tableCell("" + sr.level)
							+ tableCell(sr.capabilities));
				}
				res += endTable();

			} else {
				res += "No Wifi Networks" + htmlBr;
			}
		}
		return res;
	}

	/**
	 * Returns information about application preferences
	 * 
	 * @param level
	 *            html header level
	 * @return the information in html format
	 */
	protected String getPreferencesInformation(int level) {

		String res = "";

		Map<String, ?> keys = PreferenceManager.getDefaultSharedPreferences(this).getAll();

		for (Map.Entry<String, ?> entry : keys.entrySet()) {
			res += toHtml(entry.getKey(), entry.getValue().toString());
		}
		return res;
	}

	/**
	 * Returns information about application permissions
	 * 
	 * @param level
	 *            html header level
	 * @return the information in html format
	 */
	protected String getPermissionsInformation(int level) {

		String res = "";

		PackageManager manager = this.getPackageManager();
		try {
			PackageInfo info = manager.getPackageInfo(this.getPackageName(),
					PackageManager.GET_PERMISSIONS);
			String[] requestedPermissions = info.requestedPermissions;
			if (requestedPermissions != null) {
				for (int i = 0; i < requestedPermissions.length; i++) {
					res += requestedPermissions[i] + htmlBr;
				}
			}
		} catch (NameNotFoundException e) {
			if (BuildConfig.DEBUG)
				Log.e(TAG, e.getMessage(), e);
		}

		return res;
	}

	/**
	 * changes webview data
	 * 
	 * @param s
	 *            the html data
	 */
	@SuppressLint("SetJavaScriptEnabled")
	private void setWebViewData(String s) {
		WebView browser = (WebView) findViewById(R.id.webviewdiag);

		browser.getSettings().setJavaScriptEnabled(true);
		browser.getSettings().setDefaultTextEncodingName("utf-8");
		browser.addJavascriptInterface(new Js2Java(), "js2Java");
		browser.loadData(s, "text/html; charset=utf-8", "UTF-8");
	}

	/**
	 * returns html formated in bold
	 * 
	 * @param parm
	 *            the string to be bold
	 * @return the html code
	 */
	private String htmlinBold(String parm) {
		return "<B>" + parm + "</B>";
	}

	/**
	 * returns html code formated in key: value as string format
	 * 
	 * @param key
	 *            the key
	 * @param val
	 *            the value
	 * @return the html code
	 */
	private String toHtml(String key, String val) {
		return htmlinBold(key + ":") + " " + val + htmlBr;
	}

	/**
	 * returns html code formated in key: value as float format
	 * 
	 * @param key
	 *            the key
	 * @param val
	 *            the value
	 * @return the html code
	 */
	private String toHtml(String key, float val) {
		return htmlinBold(key + ":") + " " + String.valueOf(val) + htmlBr;
	}

	/**
	 * returns html code formated in key: value as int format
	 * 
	 * @param key
	 *            the key
	 * @param val
	 *            the value
	 * @return the html code
	 */
	private String toHtml(String key, int val) {
		return htmlinBold(key + ":") + " " + String.valueOf(val) + htmlBr;
	}

	/**
	 * returns html code formated in key: value as long format
	 * 
	 * @param key
	 *            the key
	 * @param val
	 *            the value
	 * @return the html code
	 */
	private String toHtml(String key, long val) {
		return htmlinBold(key + ":") + " " + String.valueOf(val) + htmlBr;
	}

	/**
	 * returns the beginning of html table
	 * 
	 * @return the html code
	 */
	private String beginTable() {
		return "<TABLE>";
	}

	/**
	 * returns the end of html table
	 * 
	 * @return the html code
	 */
	private String endTable() {
		return "</TABLE>\n";
	}

	/**
	 * return a table row in html
	 * 
	 * @param elements
	 *            the elements in the table
	 * @return hte html code
	 */
	private String tableLine(String elements) {
		return "<TR>" + elements + "</TR>\n";
	}

	/**
	 * returns a table cell in html
	 * 
	 * @param cell
	 *            the cell value
	 * @return the html code
	 */
	private String tableCell(String cell) {
		return "<TD>" + cell + "</TD>";
	}

	/**
	 * return an header with level
	 * 
	 * @param level
	 *            the header level
	 * @param text
	 *            the text in the header
	 * @return hte html code
	 */
	private String htmlHeader(int level, String text) {
		return "<H" + level + ">" + text + "</H" + level + ">\n";
	}

	/**
	 * returns ipv4 address in string format
	 * 
	 * @param ipAddress
	 *            in int format
	 * @return the representation of ipv4 address
	 */
	@SuppressLint("DefaultLocale")
	private String formatIPv4Address(int ipAddress) {
		return String.format("%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff),
				(ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
	}

	public class MyCustomAdapter extends ArrayAdapter<String> {

		public MyCustomAdapter(Context context, int textViewResourceId, String[] objects,
				Integer[] image) {
			super(context, textViewResourceId, objects);
		}

		@Override
		public View getDropDownView(int position, View convertView, ViewGroup parent) {
			return getCustomView(position, convertView, parent);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			return getCustomView(position, convertView, parent);
		}

		public View getCustomView(int position, View convertView, ViewGroup parent) {

			LayoutInflater inflater = getLayoutInflater();
			View row = inflater.inflate(R.layout.diags_spinner_row, parent, false);
			TextView label = (TextView) row.findViewById(R.id.description);
			label.setText(names[position]);

			ImageView icon = (ImageView) row.findViewById(R.id.icon);
			icon.setImageResource(iconsResources[position]);
			return row;
		}
	}

	class Js2Java {
		@JavascriptInterface
		public void callGeoIntent(String coords) {
			if (BuildConfig.DEBUG)
				Log.d(TAG, "Running from javascript");
			Intent i = new Intent(android.content.Intent.ACTION_VIEW);
			i.setData(Uri.parse("geo:" + coords));
			startActivity(i);
		}
	}

	/**
	 * returns Latitute and Longitude based on CellID and lac
	 * 
	 * @param cellID
	 *            the cellID
	 * @param lac
	 *            the lac
	 * @return a string with lat,long
	 * @throws Exception
	 */
	private String getLatLong(int cellID, int lac) throws Exception {
		String urlString = "http://www.google.com/glm/mmap";

		// ---open a connection to Google Maps API---
		URL url = new URL(urlString);
		URLConnection conn = url.openConnection();
		HttpURLConnection httpConn = (HttpURLConnection) conn;
		httpConn.setRequestMethod("POST");
		httpConn.setDoOutput(true);
		httpConn.setDoInput(true);
		httpConn.connect();

		// ---write some custom data to Google Maps API---
		OutputStream outputStream = httpConn.getOutputStream();
		WriteData(outputStream, cellID, lac);

		// ---get the response---
		InputStream inputStream = httpConn.getInputStream();
		DataInputStream dataInputStream = new DataInputStream(inputStream);

		// ---interpret the response obtained---
		dataInputStream.readShort();
		dataInputStream.readByte();
		int code = dataInputStream.readInt();
		if (code == 0) {
			double lat = (double) dataInputStream.readInt() / 1000000D;
			double lng = (double) dataInputStream.readInt() / 1000000D;
			dataInputStream.readInt();
			dataInputStream.readInt();
			dataInputStream.readUTF();

			return lat + "," + lng;
		} else {
			return "";
		}
	}

	private void WriteData(OutputStream out, int cellID, int lac) throws IOException {
		DataOutputStream dataOutputStream = new DataOutputStream(out);
		dataOutputStream.writeShort(21);
		dataOutputStream.writeLong(0);
		dataOutputStream.writeUTF("en");
		dataOutputStream.writeUTF("Android");
		dataOutputStream.writeUTF("1.3.1");
		dataOutputStream.writeUTF("Web");
		dataOutputStream.writeByte(27);
		dataOutputStream.writeInt(0);
		dataOutputStream.writeInt(0);
		dataOutputStream.writeInt(3);
		dataOutputStream.writeUTF("");

		dataOutputStream.writeInt(cellID);
		dataOutputStream.writeInt(lac);

		dataOutputStream.writeInt(0);
		dataOutputStream.writeInt(0);
		dataOutputStream.writeInt(0);
		dataOutputStream.writeInt(0);
		dataOutputStream.flush();
	}

	/**
	 * return audio codec in string format
	 * 
	 * @param audioCodec
	 *            parameter
	 * @return string with audio codec
	 */
	private String getAudioCodec(int audioCodec) {
		switch (audioCodec) {
		case AudioEncoder.AMR_NB:
			return "AMR_NB";
		case AudioEncoder.DEFAULT:
			return "DEFAULT";
		default:
			return "unknown";
		}
	}

	/**
	 * returns file format in string format
	 * 
	 * @param fileFormat
	 *            parameter
	 * @return string with file format
	 */
	@SuppressWarnings("deprecation")
	private String getFileFormat(int fileFormat) {
		switch (fileFormat) {
		case OutputFormat.MPEG_4:
			return "MPEG_4";
		case OutputFormat.RAW_AMR:
			return "RAW_AMR";
		case OutputFormat.THREE_GPP:
			return "THREE_GPP";
		case OutputFormat.DEFAULT:
			return "DEFAULT";
		default:
			return "unknown";
		}
	}

	/**
	 * returns video codec in string format
	 * 
	 * @param videoCodec
	 *            parameter
	 * @return string with video codec
	 */
	private String getVideoCodec(int videoCodec) {
		switch (videoCodec) {
		case VideoEncoder.H263:
			return "H263";
		case VideoEncoder.H264:
			return "H264";
		case VideoEncoder.MPEG_4_SP:
			return "MPEG_4_SP";
		case VideoEncoder.DEFAULT:
			return "DEFAULT";
		default:
			return "unknown";
		}
	}

	/**
	 * returns the display metrics
	 * 
	 * @return string with width x height
	 */
	@SuppressWarnings("deprecation")
	private String getDisplayMetrics() {

		int width = 0;
		int height = 0;
		WindowManager w = getWindowManager();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
			DisplayMetrics metrics = new DisplayMetrics();
			w.getDefaultDisplay().getMetrics(metrics);
			width = metrics.widthPixels;
			height = metrics.heightPixels;
		} else {
			Display d = w.getDefaultDisplay();
			width = d.getWidth();
			height = d.getHeight();
		}
		return width + "x" + height;
	}

	@SuppressWarnings("unused")
	class PInfo {
		private String appname = "";
		private String pname = "";
		private String versionName = "";
		private int versionCode = 0;
		private boolean issystem = false;
		private Drawable icon;
	}

	/**
	 * returns installed packages
	 * 
	 * @param getSysPackages
	 *            true if includes system packages
	 * @return arraylist with all packages
	 */
	private ArrayList<PInfo> getInstalledApps(boolean getSysPackages) {
		ArrayList<PInfo> res = new ArrayList<PInfo>();
		PackageManager pm = getPackageManager();
		List<PackageInfo> packs = pm.getInstalledPackages(PackageManager.GET_META_DATA);

		for (int i = 0; i < packs.size(); i++) {
			PackageInfo p = packs.get(i);
			if ((!getSysPackages) && isSystemPackage(p)) {
				continue;
			}
			PInfo newInfo = new PInfo();
			newInfo.appname = p.applicationInfo.loadLabel(pm).toString();
			newInfo.pname = p.packageName;
			newInfo.versionName = p.versionName;
			newInfo.versionCode = p.versionCode;
			newInfo.icon = p.applicationInfo.loadIcon(pm);
			newInfo.issystem = isSystemPackage(p);
			res.add(newInfo);
		}
		java.util.Collections.sort(res, new Comparator<PInfo>() {
			public int compare(PInfo a, PInfo b) {
				return a.appname.compareToIgnoreCase(b.appname);
			}
		});
		return res;
	}

	/**
	 * get the type of package
	 * 
	 * @param pkgInfo
	 *            the package
	 * @return true of parameter is a system package
	 */
	private boolean isSystemPackage(PackageInfo pkgInfo) {
		return ((pkgInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) ? true : false;
	}

	private String getDiagsTitle(int page) {
		return titles[page];
	}

	private void showDiags(int page) {

		GetDiagsInfoTask myTask = new GetDiagsInfoTask();
		myTask.execute(page);
	}

	private class GetDiagsInfoTask extends AsyncTask<Integer, Long, String> {

		private Dialog progress;
		private int page;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			progress = new Dialog(DiagnosticsActivity.this, R.style.SimpleProgress);
			progress.setContentView(R.layout.custom_progress_dialog);
			progress.show();
		}

		protected String doInBackground(Integer... tasks) {
			page = tasks[0];
			String res = getDiagsInfo(tasks[0]);
			return res;
		}

		@Override
		protected void onPostExecute(String result) {
			super.onPostExecute(result);
			progress.dismiss();
			showDiagsDetail(getDiagsTitle(page), result);
		}
	}

	private String getDiagsInfo(int page) {
		String res = "";
		switch (page) {
		case 0:
			res = getSystemInformation(2);
			break;
		case 1:
			res = getBatteryInformation(2);
			break;
		case 2:
			res = getNetworkInformation(2);
			break;
		case 3:
			res = getBluetoothInformation(2);
			break;
		case 4:
			res = getSensorsInformation(2);
			break;
		case 5:
			res = getCameraInformation(2);
			break;
		case 6:
			res = getStorageInformation(2);
			break;
		case 7:
			res = getPackagesInformation(2);
			break;
		case 8:
			res = getVolatileDataInformation(2);
			break;
		case 9:
			res = getWifiNetworksInformation(2);
			break;
		case 10:
			res = getPreferencesInformation(2);
			break;
		case 11:
			res = getPermissionsInformation(2);
			break;
		default:
			break;
		}
		return res;
	}

	private void showDiagsDetail(String title, String data) {
		String h = htmlBegin + htmlHeader(1, title);
		setWebViewData(h + data + htmlEnd);
	}

	private class WriteDiagsInfoTask extends AsyncTask<Void, Void, Void> {
		private Dialog progress;
		private String h;
		private String data;
		private File file;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			progress = new Dialog(DiagnosticsActivity.this, R.style.SimpleProgress);
			progress.setContentView(R.layout.custom_progress_dialog);
			progress.show();
		}

		@Override
		protected Void doInBackground(Void... params) {
			super.onPostExecute(null);
			h = htmlBegin + htmlHeader(1, getString(R.string.writeDiags));
			data = "";
			publishProgress();
			data += getText(R.string.generatingDiags) + htmlBr;
			publishProgress();
			if (BuildConfig.DEBUG)
				Log.d(TAG, "Creating diags file");
			if (BuildConfig.DEBUG)
				Log.d(TAG, "Filename: "
						+ Environment.getExternalStorageDirectory().getAbsolutePath()
						+ File.separator + getUniqueFilename());

			if (Environment.getExternalStorageDirectory().canWrite()) {
				file = new File(Environment.getExternalStorageDirectory().getAbsolutePath()
						+ File.separator + getUniqueFilename());
			} else {
				// set alternative:
				// TODO document better this fix - HTC related
				file = new File("/mnt/emmc" + File.separator + getUniqueFilename());
				data += getText(R.string.usingAlternate) + htmlBr;
				publishProgress();
				if (BuildConfig.DEBUG)
					Log.e(TAG, "Write Denied to "
							+ Environment.getExternalStorageDirectory().getAbsolutePath()
							+ File.separator + getUniqueFilename());
			}

			try {
				file.createNewFile();
			} catch (IOException e) {
				if (BuildConfig.DEBUG)
					Log.e(TAG, e.getMessage(), e);
			}

			if (file.exists()) {
				OutputStream fo;
				String buffer;
				try {
					fo = new FileOutputStream(file);
					data += getText(R.string.writingHeader) + htmlBr;
					publishProgress();
					String script = new String(getRawResource(R.raw.toc), "UTF-8");
					buffer = htmlFileBegin.replace("$TOC_SCRIPT$", script);
					fo.write(buffer.getBytes());
					publishProgress();
					for (int i = 0; i < titles.length - 1; i++) {
						data += getText(R.string.writing) + getDiagsTitle(i) + htmlBr;
						buffer = htmlHeader(1, getDiagsTitle(i)) + getDiagsInfo(i);
						fo.write(buffer.getBytes());
						publishProgress();
					}
					fo.write(htmlEnd.getBytes());
					fo.flush();
					publishProgress();
					fo.close();

				} catch (FileNotFoundException e1) {
					data += getText(R.string.errorWriting) + htmlBr;
					publishProgress();
				} catch (IOException e) {
					if (BuildConfig.DEBUG)
						Log.e(TAG, e.getMessage(), e);
				}
			}
			data += "Done." + htmlBr;
			publishProgress();
			return null;
		}

		@Override
		protected void onProgressUpdate(Void... values) {
			super.onProgressUpdate(values);
			setWebViewData(h + data + htmlEnd);
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			progress.dismiss();

			if (file != null) {
				Intent sendIntent = new Intent();
				sendIntent.setAction(Intent.ACTION_SEND);
				sendIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				sendIntent.putExtra(Intent.EXTRA_SUBJECT, "RE: NXTcontroller Report");
				sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + file.getPath()));
				sendIntent.setType("text/html");
				startActivity(Intent.createChooser(sendIntent, getText(R.string.sendTo)));
			}
		}
	}

	private void writeDiagFile() {
		WriteDiagsInfoTask myTask = new WriteDiagsInfoTask();
		myTask.execute();
	}

	private byte[] getRawResource(int rr) {
		InputStream in_s = getResources().openRawResource(rr);

		byte[] b = null;
		try {
			b = new byte[in_s.available()];
			in_s.read(b);
			in_s.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return b;
	}

	@SuppressLint("SimpleDateFormat")
	private String getUniqueFilename() {

		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
		String currentDateandTime = sdf.format(new Date());

		String version = "";

		PackageManager manager = this.getPackageManager();
		try {
			PackageInfo info = manager.getPackageInfo(this.getPackageName(), 0);
			version = info.versionName;
		} catch (NameNotFoundException e) {
			if (BuildConfig.DEBUG)
				Log.e(TAG, e.getMessage(), e);
		}

		String filename = "NXTController " + version + " " + android.os.Build.MANUFACTURER + "-"
				+ android.os.Build.MODEL + "-" + currentDateandTime + ".html";

		return filename.replace(" ", "_");
	}

	private static final int IO_BUFFER_SIZE = 4 * 1024;

	private static void copy(InputStream in, OutputStream out) throws IOException {
		byte[] b = new byte[IO_BUFFER_SIZE];
		int read;
		while ((read = in.read(b)) != -1) {
			out.write(b, 0, read);
		}
	}

}
