package com.plugplayer.recivaremote.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.plugplayer.plugplayer.upnp.PlugPlayerControlPoint;

public class ConnectivityReceiver extends BroadcastReceiver
{
	public static boolean state = false;

	@Override
	public void onReceive( Context context, Intent intent )
	{
		NetworkInfo info = (NetworkInfo)intent.getParcelableExtra( ConnectivityManager.EXTRA_NETWORK_INFO );
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "onReceive ConnReciver , state="+state+" info.isConnected="+info.isConnected()+" info.getType="+info.getType() );
		if ( state && ( !info.isConnected() || info.getType() != ConnectivityManager.TYPE_WIFI ) )
		{
			state = false;
			
			if ( PlugPlayerControlPoint.getInstance() != null )
				PlugPlayerControlPoint.getInstance().stop( true );
			
			if ( MainActivity.me != null )
				((com.plugplayer.plugplayer.activities.MainActivity)MainActivity.me).showNetwork( true );
		}
		else if ( !state && info.isConnected() && info.getType() == ConnectivityManager.TYPE_WIFI )
		{
			state = true;
			
			if ( PlugPlayerControlPoint.getInstance() != null )
				PlugPlayerControlPoint.getInstance().start();
			
			if ( MainActivity.me != null )
				((com.plugplayer.plugplayer.activities.MainActivity)MainActivity.me).showNetwork( false );
		}
	}
}
