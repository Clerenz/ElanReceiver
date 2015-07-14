package de.clemensloos.elan.receiver;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import de.clemensloos.elan.receiver.util.SystemUiHider;
import de.clemensloos.elan.receiver.util.Utils;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 * 
 * @see SystemUiHider
 */
public class ElanReceiverActivity extends Activity {

	private View contentView;

	private Intent intent;
	private int port;
	private JmDNS mdnsServer;
	private MulticastLock lock;

	private SharedPreferences sharedPreferences;

	private ProgressDialog progressDialog;
	
	private FrameLayout mainLayout;
	private Drawable elan_logo;

	/**
	 * Whether or not the system UI should be auto-hidden after
	 * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
	 */
	private static final boolean AUTO_HIDE = true;

	/**
	 * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
	 * user interaction before hiding the system UI.
	 */
	private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

	/**
	 * If set, will toggle the system UI visibility upon interaction. Otherwise,
	 * will show the system UI visibility upon interaction.
	 */
	private static final boolean TOGGLE_ON_CLICK = true;

	/**
	 * The flags to pass to {@link SystemUiHider#getInstance}.
	 */
	private static final int HIDER_FLAGS = SystemUiHider.FLAG_HIDE_NAVIGATION;

	/**
	 * The instance of the {@link SystemUiHider} for this activity.
	 */
	private SystemUiHider mSystemUiHider;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Parameters necessary for activity called from service when screen is
		// locked.
		Window window = getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
		window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
		window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

		// Load shared preferences
		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		port = Integer.parseInt(sharedPreferences.getString(getResources().getString(R.string.pref_port_key),
				getResources().getString(R.string.pref_port_default)));

		// load the layout xml
		setContentView(R.layout.activity_fullscreen);
		mainLayout = (FrameLayout)findViewById(R.id.main_layout);
		mainLayout.setBackgroundColor(Color.BLACK);
		elan_logo = getResources().getDrawable(R.drawable.elan_logo);
		elan_logo.setBounds(0, 0, elan_logo.getIntrinsicWidth(), elan_logo.getIntrinsicHeight());

		// Following things are for fullscreen view ============================
		final View controlsView = findViewById(R.id.fullscreen_content_controls);
		contentView = findViewById(R.id.fullscreen_content);

		// Set up an instance of SystemUiHider to control the system UI for
		// this activity.
		mSystemUiHider = SystemUiHider.getInstance(this, contentView, HIDER_FLAGS);
		mSystemUiHider.setup();
		mSystemUiHider.setOnVisibilityChangeListener(new SystemUiHider.OnVisibilityChangeListener() {
			// Cached values.
			int mControlsHeight;
			int mShortAnimTime;

			@Override
			@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
			public void onVisibilityChange(boolean visible) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
					// If the ViewPropertyAnimator API is available
					// (Honeycomb MR2 and later), use it to animate the
					// in-layout UI controls at the bottom of the
					// screen.
					if (mControlsHeight == 0) {
						mControlsHeight = controlsView.getHeight();
					}
					if (mShortAnimTime == 0) {
						mShortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);
					}
					controlsView.animate().translationY(visible ? 0 : mControlsHeight).setDuration(mShortAnimTime);
				} else {
					// If the ViewPropertyAnimator APIs aren't
					// available, simply show or hide the in-layout UI
					// controls.
					controlsView.setVisibility(visible ? View.VISIBLE : View.GONE);
				}

				if (visible && AUTO_HIDE) {
					// Schedule a hide().
					delayedHide(AUTO_HIDE_DELAY_MILLIS);
				}
			}
		});

		// Set up the user interaction to manually show or hide the system UI.
		contentView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (TOGGLE_ON_CLICK) {
					mSystemUiHider.toggle();
				} else {
					mSystemUiHider.show();
				}
			}
		});
		// End of fullscreen view things =======================================

		// Start the service, that' hosting the server
		intent = new Intent(this, ElanServerService.class);
		intent.putExtra(getResources().getString(R.string.port), port);
		startService(intent);

	}

	@Override
	public void onResume() {
		super.onResume();

		// Is there a network at all?
		if (!Utils.isNetworkAvailable(this)) {
			showMessage(R.string.message_no_network);
			finish();
		}

		// Has the port changed? (returning from settings)
		portChanged();

		// Refresh text size (returning from settings)
		int textsize = Integer.parseInt(sharedPreferences.getString(getResources()
				.getString(R.string.pref_textsize_key), getResources().getString(R.string.pref_textsize_default)));
		((TextView) contentView).setTextSize(TypedValue.COMPLEX_UNIT_DIP, textsize);
		Typeface font = Typeface.createFromAsset(getAssets(), getResources().getString(R.string.font_type));
		((TextView) contentView).setTypeface(font);
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);

		// Trigger the initial hide() shortly after the activity has been
		// created, to briefly hint to the user that UI controls
		// are available.
		delayedHide(100);
	}

	@Override
	protected void onPause() {

		if (mdnsServer != null) {
			try {
				mdnsServer.unregisterAllServices();
				mdnsServer.close();
				mdnsServer = null;
			} catch (Exception e) {
				// ignore
			}
		}

		if (lock != null) {
			try {
				lock.release();
				lock = null;
			} catch (Exception e) {
				// ignore
			}
		}

		super.onPause();
	}

	@Override
	protected void onDestroy() {

		if (lock != null) {
			lock.release();
			lock = null;
		}
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		// Only one menu item directly on the bar
		MenuItem buttonSettings = menu.add("");
		buttonSettings.setIcon(R.drawable.ic_menu_preferences);
		buttonSettings.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		buttonSettings.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				Intent settingsIntent = new Intent(ElanReceiverActivity.this, SettingsActivity.class);
				ElanReceiverActivity.this.startActivity(settingsIntent);
				return true;
			}
		});

		MenuItem buttonSettings2 = menu.add(getResources().getString(R.string.streaming_service));
		buttonSettings2.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		buttonSettings2.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				// Start the JmDNS thread
				JmDNSThread myThread = new JmDNSThread();
				myThread.start();
				return true;
			}
		});

		return true;
	}

	@Override
	public void onBackPressed() {

		// If back is pressed (and ONLY then) the service is stopped
		stopService(intent);
		super.onBackPressed();
	}

	@Override
	public void onNewIntent(Intent intent) {

		super.onNewIntent(intent);

		try {
			String song = intent.getExtras().getString(getResources().getString(R.string.song));
			newValue(song);
		} catch (Exception e) {
			// ignore, called for other reason ...
		}

	}

	/**
	 * Touch listener to use for in-layout UI controls to delay hiding the
	 * system UI. This is to prevent the jarring behavior of controls going away
	 * while interacting with activity UI.
	 */
	View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
		@Override
		public boolean onTouch(View view, MotionEvent motionEvent) {
			if (AUTO_HIDE) {
				delayedHide(AUTO_HIDE_DELAY_MILLIS);
			}
			return false;
		}
	};

	Handler mHideHandler = new Handler();
	Runnable mHideRunnable = new Runnable() {
		@Override
		public void run() {
			mSystemUiHider.hide();
		}
	};

	/**
	 * Check if the port in the sharedPreferences is different from the one
	 * actually set --> refresh!
	 */
	private void portChanged() {

		int newPort = Integer.parseInt(sharedPreferences.getString(getResources().getString(R.string.pref_port_key),
				getResources().getString(R.string.pref_port_default)));

		if (newPort != port) {
			port = newPort;

			// stop the service and restart with different port!
			stopService(intent);
			intent = new Intent(this, ElanServerService.class);
			intent.putExtra(getResources().getString(R.string.port), port);
			startService(intent);
		}

	}

	/**
	 * Refreshes the displayed value, ie the next song. Uses the ui thread
	 * 
	 * @param value
	 */
	protected void newValue(final String value) {

		runOnUiThread(new Runnable() {
			public void run() {

				String encVal = value;
				if (encVal.startsWith(getResources().getString(R.string.spec_ident))) {
					encVal = encVal.replace(getResources().getString(R.string.spec_ident), "");
					int i = Integer.parseInt(encVal, 16);
					encVal = Character.toString((char) i);
				}
				if( encVal.equals("El")) {
					((TextView) contentView).setText("Elan");
					SpannableString ss = new SpannableString("abc");
					ImageSpan is = new ImageSpan(elan_logo, ImageSpan.ALIGN_BASELINE);
					ss.setSpan(is, 0, 3, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
					mainLayout.setBackgroundColor(Color.WHITE);
					((TextView) contentView).setText(ss);
				}
				else {
					mainLayout.setBackgroundColor(Color.BLACK);
					((TextView) contentView).setText(encVal);
				}
			}
		});
	}

	/**
	 * Schedules a call to hide() in [delay] milliseconds, canceling any
	 * previously scheduled calls.
	 */
	private void delayedHide(int delayMillis) {
		mHideHandler.removeCallbacks(mHideRunnable);
		mHideHandler.postDelayed(mHideRunnable, delayMillis);
	}

	/**
	 * Showing a message to the user within the ui thread
	 * 
	 * @param id
	 *            string resource
	 */
	protected void showMessage(int id) {
		showMessage(getResources().getString(id));
	}

	/**
	 * Showing a message to the user within the ui thread
	 * 
	 * @param message
	 */
	protected void showMessage(final String message) {

		runOnUiThread(new Runnable() {
			public void run() {
				Utils.showDialog(message, ElanReceiverActivity.this);
			}
		});
	}

	/**
	 * 
	 * @author Clemens.Loos
	 * 
	 */
	public class JmDNSThread implements Runnable {

		Thread thread = null;

		public synchronized void start() {

			if (thread == null) {
				thread = new Thread(this);
				thread.start();
			}

			// Create progress dialog
			progressDialog = new ProgressDialog(ElanReceiverActivity.this);
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.setTitle(getResources().getString(R.string.label_process_title));
			progressDialog.setMessage(getResources().getString(R.string.label_process_desc));
			progressDialog.setCancelable(true);
			progressDialog.setIndeterminate(false);
			progressDialog.setMax(60);
			progressDialog.setProgress(0);
			progressDialog.setProgressNumberFormat(progressDialog.getMax() + " "
					+ getResources().getString(R.string.label_seconds));
			progressDialog.setOnCancelListener(new OnCancelListener() {
				@Override
				public void onCancel(DialogInterface dialog) {
					JmDNSThread.this.stop();
				}
			});
			progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getResources().getString(R.string.label_cancel), new OnClickListener() {
				@Override
				public void onClick(DialogInterface arg0, int arg1) {
					progressDialog.cancel();
				}
			});
			progressDialog.show();

			Timer timer = new Timer();
			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					progressDialog.incrementProgressBy(1);
					if (progressDialog.getProgress() == progressDialog.getMax()) {
						this.cancel();
						JmDNSThread.this.stop();
					}
				}
			}, 1000, 1000);
		}

		public void run() {

			android.net.wifi.WifiManager wifi = (android.net.wifi.WifiManager) getSystemService(android.content.Context.WIFI_SERVICE);
			lock = wifi.createMulticastLock("StreamServiceLock");
			lock.setReferenceCounted(true);
			lock.acquire();

			// start service providing
			try {
				mdnsServer = JmDNS.create("localhost");
				ServiceInfo serviceInfo = ServiceInfo.create(getResources().getString(R.string.mdns_service_type),
						getResources().getString(R.string.mdns_service_name), port, getResources().getString(R.string.streaming_description));
				mdnsServer.registerService(serviceInfo);

			} catch (IOException e) {
				showMessage(getResources().getString(R.string.message_mdns_failed));
			}

		}

		public synchronized void stop() {

			try {
				mdnsServer.unregisterAllServices();
				mdnsServer.close();
				mdnsServer = null;
			} catch (Exception e) {
				// ignore
			}

			if (lock != null) {
				lock.release();
				lock = null;
			}

			thread.interrupt();
			progressDialog.dismiss();
		}

	}

}
