package net.jpralves.nxt;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.TextView;

/**
 * About activity
 * <P>
 * Generates information about the program. Simple window with rolling text
 * 
 * @author Joao Alves
 * @version 1.0
 */
public class AboutActivity extends Activity {

	private static final String TAG = AboutActivity.class.getSimpleName();

	private int height = 0;
	private boolean stopMoving = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_about);
	}

	@SuppressLint("NewApi")
	@Override
	protected void onStart() {
		super.onStart();
		if (BuildConfig.DEBUG)
			Log.d(TAG, "onStart");
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
		TextView tv = (TextView) findViewById(R.id.aboutBodyView);
		tv.setMovementMethod(LinkMovementMethod.getInstance());
		tv.setText(Html.fromHtml(getString(R.string.aboutDetails)));

		Thread myThread = new Thread() {
			TextView tv = (TextView) findViewById(R.id.aboutBodyView);

			public void run() {
				try {
					while (height == 0) {
						sleep(5000);
					}
					int ms = 0;
					while (tv.getScrollY() < height && !stopMoving) {
						ms = ms + 100;
						runOnUiThread(new Runnable() {
							public void run() {
								tv.scrollBy(0, 5);
							}
						});
						sleep(100);
					}
				} catch (Exception e) {
				}
			}
		};
		myThread.start();
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

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		// the height will be set at this point
		TextView tv = (TextView) findViewById(R.id.aboutBodyView);
		height = tv.getLayout().getHeight() - tv.getMeasuredHeight();
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			stopMoving = true;
		}
		return true;
	}
}