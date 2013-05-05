package net.jpralves.nxt;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.TextView;

/**
 * SplashScreen activity.
 * 
 * <P>
 * Presents an initial splash screen with timed display
 * 
 * @author Joao Alves
 * @version 1.0
 */

public class SplashScreen extends Activity {

	private static final String TAG = SplashScreen.class.getSimpleName();

	private long ms = 0;
	private long splashTime = 10000;
	private boolean splashActive = true;
	private boolean paused = false;
	private Thread myThread;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_splash);
	}

	@Override
	protected void onResume() {
		super.onResume();

		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
		// Obtain the sharedPreference, default to true if not available
		boolean isSplashEnabled = sp.getBoolean("isSplashEnabled", true);

		if (isSplashEnabled) {
			if (BuildConfig.DEBUG)
				Log.d(TAG, "Started SplashScreen");
			String version = "";
			ms = 0;
			splashActive = true;
			paused = false;
			PackageManager manager = this.getPackageManager();
			try {
				PackageInfo info = manager.getPackageInfo(this.getPackageName(), 0);
				version = info.versionName;
			} catch (NameNotFoundException e) {
				if (BuildConfig.DEBUG)
					Log.e(TAG, e.getMessage(), e);
			}

			if (BuildConfig.DEBUG)
				Log.d(TAG, "packageversion=" + version);
			TextView tv = (TextView) findViewById(R.id.packageversion);
			tv.setText(version);

			tv = (TextView) findViewById(R.id.ipaddress);
			tv.setText(Networking.getLocalIpAddressString());

			myThread = new Thread() {
				TextView tv = (TextView) findViewById(R.id.counterprogress);

				public void run() {
					try {

						while (splashActive && ms < splashTime) {
							if (!paused) {
								ms = ms + 100;
								runOnUiThread(new Runnable() {
									public void run() {
										tv.setText("" + ms / 100);
									}
								});
							}
							sleep(100);
						}
					} catch (Exception e) {
					} finally {
						Intent mainIntent = new Intent(SplashScreen.this,
								NXTControllerActivity.class);
						startActivity(mainIntent);
					}
				}
			};
			myThread.start();
		} else {
			finish();
			Intent mainIntent = new Intent(SplashScreen.this, NXTControllerActivity.class);
			startActivity(mainIntent);
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			splashActive = false;
		}
		return true;
	}
}
