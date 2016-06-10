package de.clemensloos.elan.receiver;


import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

import de.clemensloos.elan.receiver.util.Utils;


public class ElanServerService extends Service {

	private boolean running = true;
	
	private int port;
	
	// The server instance
	private MyNanoHTTPD nanoHttp;
	
	// The notification
	private NotificationCompat.Builder mBuilder;
	private int notificationId = 1;
    private int notificationIdSong = 2;
	
	
	@Override
	public IBinder onBind(Intent intent) {
		// Don't bind me, just call me!
		return null;
	}


    public boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        // if no network is available networkInfo will be null
        // otherwise check if we are connected
        return networkInfo != null && networkInfo.isConnected();
    }

    @Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		running = true;

        if(intent == null) {
            return START_NOT_STICKY;
        }
		
		port = intent.getExtras().getInt(getResources().getString(R.string.port), 0);

        // Is there a network at all?
        if (!isNetworkAvailable()) {
            Toast.makeText(this, getResources().getString(R.string.toast_no_network), Toast.LENGTH_SHORT).show();
            return START_NOT_STICKY;
        }

		try {
			nanoHttp = new MyNanoHTTPD(port, this);
			Toast.makeText(this, getResources().getString(R.string.toast_listening) + port + ".", Toast.LENGTH_SHORT).show();
			
		} catch (IOException e) {
			Toast.makeText(this, R.string.toast_error_listening, Toast.LENGTH_SHORT).show();
			return START_NOT_STICKY;
		}
		
		
		// Create the notification
		mBuilder = new NotificationCompat.Builder(this)
				.setSmallIcon(R.drawable.ic_launcher)
				.setContentTitle(getResources().getString(R.string.notif_title))
				.setContentText(getResources().getString(R.string.notif_desc) + " " + port);
		Intent notificationIntent = new Intent(this, ElanReceiverActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		mBuilder.setContentIntent(pendingIntent);
		Notification notification = mBuilder.build();
		notification.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
		NotificationManager nm = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		nm.notify(notificationId, notification);

		new Thread(new Watchdog()).start();
				
		// If we get killed, after returning from here, restart
		return START_STICKY;
	}
	
	
	
	@Override
	public void onDestroy() {

		running = false;
		
		NotificationManager nm = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		nm.cancel(notificationId);
		
		// stop the server
        if (nanoHttp != null) {
            nanoHttp.stop();
            Toast.makeText(this, getResources().getString(R.string.toast_stop_listening) + port + ".", Toast.LENGTH_SHORT).show();
        }
    }
	
	
	
	// Called from the server, if it receives a new value --> pass it to the UI activity
    public void newValue(final String song, final String title, final String artist) {

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		@SuppressWarnings("deprecation")
		WakeLock aWakeLock = pm.newWakeLock(
				PowerManager.FULL_WAKE_LOCK |
						PowerManager.ACQUIRE_CAUSES_WAKEUP |
						PowerManager.ON_AFTER_RELEASE,
				"TempWakeLock");

		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		boolean popup_mode = preferences.getBoolean(getResources().getString(R.string.pref_popupmode_key), false);

		if (popup_mode) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {

                    String encVal = song;
                    if (encVal.startsWith(getResources().getString(R.string.spec_ident))) {
                        encVal = encVal.replace(getResources().getString(R.string.spec_ident), "");
                        int i = Integer.parseInt(encVal, 16);
                        encVal = Character.toString((char) i);
                    }
                    if( encVal.equals("El")) {
                        return;
                    }

                    LayoutInflater inflater = LayoutInflater.from(getApplicationContext());
                    View layout = inflater.inflate(R.layout.toast_layout, null);

                    TextView text = (TextView) layout.findViewById(R.id.toast_song);
                    text.setText(encVal);
                    TextView info =(TextView) layout.findViewById(R.id.toast_title);
                    info.setText(title + (artist.equals("") ? "" : " - " + artist));

                    Toast toast = new Toast(getApplicationContext());
                    toast.setDuration(Toast.LENGTH_LONG);
                    toast.setView(layout);
                    toast.show();

					// Create the notification
                    NotificationCompat.Builder builder = new NotificationCompat.Builder(ElanServerService.this)
							.setSmallIcon(R.drawable.ic_launcher)
							.setContentTitle("Next song: " + encVal)
							.setContentText(title + (artist.equals("") ? "" : " - " + artist));
					Intent notificationIntent = new Intent(ElanServerService.this, ElanServerService.class);
					PendingIntent pendingIntent = PendingIntent.getActivity(ElanServerService.this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
					builder.setContentIntent(pendingIntent);
					Notification notification = builder.build();
//					notification.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
					NotificationManager nm = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
					nm.notify(notificationIdSong, notification);

                }
            });
		}

		else {
			Intent intent = new Intent(this, ElanReceiverActivity.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			intent.putExtra(getResources().getString(R.string.song), song);
			intent.putExtra(getResources().getString(R.string.title), title);

			startActivity(intent);

			if (!song.equals("")) {
				pm.userActivity(SystemClock.uptimeMillis(), false);
				aWakeLock.acquire();
				aWakeLock.release();
			}
		}
	}
	
	class Watchdog implements Runnable {

		@Override
		public void run() {

			boolean issue = false;

			while (running) {
				try {
                    //Thread.sleep(120000,0);
                    Thread.sleep(issue ? 30000 : 180000);
                } catch (InterruptedException e) {
					// ignore
				}

				if(!isNetworkAvailable()) {
					issue = true;
                    newValue("NN", "No network!", "");
                    WifiManager wm = (WifiManager) ElanServerService.this.getSystemService(Context.WIFI_SERVICE);
                    if (wm.reconnect()) {
                        if(isNetworkAvailable()) {
                            issue = false;
                            newValue("Ok!", "Network reconnected!", "");
                        }
                    }
				}
				else if (issue) {
					issue = false;
					newValue("Ok!", "Network reconnected!", "");
				}

			}
		}

	}
	
}
