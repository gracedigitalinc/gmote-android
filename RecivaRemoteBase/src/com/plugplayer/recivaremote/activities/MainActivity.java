package com.plugplayer.recivaremote.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings.Secure;
import android.util.Log;
import android.util.PlugPlayerUtil;
import android.view.View;
import android.widget.TabHost;
import android.widget.Toast;
import android.widget.TabHost.OnTabChangeListener;

import com.android.vending.licensing.AESObfuscator;
import com.android.vending.licensing.LicenseChecker;
import com.android.vending.licensing.ServerManagedPolicy;
import com.plugplayer.plugplayer.activities.SettingsEditor;
import com.plugplayer.plugplayer.upnp.PlugPlayerControlPoint;
import com.plugplayer.plugplayer.upnp.RecivaRenderer;
import com.plugplayer.plugplayer.upnp.RecivaRenderer.RecivaRendererListener;
import com.plugplayer.plugplayer.upnp.Renderer;
import com.plugplayer.plugplayer.upnp.Renderer.PlayMode;
import com.plugplayer.plugplayer.upnp.Renderer.PlayState;
import com.plugplayer.recivaremote.R;
import com.plugplayer.recivaremote.upnp.RecivaControlPoint;

public class MainActivity extends com.plugplayer.plugplayer.activities.MainActivity implements RecivaRendererListener
{
	static
	{
		RecivaControlPoint.init();
	}
	
	@Override
	protected void licenseCheck()
	{
		String deviceId = Secure.getString( getContentResolver(), Secure.ANDROID_ID ) + Build.MODEL + Build.DEVICE + Build.MANUFACTURER + Build.BRAND
				+ Build.CPU_ABI;
		ServerManagedPolicy policy = new ServerManagedPolicy( MainActivity.this, new AESObfuscator( new byte[] { -16, 45, 50, -78, -93, -117, 114, -94, 71, 58,
				-45, -25, 37, -67, -96, -103, -121, 102, -84, 69 }, getPackageName(), deviceId ) );
		checker = new LicenseChecker(
				MainActivity.this,
				policy,
				"MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAmrwPisgdleqZULxymyzATAhc0l7RAM2qTdcnrNUdnvaNIF/ArGJuCyEeEPCnasKWxtufCYeAzrj+JnyDrlLJKkwNEJUPtZdZtAJ8z1bUtxVEKlfuUDSDI68Sb7SEmNj+aB4VMUcAq50Wci4cblYixU+0vSp42u5j69jj428RQfRbVjZGwGWedXNntueEQo8LT8s7lkcdM+9tsRH2g6l2NJ0l518pWLHHF5OS/jbM0gLps4siTo/+NAXdCeVOxHEVojxavk3aRlzcUSRFtGD0dHg00rMNWsVxK/yjZ37DUmcUBZKYmDLB46i0ib+vO31ezu6udA2Ql02ZW5UofgTCKwIDAQAB" );
		checker.checkAccess( licenseCheckerCallback );
	}

	@Override
	public void onCreate( final Bundle savedInstanceState )
	{
		ConnectivityReceiver.state = getActiveNetwork() == NetworkType.Wifi;

		super.onCreate( savedInstanceState );
		Log.i(android.util.PlugPlayerUtil.DBG_TAG, "after super.onCreate of MainActivity: RecivaRemoteBase");
		final SharedPreferences preferences = getSharedPreferences( SettingsEditor.ADVANCED_SETTINGS, Activity.MODE_PRIVATE );
		preferences.edit().putBoolean( SettingsEditor.TAP_TO_PLAY, false ).commit();
	}
	
	@Override
	protected void onResume()
	{
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "No super.onResume in MainActivity:recivaRemote" );
		if(PlugPlayerUtil.mit_debug_enable){
			Toast.makeText( getApplicationContext(), "No super.onResume in MainActivity:recivaRemote", Toast.LENGTH_SHORT ).show();
		}
		ConnectivityReceiver.state = getActiveNetwork() == NetworkType.Wifi;

		if ( ConnectivityReceiver.state )
		{
			super.onResume();
		}
		else
		{
			// still need to call super.onResume() or Android will complain. So set ignoreResume so it won't do any networking
			boolean oldIgnoreResume = MainActivity.ignoreResume;
			MainActivity.ignoreResume = true;
			super.onResume();
			MainActivity.ignoreResume = oldIgnoreResume; // put it back the way it was
		}
		if ( !networkCheck() )
			return;
	}
	
	protected Class getRadiosActivityClass()
	{
		return RadiosActivity.class;
	}
	
	protected void setupTabs( Bundle savedInstanceState, TabHost tabHost )
	{
		Resources res = getResources();

		TabHost.TabSpec spec;
		Intent intent;

		// Create an Intent to launch an Activity for the tab (to be reused)
		intent = new Intent().setClass( MainActivity.this, NowPlayingActivity.class );
		spec = tabHost.newTabSpec( "nowplaying" ).setIndicator( getResources().getText( R.string.playing ), res.getDrawable( R.drawable.ic_tab_nowplaying ) )
				.setContent( intent );
		tabHost.addTab( spec );
		Log.i(android.util.PlugPlayerUtil.DBG_TAG, "commenting inside setuptabs: RecivaRemote");

		// Do the same for the other tabs
		intent = new Intent().setClass( MainActivity.this, PresetsActivity.class );
		spec = tabHost.newTabSpec( "presets" ).setIndicator( getResources().getText( R.string.presets ), res.getDrawable( R.drawable.ic_tab_presets ) )
				.setContent( intent );
		tabHost.addTab( spec );

		intent = new Intent().setClass( MainActivity.this, PlaylistActivity.class );
		spec = tabHost.newTabSpec( "playlist" ).setIndicator( getResources().getText( R.string.playlist ), res.getDrawable( R.drawable.ic_tab_playlist ) )
				.setContent( intent );
		tabHost.addTab( spec );

		intent = new Intent().setClass( MainActivity.this, MenuActivityGroup.class );
		spec = tabHost.newTabSpec( "menu" ).setIndicator( getResources().getText( R.string.menu ), res.getDrawable( R.drawable.ic_tab_menu ) )
				.setContent( intent );
		tabHost.addTab( spec );

		intent = new Intent().setClass( MainActivity.this, getRadiosActivityClass() );
		spec = tabHost.newTabSpec( "radios" ).setIndicator( getResources().getText( R.string.radios ), res.getDrawable( R.drawable.ic_tab_radios ) )
				.setContent( intent );
		tabHost.addTab( spec );
		
		tabHost.setOnTabChangedListener( new OnTabChangeListener()
		{
			public void onTabChanged( String tabId )
			{
				Log.i( android.util.PlugPlayerUtil.DBG_TAG, "onTabChanged(tabID=" + tabId + "):receivaremote.MainActivity" );
				if ( tabId.equals( "radios" ) )
				{
					controlPoint.search();
					controlPoint.checkAlive();
				}
			}
		} );

		short tab = -1;
		if ( savedInstanceState != null )
			tab = savedInstanceState.getShort( "mycurrentTab", tab );

		if ( tab == -1 )
		{ 
			Log.i( android.util.PlugPlayerUtil.DBG_TAG, "setupTabs:, setting tab to 0" );
			tab = 0;//mit change, to set current tab when searching for radios as Radios Tab
			mediaRendererChanged( PlugPlayerControlPoint.getInstance().getCurrentRenderer(), null );
		}
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "setupTabs:,  tab set to "+tab );
			
		tabHost.setCurrentTab( tab );
		getTabHost().invalidate();
		
		//controlPoint.addDevice( "http://192.168.1.66:8050/aaa62e94-4541-4f43-bb46-bdeb486bae1c/description.xml" );
		
		ConnectivityReceiver.state = getActiveNetwork() == NetworkType.Wifi;
		if ( !ConnectivityReceiver.state )
		{
			showNetwork( true );
		}
	}
	

	@Override
	public void emitPowerChanged(boolean powerState)
	{
System.out.println( "Got power change to " + powerState );	
		handler.post( new Runnable()
		{
			public void run()
			{
				RecivaRenderer n = (RecivaRenderer)PlugPlayerControlPoint.getInstance().getCurrentRenderer();
				
				if ( n == null || !n.isPowerOn() )
				{
					getTabHost().getTabWidget().getChildTabViewAt( 1 ).setVisibility( View.GONE );
					getTabHost().getTabWidget().getChildTabViewAt( 2 ).setVisibility( View.GONE );
					getTabHost().getTabWidget().getChildTabViewAt( 3 ).setVisibility( View.GONE );
					getTabHost().setCurrentTab( 0 );
					getTabHost().invalidate();
				}
				else
				{
					getTabHost().getTabWidget().getChildTabViewAt( 1 ).setVisibility( View.VISIBLE );
					getTabHost().getTabWidget().getChildTabViewAt( 2 ).setVisibility( View.VISIBLE );
					getTabHost().getTabWidget().getChildTabViewAt( 3 ).setVisibility( View.VISIBLE );
					getTabHost().invalidate();
				}
			}
		} );
	}
	
	@Override
	public void mediaRendererChanged( final Renderer newRenderer, final Renderer oldRenderer )
	{
		System.out.println( "Got renderer change to " + newRenderer );	
		Log.i(android.util.PlugPlayerUtil.DBG_TAG, "inside mediaRendererChanged() : MainActivity recivaremote");
		handler.post( new Runnable()
		{
			public void run()
			{
				RecivaRenderer n = (RecivaRenderer)newRenderer;
				RecivaRenderer o = (RecivaRenderer)oldRenderer;
				
				if ( o != null )
					o.removeRecivaRendererListener( MainActivity.this );
				
				if ( n != null )
					n.addRecivaRendererListener( MainActivity.this );
				
				if ( n == null || !n.isPowerOn() )
				{
					getTabHost().getTabWidget().getChildTabViewAt( 1 ).setVisibility( View.GONE );
					getTabHost().getTabWidget().getChildTabViewAt( 2 ).setVisibility( View.GONE );
					getTabHost().getTabWidget().getChildTabViewAt( 3 ).setVisibility( View.GONE );
					getTabHost().setCurrentTab( 0 );//mit setting current tab to Radios when searching
					getTabHost().invalidate();
				}
				else
				{
					getTabHost().getTabWidget().getChildTabViewAt( 1 ).setVisibility( View.VISIBLE );
					getTabHost().getTabWidget().getChildTabViewAt( 2 ).setVisibility( View.VISIBLE );
					getTabHost().getTabWidget().getChildTabViewAt( 3 ).setVisibility( View.VISIBLE );
					getTabHost().invalidate();
				}
			}
		} );
	}

	@Override
	public void playStateChanged(PlayState state)
	{
	}

	@Override
	public void playModeChanged(PlayMode mode)
	{
	}

	@Override
	public void playlistChanged()
	{
	}

	@Override
	public void volumeChanged(float volume)
	{
	}

	@Override
	public void trackNumberChanged(int trackNumber)
	{
	}

	@Override
	public void timeStampChanged(int seconds)
	{
	}

	@Override
	public void error(String message)
	{
	}

	@Override
	public void radioStationChanged(String playbackXMLString)
	{
	}
}
