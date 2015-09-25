package de.clemensloos.elan.receiver;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
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
import android.widget.LinearLayout;
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
    Handler mHideHandler = new Handler();
    private View contentView;
    private TextView songView;
    private TextView textView;
    private Intent intent;
    private int port;
    private boolean showTitle = false;
    private SharedPreferences sharedPreferences;
    private ProgressDialog progressDialog;
    private FrameLayout mainLayout;
    private Drawable elan_logo;
    /**
     * The instance of the {@link SystemUiHider} for this activity.
	 */
	private SystemUiHider mSystemUiHider;
    Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            mSystemUiHider.hide();
        }
    };
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
        mainLayout = (FrameLayout) findViewById(R.id.main_layout);
        mainLayout.setBackgroundColor(Color.BLACK);
        elan_logo = getResources().getDrawable(R.drawable.elan_logo);
        elan_logo.setBounds(0, 0, elan_logo.getIntrinsicWidth(), elan_logo.getIntrinsicHeight());

        songView = (TextView) findViewById(R.id.song_view);
        textView = (TextView) findViewById(R.id.text_view);
        showTitle = sharedPreferences.getBoolean(
                getResources().getString(R.string.pref_enable_title_key),
                getResources().getBoolean(R.bool.pref_enable_title_default));
        if (!showTitle) {
            textView.setText("");
        }

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
        // TODO start only if not running ...
        intent = new Intent(this, ElanServerService.class);
        intent.putExtra(getResources().getString(R.string.port), port);
        startService(intent);

    }

    @Override
    public void onResume() {
        super.onResume();

        // Has the port changed? (returning from settings)
        portChanged();

        // Refresh text size (returning from settings)
        int textsize = Integer.parseInt(sharedPreferences.getString(getResources()
                .getString(R.string.pref_songsize_key), getResources().getString(R.string.pref_songsize_default)));
        songView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textsize);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) songView.getLayoutParams();
        if (sharedPreferences.getBoolean(
                getResources().getString(R.string.pref_use_neg_margin_key),
                getResources().getBoolean(R.bool.pref_use_neg_margin_default))) {
            params.setMargins(0, -50, 0, 0);
        } else {
            params.setMargins(0, 0, 0, 0);
        }
        songView.setLayoutParams(params);
        Typeface font = Typeface.createFromAsset(getAssets(), getResources().getString(R.string.font_type));
        songView.setTypeface(font);

        textsize = Integer.parseInt(sharedPreferences.getString(getResources()
                .getString(R.string.pref_textsize_key), getResources().getString(R.string.pref_textsize_default)));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textsize);
        params = (LinearLayout.LayoutParams) textView.getLayoutParams();
        if (sharedPreferences.getBoolean(
                getResources().getString(R.string.pref_use_neg_margin_key),
                getResources().getBoolean(R.bool.pref_use_neg_margin_default))) {
            params.setMargins(0, -100, 0, 0);
        } else {
            params.setMargins(0, 0, 0, 0);
        }
        textView.setLayoutParams(params);

        showTitle = sharedPreferences.getBoolean(
                getResources().getString(R.string.pref_enable_title_key),
                getResources().getBoolean(R.bool.pref_enable_title_default));

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(getString(R.string.local_ip_key), getIpAddr());
        editor.commit();
    }

    public String getIpAddr() {
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ip = wifiInfo.getIpAddress();

        return String.format(
                "%d.%d.%d.%d",
                (ip & 0xff),
                (ip >> 8 & 0xff),
                (ip >> 16 & 0xff),
                (ip >> 24 & 0xff));
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
            String title = intent.getExtras().getString(getResources().getString(R.string.title), "");
            newValue(song, title);
        } catch (Exception e) {
			// ignore, called for other reason ...
		}

	}

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
     * @param song
     *              the new value
     */
    protected void newValue(final String song, final String title) {

		runOnUiThread(new Runnable() {
			public void run() {

                String encVal = song;
                if (encVal.startsWith(getResources().getString(R.string.spec_ident))) {
					encVal = encVal.replace(getResources().getString(R.string.spec_ident), "");
					int i = Integer.parseInt(encVal, 16);
					encVal = Character.toString((char) i);
				}
				if( encVal.equals("El")) {
                    songView.setText("Elan");
                    SpannableString ss = new SpannableString("abc");
					ImageSpan is = new ImageSpan(elan_logo, ImageSpan.ALIGN_BASELINE);
					ss.setSpan(is, 0, 3, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
					mainLayout.setBackgroundColor(Color.WHITE);
                    songView.setText(ss);
                }
				else {
					mainLayout.setBackgroundColor(Color.BLACK);
                    songView.setText(encVal);
                }

                if (showTitle) {
                    textView.setText(title);
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
     *                  the message to show
     */
	protected void showMessage(final String message) {

		runOnUiThread(new Runnable() {
			public void run() {
				Utils.showDialog(message, ElanReceiverActivity.this);
			}
		});
	}

}
