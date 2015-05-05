package com.plugplayer.plugplayer.activities;

import java.io.FileNotFoundException;

import org.cybergarage.http.HTTPRequest;

import android.app.AlertDialog;
import android.app.SearchManager;
import android.app.TabActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings.Secure;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.PlugPlayerUtil;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.Toast;

import com.android.vending.licensing.AESObfuscator;
import com.android.vending.licensing.LicenseChecker;
import com.android.vending.licensing.LicenseCheckerCallback;
import com.android.vending.licensing.ServerManagedPolicy;
import com.plugplayer.plugplayer.R;
import com.plugplayer.plugplayer.upnp.AndroidRenderer;
import com.plugplayer.plugplayer.upnp.ControlPointListener;
import com.plugplayer.plugplayer.upnp.LinnRenderer;
import com.plugplayer.plugplayer.upnp.PlugPlayerControlPoint;
import com.plugplayer.plugplayer.upnp.Renderer;
import com.plugplayer.plugplayer.upnp.Renderer.PlayState;
import com.plugplayer.plugplayer.upnp.Server;
import com.plugplayer.plugplayer.utils.StateMap;

public class MainActivity extends TabActivity implements ControlPointListener, MainActivityInterface
{
	private static final String STATE_NAME = "/controllerstate";

	public static String appName = "";
	public static String appVersion = "";
	public static String appBundle = "";

	public static int defaultSize;
	static public MainActivityInterface me = null;

	public static boolean lockApp = false;

	public static String lockCode = "";

	protected PlugPlayerControlPoint controlPoint = null;

	public Handler handler = new Handler();
	protected LicenseChecker checker = null;

	public enum NetworkType
	{
		NoNetwork, Wifi, Mobile, Other
	};

	protected NetworkType getActiveNetwork()
	{
		NetworkInfo info = ((ConnectivityManager)getSystemService( Context.CONNECTIVITY_SERVICE )).getActiveNetworkInfo();

		if ( info == null || !info.isConnected() )
			return NetworkType.NoNetwork;

		if ( info.getType() == ConnectivityManager.TYPE_WIFI )
			return NetworkType.Wifi;

		if ( info.getType() == ConnectivityManager.TYPE_MOBILE )
			return NetworkType.Mobile;

		return NetworkType.Other;
	}

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

	protected boolean networkCheck()
	{

		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "networkCheck = true:plugplayer.MainActivity: NW=" + getActiveNetwork() );
		if ( getActiveNetwork() == NetworkType.Wifi )
		{
			return true;
		}
		else if ( getActiveNetwork() == NetworkType.Mobile )
		{
			if ( PlugPlayerUtil.mit_debug_enable )
			{
				Toast.makeText( getApplicationContext(), "Not connected to WiFi. Currently on Mobile NW", Toast.LENGTH_LONG ).show();
			}
		}
		else
		{
			if ( PlugPlayerUtil.mit_debug_enable )
			{
				Toast.makeText( getApplicationContext(), "Not connected to WiFi. Currently no NW Connection", Toast.LENGTH_LONG ).show();
			}
		}
		((com.plugplayer.plugplayer.activities.MainActivity)MainActivity.me).showNetwork( true );
		new AlertDialog.Builder( this ).setTitle( "No Wifi Connection" ).setMessage( "Please connect to Wifi Network" )
				.setPositiveButton( android.R.string.ok, new DialogInterface.OnClickListener()
				{
					public void onClick( DialogInterface dialog, int which )
					{
						startActivity( new Intent( android.provider.Settings.ACTION_WIFI_SETTINGS ) );
						finish();
					}
				} ).setNegativeButton( android.R.string.no, new DialogInterface.OnClickListener()
				{
					public void onClick( DialogInterface dialog, int which )
					{
						finish();
					}
				} ).setIcon( android.R.drawable.ic_dialog_alert ).show();

		return false;
	}

	protected void setDefaultSize()
	{
		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics( metrics );
		defaultSize = Math.max( metrics.widthPixels, metrics.heightPixels );
	}

	@Override
	public void onCreate( final Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "onCreate MainActivity of PlugPlayer extends TabActivity" );
		setDefaultSize();

		me = this;

		try
		{
			ApplicationInfo info = getApplication().getApplicationInfo();
			CharSequence appLabel = getPackageManager().getApplicationLabel( info );
			String packageName = getPackageName();
			PackageInfo packageInfo = getPackageManager().getPackageInfo( packageName, 0 );
			HTTPRequest.UserAgent = appLabel + "/" + packageInfo.versionName + " (Android/" + Build.VERSION.RELEASE + "; " + Build.MODEL + ")";

			appName = appLabel.toString();
			appVersion = packageInfo.versionName;
			appBundle = packageInfo.packageName;
		}
		catch ( Exception e )
		{
		}

		SettingsEditor.initSettings();
		setContentView( R.layout.main );

		// if ( !networkCheck() )
		// return;

		Eula.show( this );

		controlPoint = PlugPlayerControlPoint.getInstance();
		if ( controlPoint == null )
		{
			getWindow().setFlags( WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN );
		}
		else
		{
			Log.i( android.util.PlugPlayerUtil.DBG_TAG, "controlPoint got instance, calling showVideo() :plugplayer.MainActivity" );
			showVideo( false );

		}

		// try
		// {
		// Parser p = UPnP.getXMLParser();
		// Node n = p.parse( "<?xml version=\"1.0\"?>\n<scpd xmlns=\"urn:schemas-upnp-org:service-1-0\">\n<specVersion>\n</specVersion>\n</scpd>\n" );
		// System.out.println( n.toString() );
		// System.exit( 0 );
		// }
		// catch ( Exception e )
		// {
		// e.printStackTrace();
		// }

		new AsyncTask<Object, Object, Object>()
		{
			@Override
			protected Object doInBackground( Object... arg0 )
			{
				Log.i( android.util.PlugPlayerUtil.DBG_TAG, "doInBackground() of plugplayer.MainActivity" );
				licenseCheck();

				controlPoint = PlugPlayerControlPoint.getInstance();

				if ( controlPoint == null )
				{
					Log.i( android.util.PlugPlayerUtil.DBG_TAG, "doInBackground:controlPoint =null, createFromState():plugplayer.MainActivity" );
					try
					{
						StateMap state = StateMap.fromXML( getFilesDir() + STATE_NAME );
						controlPoint = PlugPlayerControlPoint.createFromState( state );
						controlPoint.start();
					}
					catch ( FileNotFoundException e )
					{
					}
					catch ( Exception e )
					{
						e.printStackTrace();

						if ( PlugPlayerControlPoint.getInstance() != null )
							PlugPlayerControlPoint.getInstance().stop( false );
					}
				}

				if ( controlPoint == null )
				{
					Log.i( android.util.PlugPlayerUtil.DBG_TAG, "doInBackground:controlPoint =null, so instanciate it:plugplayer.MainActivity" );
					controlPoint = new PlugPlayerControlPoint();
					controlPoint.start();
				}

				controlPoint.addControlPointListener( MainActivity.this );

				// boolean debug = Settings.Secure.getString( this.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID ) == null;
				// if ( debug )
				// {
				// controlPoint.addDevice( "http://192.168.1.76:55178/MediaRenderer" );
				// controlPoint.addDevice( "http://192.168.1.76:55178/Ds" );
				// controlPoint.addDevice( "http://192.168.1.103:49153/description.xml" );
				// controlPoint.addDevice( "http://192.168.1.91:8050/9287b83d-d124-4945-97d1-d8adcd93efa1/description.xml" );
				// }
				return null;
			}

			@Override
			protected void onPostExecute( Object object )
			{
				Log.i( android.util.PlugPlayerUtil.DBG_TAG, "onPostExecute(), setupTabs:plugplayer.MainActivity" );
				setupTabs( savedInstanceState, getTabHost() );
				showVideo( false );
			}
		}.execute( (Object)null );
	}

	/* this method is not used i guess */
	protected void setupTabs( Bundle savedInstanceState, TabHost tabHost )
	{
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "setupTabs(), plugplayer.MainActivity" );
		Resources res = getResources();

		TabHost.TabSpec spec;
		Intent intent;

		// Create an Intent to launch an Activity for the tab (to be reused)
		intent = new Intent().setClass( MainActivity.this, NowPlayingActivity.class );

		// Initialize a TabSpec for each tab and add it to the TabHost
		spec = tabHost.newTabSpec( "nowplaying" ).setIndicator( getResources().getText( R.string.playing ), res.getDrawable( R.drawable.ic_tab_nowplaying ) )
				.setContent( intent );
		tabHost.addTab( spec );
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "uncommenting inside setuptabs: PlugPlayer" );

		// mithun not used when app lunnched
		// Do the same for the other tabs
		intent = new Intent().setClass( MainActivity.this, PlaylistActivity.class );
		spec = tabHost.newTabSpec( "playlist" ).setIndicator( getResources().getText( R.string.playlist ), res.getDrawable( R.drawable.ic_tab_playlist ) )
				.setContent( intent );
		tabHost.addTab( spec );

		intent = new Intent().setClass( MainActivity.this, BrowseActivityGroup.class );
		spec = tabHost.newTabSpec( "browse" ).setIndicator( getResources().getText( R.string.browse ), res.getDrawable( R.drawable.ic_tab_browse ) )
				.setContent( intent );
		tabHost.addTab( spec );

		intent = new Intent().setClass( MainActivity.this, DevicesActivity.class );
		spec = tabHost.newTabSpec( "devices" ).setIndicator( getResources().getText( R.string.devices ), res.getDrawable( R.drawable.ic_tab_devices ) )
				.setContent( intent );
		tabHost.addTab( spec );

		intent = new Intent().setClass( MainActivity.this, LinnActivity.class );
		spec = tabHost.newTabSpec( "linn" ).setIndicator( getResources().getText( R.string.linn ), res.getDrawable( R.drawable.ic_tab_linn ) )
				.setContent( intent );
		tabHost.addTab( spec );

		if ( !(controlPoint.getCurrentRenderer() instanceof LinnRenderer) || !((LinnRenderer)controlPoint.getCurrentRenderer()).hasProductService() )
			tabHost.getTabWidget().getChildTabViewAt( 4 ).setVisibility( View.GONE );

		tabHost.setOnTabChangedListener( new OnTabChangeListener()
		{
			public void onTabChanged( String tabId )
			{
				Log.i( android.util.PlugPlayerUtil.DBG_TAG, "onTabChanged(tabID=" + tabId + "):plugplayer.MainActivity" );
				if ( tabId.equals( "devices" ) )
				{
					controlPoint.search();
					controlPoint.checkAlive();
				}
				else if ( tabId.equals( "linn" ) )
				{
					LinnActivity activity = (LinnActivity)getCurrentActivity();
					activity.updateState();
				}
			}
		} );

		short tab = -1;
		if ( savedInstanceState != null )
			tab = savedInstanceState.getShort( "mycurrentTab", tab );

		if ( tab == -1 )
		{
			if ( controlPoint.getCurrentServer() == null )
				tabHost.setCurrentTab( 3 );
			else if ( controlPoint.getCurrentRenderer().getPlaylistEntryCount() == 0 )
				tabHost.setCurrentTab( 2 );
			else if ( controlPoint.getCurrentRenderer().getPlayState() != PlayState.Playing )
				tabHost.setCurrentTab( 1 );
			else
				tabHost.setCurrentTab( 0 );
		}
		else
			tabHost.setCurrentTab( tab );
	}

	public void setTab( int tab )
	{
		TabHost tabHost = getTabHost();
		tabHost.setCurrentTab( tab );
	}

	@Override
	protected void onSaveInstanceState( Bundle outState )
	{
		super.onSaveInstanceState( outState );

		TabHost tabHost = getTabHost();
		outState.putShort( "mycurrentTab", (short)tabHost.getCurrentTab() );
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "after super.onPause in MainActivity:PlugPlayer" );
		if ( PlugPlayerUtil.mit_debug_enable )
		{
			Toast.makeText( getApplicationContext(), "after super.onPause in MainActivity:PlugPlayer", Toast.LENGTH_SHORT ).show();
		}
		try
		{
			if ( controlPoint != null )
			{
				StateMap state = new StateMap();
				controlPoint.toState( state );
				state.writeXML( getFilesDir() + STATE_NAME );
			}
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}

		System.gc();
	}

	@Override
	protected void onDestroy()
	{
		if ( controlPoint != null )
			controlPoint.stop( false );

		if ( checker != null )
			checker.onDestroy();

		super.onDestroy();
	}

	@Override
	protected void onNewIntent( Intent intent )
	{
		if ( Intent.ACTION_SEARCH.equals( intent.getAction() ) )
		{
			// We should be there already, but just in case...
			TabHost tabHost = getTabHost();
			tabHost.setCurrentTab( 2 );

			String query = intent.getStringExtra( SearchManager.QUERY );

			// trim the keyword
			query = query.trim();

			BrowseActivityGroup.pushSearch( query );
		}
	}

	@Override
	public boolean onSearchRequested()
	{
		return super.onSearchRequested();
		// return getCurrentActivity().onSearchRequested();
	}

	public static boolean ignoreResume = false;

	@Override
	protected void onResume()
	{
		super.onResume();
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "after super.onResume in MainActivity:PlugPlayer" );
		if ( PlugPlayerUtil.mit_debug_enable )
		{
			Toast.makeText( getApplicationContext(), "after super.onResume in MainActivity:PlugPlayer", Toast.LENGTH_SHORT ).show();
		}
		if ( !ignoreResume && controlPoint != null )
		{
			Renderer r = controlPoint.getCurrentRenderer();
			if ( r != null && r.getActive() && !(r instanceof AndroidRenderer) )
			{
				r.setActive( false, false );
				r.setActive( true, false );
			}
		}
	}

	@Override
	public boolean onKeyDown( int keyCode, KeyEvent event )
	{
		if ( controlPoint != null && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) )
		{
			Renderer r = controlPoint.getCurrentRenderer();
			if ( r != null && r instanceof AndroidRenderer )
			{
				r.getVolume();
			}
		}

		return super.onKeyDown( keyCode, event );
	}

	protected final LicenseCheckerCallback licenseCheckerCallback = new LicenseCheckerCallback()
	{
		public void allow()
		{
			if ( isFinishing() )
				return;

			// NO-OP
		}

		public void dontAllow()
		{
			if ( isFinishing() )
				return;

			handler.post( new Runnable()
			{
				public void run()
				{
					Toast.makeText( MainActivity.this, "Error Code 10", Toast.LENGTH_LONG ).show();
					handler.postDelayed( new Runnable()
					{
						public void run()
						{
							System.exit( 0 );
						}
					}, 5000 );
				}
			} );
		}

		/*
		 * public void applicationError( final ApplicationErrorCode errorCode ) { if ( isFinishing() || errorCode == ApplicationErrorCode.CHECK_IN_PROGRESS )
		 * return;
		 * 
		 * handler.post( new Runnable() { public void run() { Toast.makeText( MainActivity.this, "Error Code 20 - " + errorCode.ordinal(), Toast.LENGTH_LONG
		 * ).show(); handler.postDelayed( new Runnable() { public void run() { System.exit( 0 ); } }, 5000 ); } } ); }
		 */

		public void allow( int reason )
		{
			// TODO Auto-generated method stub

		}

		public void dontAllow( int reason )
		{
			// TODO Auto-generated method stub

		}

		public void applicationError( final int errorCode )
		{
			// TODO Auto-generated method stub
			if ( isFinishing() || errorCode == LicenseCheckerCallback.ERROR_CHECK_IN_PROGRESS )
				return;

			handler.post( new Runnable()
			{
				public void run()
				{
					Toast.makeText( MainActivity.this, "Error Code 20 - " + errorCode, Toast.LENGTH_LONG ).show();
					handler.postDelayed( new Runnable()
					{
						public void run()
						{
							System.exit( 0 );
						}
					}, 5000 );
				}
			} );

		}
	};

	boolean videoShown = false;

	// public boolean isVideoShown()
	// {
	// return videoShown;
	// }

	boolean networkShown = false;

	public void showNetwork( final boolean show )
	{
		if ( show == networkShown )
			return;

		handler.post( new Runnable()
		{
			public void run()
			{
				networkShown = show;

				// findViewById( R.id.splash ).setVisibility( View.GONE );
				Log.i( android.util.PlugPlayerUtil.DBG_TAG, "inside ShowNetwork(" + show + ")" );
				if ( show && getActiveNetwork() != NetworkType.Wifi/* to simply not think NW disconnected for every NW change sticky/spurious broadreceived */)
				{
					findViewById( R.id.network ).setVisibility( View.VISIBLE );
					getTabHost().setVisibility( View.GONE );
				}
				else
				{
					findViewById( R.id.network ).setVisibility( View.GONE );
					getTabHost().setVisibility( View.VISIBLE );
				}
			}
		} );
	}

	MediaController mediaController = null;

	public void showVideo( final boolean show )
	{
		showVideo( show, null, null );
	}

	public void showVideo( final boolean show, final View.OnClickListener prevListener, final View.OnClickListener nextListener )
	{
		handler.post( new Runnable()
		{
			public void run()
			{
				videoShown = show;

				ImageView splash = (ImageView)findViewById( R.id.splash );
				splash.setImageDrawable( null );
				splash.setVisibility( View.GONE );

				// final SharedPreferences preferences = MainActivity.this.getSharedPreferences( SettingsEditor.ADVANCED_SETTINGS, Activity.MODE_PRIVATE );

				// if ( show )
				// {
				// if ( preferences.getBoolean( SettingsEditor.INTERNAL_VIDEO, true ) )
				// {
				// findViewById( R.id.videobackground ).setVisibility( View.VISIBLE );
				// VideoView videoView = (VideoView)findViewById( R.id.video );
				// getTabHost().setVisibility( View.GONE );
				// getWindow().setFlags( WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN );
				//
				// mediaController = new MediaController( MainActivity.this )
				// {
				// long lastChange = 0;
				//
				// @Override
				// public void show( int timeout )
				// {
				// long currentTime = System.currentTimeMillis();
				//
				// if ( currentTime - lastChange > 500 )
				// super.show( timeout );
				//
				// lastChange = currentTime;
				// }
				//
				// @Override
				// public void hide()
				// {
				// long currentTime = System.currentTimeMillis();
				//
				// if ( currentTime - lastChange > 500 )
				// super.hide();
				//
				// lastChange = currentTime;
				// };
				//
				// };
				// mediaController.setAnchorView( videoView );
				// videoView.setMediaController( mediaController );
				// mediaController.setPrevNextListeners( prevListener, nextListener );
				// videoView.setVisibility( View.VISIBLE );
				// }
				// }
				// else
				// {
				// if ( preferences.getBoolean( SettingsEditor.INTERNAL_VIDEO, true ) )
				// {
				// mediaController = null;
				// VideoView videoView = (VideoView)findViewById( R.id.video );
				// videoView.setVisibility( View.GONE );
				// findViewById( R.id.videobackground ).setVisibility( View.GONE );
				//
				// // XXX There seems to be a bug in some unknown version of android (2.2.1?!) that causes this to sometimes fail when we aren't playing
				// // video
				// try
				// {
				// videoView.stopPlayback();
				// }
				// catch ( Exception e )
				// {
				// }
				// }
				//
				getTabHost().setVisibility( View.VISIBLE );
				getWindow().setFlags( WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN );
				// }
			}
		} );
	}

	// public MediaController getMediaController()
	// {
	// return mediaController;
	// }
	//
	// public VideoView getVideoView()
	// {
	// return (VideoView)findViewById( R.id.video );
	// }
	//
	// public void videobacktap( View v )
	// {
	// final SharedPreferences preferences = getSharedPreferences( SettingsEditor.ADVANCED_SETTINGS, Activity.MODE_PRIVATE );
	//
	// if ( preferences.getBoolean( SettingsEditor.INTERNAL_VIDEO, true ) )
	// {
	// if ( !videoShown || mediaController == null )
	// return;
	//
	// if ( mediaController.isShowing() )
	// mediaController.hide();
	// else
	// mediaController.show( 3000 );
	// }
	// }
	//
	// public void videotap( View v )
	// {
	// }

	@Override
	public void onBackPressed()
	{
		super.onBackPressed();
		// if ( !videoShown )
		// {
		// // super.onBackPressed();
		// }
		// else
		// showVideo( false );
	}

	public void mediaServerChanged( Server newServer, Server oldServer )
	{
	}

	public void mediaServerListChanged()
	{
	}

	public void mediaRendererChanged( final Renderer newRenderer, Renderer oldRenderer )
	{
		handler.post( new Runnable()
		{
			public void run()
			{
				if ( newRenderer instanceof LinnRenderer && ((LinnRenderer)newRenderer).hasProductService() )
					getTabHost().getTabWidget().getChildTabViewAt( 4 ).setVisibility( View.VISIBLE );
				else
				{
					getTabHost().getTabWidget().getChildTabViewAt( 4 ).setVisibility( View.GONE );
					getTabHost().setCurrentTab( 3 );
				}
			}
		} );
	}

	public void mediaRendererListChanged()
	{
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "inside mediaRendererListChanged, no implementation:MainActivity" );
	}

	public void onErrorFromDevice( final String error )
	{
		// handler.post( new Runnable()
		// {
		// public void run()
		// {
		// Toast.makeText( MainActivity.this, "Error: " + error, Toast.LENGTH_LONG ).show();
		// }
		// } );
	}

	public Handler getHandler()
	{
		return handler;
	}

	public PlugPlayerControlPoint getControlPoint()
	{
		return controlPoint;
	}

	public void quit()
	{
		onPause();
		onDestroy();
		finish();
		System.exit( 0 );
	}

	public Class<?> getSlideShowClass()
	{
		return SlideShowViewer.class;
	}

	public Class<?> getVideoViewerClass()
	{
		return VideoViewer.class;
	}
}
