package de.clemensloos.elan.receiver;


import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.widget.Toast;

import java.io.IOException;


public class ElanServerService extends Service {

	
	
	private int port;
	
	// The server instance
	private MyNanoHTTPD nanoHttp;
	
	// The notification
	private NotificationCompat.Builder mBuilder;
	private int notificationId = 1;
	
	
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
				
		// If we get killed, after returning from here, restart
		return START_STICKY;
	}
	
	
	
	@Override
	public void onDestroy() {
		
		NotificationManager nm = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		nm.cancel(notificationId);
		
		// stop the server
        if (nanoHttp != null) {
            nanoHttp.stop();
            Toast.makeText(this, getResources().getString(R.string.toast_stop_listening) + port + ".", Toast.LENGTH_SHORT).show();
        }
    }
	
	
	
	// Called from the server, if it receives a new value --> pass it to the UI activity
    public void newValue(String song, String title) {

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		@SuppressWarnings("deprecation")
		WakeLock aWakeLock = pm.newWakeLock(
				PowerManager.FULL_WAKE_LOCK |
				PowerManager.ACQUIRE_CAUSES_WAKEUP | 
				PowerManager.ON_AFTER_RELEASE,
				"TempWakeLock");
		
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
