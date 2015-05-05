package com.plugplayer.plugplayer.upnp;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.cybergarage.http.HTTP;
import org.cybergarage.http.HTTPRequest;
import org.cybergarage.http.HTTPServer;
import org.cybergarage.net.HostInterface;
import org.cybergarage.upnp.ControlPoint;
import org.cybergarage.upnp.Device;
import org.cybergarage.upnp.Service;
import org.cybergarage.upnp.UPnP;
import org.cybergarage.upnp.device.DeviceChangeListener;
import org.cybergarage.upnp.device.NotifyListener;
import org.cybergarage.upnp.device.SearchResponseListener;
import org.cybergarage.upnp.event.EventListener;
import org.cybergarage.upnp.ssdp.SSDPPacket;
import org.cybergarage.xml.Node;
import org.cybergarage.xml.Parser;
import org.cybergarage.xml.ParserException;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import com.android.vending.licensing.util.Base64;
import com.plugplayer.plugplayer.activities.MainActivity;
import com.plugplayer.plugplayer.activities.SettingsEditor;
import com.plugplayer.plugplayer.utils.StateList;
import com.plugplayer.plugplayer.utils.StateMap;

public class PlugPlayerControlPoint implements EventListener, NotifyListener, SearchResponseListener, DeviceChangeListener, MediaDeviceListener
{
	private static PlugPlayerControlPoint instance = null;

	public static PlugPlayerControlPoint getInstance()
	{
		return instance;
	}

	private final List<ControlPointListener> listeners;
	private Renderer currentRenderer = null;
	private Server currentServer = null;
	private final Map<String, MediaDevice> subscriptionMap;
	final ControlPoint controlPoint;
	private List<Renderer> rendererList;
	private List<Server> serverList;
	private boolean isStarted = false;

	protected static interface AndroidRendererFactory
	{
		Renderer create();
	}

	protected static AndroidRendererFactory androidRendererFactory = new AndroidRendererFactory()
	{
		public Renderer create()
		{
			return new AndroidRenderer();
		}
	};

	protected static interface AndroidServerFactory
	{
		Server[] create();
	}

	protected static AndroidServerFactory androidServerFactory = new AndroidServerFactory()
	{
		public Server[] create()
		{
			return new Server[] { new AndroidServer(), new MP3TunesServer() };
			// return new Server[] { new MP3TunesServer() };
		}
	};

	public PlugPlayerControlPoint()
	{
		HostInterface.USE_ONLY_IPV4_ADDR = true;
		// HostInterface.USE_LOOPBACK_ADDR = true;
		HTTP.setChunkSize( 128 * 1024 );

		instance = this;
		rendererList = new ArrayList<Renderer>();
		serverList = new ArrayList<Server>();
		listeners = new ArrayList<ControlPointListener>();
		subscriptionMap = new HashMap<String, MediaDevice>();
		addRenderer( androidRendererFactory.create() );
		Server[] builtInServers = androidServerFactory.create();
		if ( builtInServers != null )
			for ( Server s : builtInServers )
				addServer( s );
		controlPoint = new ControlPoint();
		controlPoint.addNotifyListener( this );
		controlPoint.addEventListener( this );
		controlPoint.addSearchResponseListener( this );
		controlPoint.addDeviceChangeListener( this );
		controlPoint.setNMPRMode( true );
	}

	public void toState( StateMap state )
	{
		int currentRendererIndex = -1;
		if ( getCurrentRenderer() != null )
			currentRendererIndex = getRendererList().indexOf( getCurrentRenderer() );

		int currentServerIndex = -1;
		if ( getCurrentServer() != null )
			currentServerIndex = getServerList().indexOf( getCurrentServer() );

		state.setName( "ControlPoint" );
		state.setValue( "currentRendererIndex", currentRendererIndex );
		state.setValue( "currentServerIndex", currentServerIndex );

		StateList rendererListState = new StateList();
		for ( Renderer r : rendererList )
		{
			StateMap rendererState = new StateMap();
			r.toState( rendererState );
			rendererListState.add( rendererState );
		}
		state.setList( "rendererList", rendererListState );

		StateList serverListState = new StateList();
		for ( Server s : serverList )
		{
			StateMap serverState = new StateMap();
			s.toState( serverState );
			serverListState.add( serverState );
		}
		state.setList( "serverList", serverListState );
	}

	public static PlugPlayerControlPoint createFromState( StateMap state )
	{
		PlugPlayerControlPoint rval = new PlugPlayerControlPoint();
		rval.fromState( state );
		return rval;
	}

	protected static interface RendererStateFactory
	{
		Renderer rendererFromState( StateMap rendererState );
	}

	protected static RendererStateFactory rendererStateFactory = new RendererStateFactory()
	{
		public Renderer rendererFromState( StateMap rendererState )
		{
			if ( rendererState.getName().equals( AndroidRenderer.STATENAME ) )
				return AndroidRenderer.createFromState( rendererState );

			if ( rendererState.getName().equals( UPNPRenderer.STATENAME ) )
				return UPNPRenderer.createFromState( rendererState );

			if ( rendererState.getName().equals( LinnCaraRenderer.STATENAME ) )
				return LinnCaraRenderer.createFromState( rendererState );

			if ( rendererState.getName().equals( LinnDavaarRenderer.STATENAME ) )
				return LinnDavaarRenderer.createFromState( rendererState );

			return null;
		}
	};

	protected static interface ServerStateFactory
	{
		Server serverFromState( StateMap serverState );
	}

	protected static ServerStateFactory serverStateFactory = new ServerStateFactory()
	{
		public Server serverFromState( StateMap serverState )
		{
			if ( serverState.getName().equals( AndroidServer.STATENAME ) )
				return AndroidServer.createFromState( serverState );

			if ( serverState.getName().equals( MP3TunesServer.STATENAME ) )
				return MP3TunesServer.createFromState( serverState );

			if ( serverState.getName().equals( UPNPServer.STATENAME ) )
				return UPNPServer.createFromState( serverState );

			return null;
		}
	};

	public void fromState( StateMap state )
	{
		StateList rendererListState = state.getList( "rendererList" );
		rendererList = new ArrayList<Renderer>();
		synchronized ( rendererList )
		{
			rendererList.clear();

			for ( StateMap rendererState : rendererListState )
			{
				Renderer r = rendererStateFactory.rendererFromState( rendererState );

				if ( r != null )
				{
					r.addMediaDeviceListener( this );
					rendererList.add( r );
				}
			}
		}

		// if ( rendererList.isEmpty() || !(rendererList.get( 0 ) instanceof AndroidRenderer) )
		// throw new RuntimeException( "Bad State" );

		StateList serverListState = state.getList( "serverList" );
		serverList = new ArrayList<Server>();
		synchronized ( serverList )
		{
			serverList.clear();

			for ( StateMap serverState : serverListState )
			{
				Server s = serverStateFactory.serverFromState( serverState );

				if ( s != null )
				{
					s.addMediaDeviceListener( this );
					serverList.add( s );
				}
			}
		}

		int currentRendererIndex = state.getValue( "currentRendererIndex", -1 );
		if ( currentRendererIndex >= 0 && currentRendererIndex < getRendererList().size() )
		{
			Renderer r = getRendererList().get( currentRendererIndex );
			r.updateAlive();
			if ( r.isAlive() )
				setCurrentRenderer( r );

			if ( currentRenderer == null )
				for ( Renderer renderer : getRendererList() )
				{
					renderer.updateAlive();
					if ( renderer.isAlive() )
					{
						setCurrentRenderer( renderer );
						break;
					}
				}
		}

		int currentServerIndex = state.getValue( "currentServerIndex", -1 );
		if ( currentServerIndex >= 0 && currentServerIndex < getServerList().size() )
		{
			Server s = getServerList().get( currentServerIndex );
			s.updateAlive();
			if ( s.isAlive() )
				setCurrentServer( s );

			if ( currentServer == null )
				for ( Server server : getServerList() )
				{
					server.updateAlive();
					if ( server.isAlive() )
					{
						setCurrentServer( server );
						break;
					}
				}
		}
	}

	private MediaDevice getDeviceByUSN( String USN )
	{
		if ( USN != null )
		{
			synchronized ( serverList )
			{
				for ( Server s : serverList )
					if ( s.getUDN().length() > 0 && USN.startsWith( s.getUDN() ) )
						return s;
			}

			synchronized ( rendererList )
			{
				for ( Renderer r : rendererList )
					if ( USN.startsWith( r.getUDN() ) )
						return r;
			}
		}

		return null;
	}

	private boolean haveServerDevice( Device dev )
	{
		final SharedPreferences preferences = MainActivity.me.getSharedPreferences( SettingsEditor.ADVANCED_SETTINGS, Activity.MODE_PRIVATE );
		boolean discovery = preferences.getBoolean( SettingsEditor.DEVICE_DISCOVERY, true );

		synchronized ( serverList )
		{
			for ( Server s : serverList )
				if ( s.getUDN().equals( dev.getUDN() ) )
				{
					if ( !discovery )
					{
						if ( s.getLocation().equals( dev.getLocation() ) )
						{
							return true;
						}
					}
					else
					{
						return true;
					}
				}
		}

		return false;
	}

	private boolean haveRendererDevice( Device dev )
	{
		final SharedPreferences preferences = MainActivity.me.getSharedPreferences( SettingsEditor.ADVANCED_SETTINGS, Activity.MODE_PRIVATE );
		boolean discovery = preferences.getBoolean( SettingsEditor.DEVICE_DISCOVERY, true );

		synchronized ( rendererList )
		{
			for ( Renderer r : rendererList )
				if ( r.getUDN().equals( dev.getUDN() ) )
				{
					if ( !discovery )
					{
						if ( r.getLocation().equals( dev.getLocation() ) )
						{
							return true;
						}
					}
					else
					{
						return true;
					}
				}
				else if ( r instanceof LinnRenderer && dev.getUDN().equals( ((LinnRenderer)r).getOtherUDN() ) )
					return true;
		}

		return false;
	}

	public interface DeviceFactory
	{
		MediaDevice createDevice( Device dev );
	}

	protected static DeviceFactory rendererFactory = new DeviceFactory()
	{
		public MediaDevice createDevice( Device dev )
		{
			MediaDevice device = LinnDavaarRenderer.createDevice( dev );

			if ( device == null )
				device = LinnCaraRenderer.createDevice( dev );

			if ( device == null )
				device = UPNPRenderer.createDevice( dev );

			return device;
		}
	};

	protected static DeviceFactory serverFactory = new DeviceFactory()
	{
		public MediaDevice createDevice( Device dev )
		{
			MediaDevice device = UPNPServer.createDevice( dev );

			return device;
		}
	};

	public boolean localRendererAddr( String descriptionURL )
	{
		if ( descriptionURL != null && AndroidRenderer.rendererDevice != null )
		{
			for ( int i = 0; i < AndroidRenderer.rendererDevice.getHTTPServerList().size(); ++i )
			{
				HTTPServer s = AndroidRenderer.rendererDevice.getHTTPServerList().getHTTPServer( i );
				String addr = s.getBindAddress() + ":" + s.getBindPort();
				if ( descriptionURL.contains( addr ) )
					return true;
			}
		}
		return false;
	}

	public boolean localServerAddr( String descriptionURL )
	{
		if ( descriptionURL != null && AndroidServer.serverDevice != null )
		{
			for ( int i = 0; i < AndroidServer.serverDevice.getHTTPServerList().size(); ++i )
			{
				HTTPServer s = AndroidServer.serverDevice.getHTTPServerList().getHTTPServer( i );
				String addr = s.getBindAddress() + ":" + s.getBindPort();
				if ( descriptionURL.contains( addr ) )
					return true;
			}
		}
		return false;
	}

	public void addDevice( Device dev )
	{
		if ( dev == null )
			return;

		if ( !haveRendererDevice( dev ) )
		{
			MediaDevice device = rendererFactory.createDevice( dev );

			if ( device != null )
			{
				Log.i( "MIT_DBG", "inside addDevice(dev), created MediaDevice=" + device.toString() );
				if ( localRendererAddr( device.getLocation() ) )
				{
					// XXX This should never happen, but it has!
				}
				else
				{
					device.setAlive( true );
					addRenderer( (Renderer)device );
				}
			}
			else
			{
				Log.i( "MIT_DBG", "inside addDevice(dev), device == null" );
			}

		}
		else
		{
			Log.i( "MIT_DBG", "inside addDevice(dev), device already exist, so updateservices for devUDN=" + dev.getUDN() );
			MediaDevice device = getDeviceByUSN( dev.getUDN() );
			if ( device != null && !(device instanceof AndroidRenderer) && dev.getLocation() != null
					&& (device.getLocation() == null || !device.getLocation().equals( dev.getLocation() )) )
			{
				device.updateServices( dev );
				device.setAlive( true );
				deviceUpdated( device );
			}
		}

		if ( !haveServerDevice( dev ) )
		{
			MediaDevice device = serverFactory.createDevice( dev );
			Log.i( "MIT_DBG", "inside addDevice(dev), device has ServerDevice" );
			if ( device != null )
			{
				if ( localServerAddr( device.getLocation() ) )
				{
					// XXX This should never happen, but it has!
				}
				else
				{
					device.setAlive( true );
					addServer( (Server)device );
				}
			}
		}
		else
		{
			MediaDevice device = getDeviceByUSN( dev.getUDN() );
			if ( device != null && !(device instanceof AndroidServer) && dev.getLocation() != null
					&& (device.getLocation() == null || !device.getLocation().equals( dev.getLocation() )) )
			{
				device.updateServices( dev );
				device.setAlive( true );
				deviceUpdated( device );
			}
		}
	}

	public void deviceAdded( Device dev )
	{
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "deviceAdded() PPCP" );
		final SharedPreferences preferences = MainActivity.me.getSharedPreferences( SettingsEditor.ADVANCED_SETTINGS, Activity.MODE_PRIVATE );
		if ( !preferences.getBoolean( SettingsEditor.DEVICE_DISCOVERY, true ) )
		{
			Log.i( android.util.PlugPlayerUtil.DBG_TAG, "deviceAdded():DeviceDiscovery is false so return:PPCP" );
			return;
		}
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "deviceAdded() DeviceDiscovery is true so call addDevice():PPCP" );
		addDevice( dev );
	}

	public void deviceUpdated( Device dev )
	{
		// Just make sure we have this device already if we are doing discovery
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "deviceUpdated() Just make sure we have this device already if we are doing discovery:PPCP" );
		deviceAdded( dev );
	}

	public void deviceRemoved( Device dev )
	{
		// XXX TODO: Mark device as not alive and pick a new one if this one was active
		// MediaDevice d = getDeviceByUSN( dev.getUDN() );
		// if ( d != null )
		// {
		//
		// }
	}

	public void deviceSearchResponseReceived( SSDPPacket ssdpPacket )
	{
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "deviceSearchResponseReceived() SSDPPacket = " + ssdpPacket.toString() );
		final SharedPreferences preferences = MainActivity.me.getSharedPreferences( SettingsEditor.ADVANCED_SETTINGS, Activity.MODE_PRIVATE );
		if ( !preferences.getBoolean( SettingsEditor.DEVICE_DISCOVERY, true ) )
			return;

		Device dev = null;

		String newLocation = ssdpPacket.getLocation();
		if ( newLocation != null && newLocation.length() > 0 )
			dev = PlugPlayerControlPoint.deviceFromLocation( newLocation, 1 );

		if ( dev != null )
			dev.setSSDPPacket( ssdpPacket );

		addDevice( dev );
	}

	public void deviceUpdated( MediaDevice d )
	{
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "deviceUpdated() MediaDevice=" + d.toString() );
		if ( d == currentServer || d == currentRenderer )
		{
			d.setActive( false, false );
			d.setActive( true, false );
		}

		if ( d instanceof Renderer )
		{
			for ( ControlPointListener listener : getListenersCopy() )
			{
				Log.i( android.util.PlugPlayerUtil.DBG_TAG, "deviceUpdated() calling mediaRendererListChanged()=" + listener.toString() );
				listener.mediaRendererListChanged();
			}
		}
		else
		{
			for ( ControlPointListener listener : getListenersCopy() )
			{
				Log.i( android.util.PlugPlayerUtil.DBG_TAG, "deviceUpdated() calling mediaServerListChanged()=" + listener.toString() );
				listener.mediaServerListChanged();
			}
		}
	}

	public void deviceNotifyReceived( SSDPPacket ssdpPacket )
	{
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "deviceNotifyReceived()" );
		final SharedPreferences preferences = MainActivity.me.getSharedPreferences( SettingsEditor.ADVANCED_SETTINGS, Activity.MODE_PRIVATE );
		if ( !preferences.getBoolean( SettingsEditor.DEVICE_DISCOVERY, true ) )
		{
			Log.i( android.util.PlugPlayerUtil.DBG_TAG, "inside deviceNotifyReceived(), pref.DEVICE_DISCOVERY is false, so return" );
			return;
		}

		if ( ssdpPacket.isRootDevice() == true && !ssdpPacket.getUSN().equals( "" ) )
		{
			MediaDevice d = getDeviceByUSN( ssdpPacket.getUSN() );
			if ( d == null )
				return;

			if ( ssdpPacket.isAlive() == true )
			{
				String newLocation = ssdpPacket.getLocation();
				if ( newLocation != null && d.getLocation() != null && !d.getLocation().equals( newLocation ) )
				{
					Device dev = PlugPlayerControlPoint.deviceFromLocation( newLocation, d.getSearchTimeout() );
					if ( dev != null )
						d.updateServices( dev );
				}

				if ( !d.isAlive() )
				{
					// d.setAlive( true );
					d.updateAlive();

					if ( d instanceof Renderer )
					{
						for ( ControlPointListener listener : getListenersCopy() )
						{
							Log.i( android.util.PlugPlayerUtil.DBG_TAG, "deviceNotifyReceived() calling mediaRendererListChanged()=" + listener.toString() );
							listener.mediaRendererListChanged();
						}
					}
					else
					{
						for ( ControlPointListener listener : getListenersCopy() )
							listener.mediaServerListChanged();
					}
				}
			}
			else if ( ssdpPacket.isByeBye() == true && d.isAlive() )
			{
				// Log.d( MainActivity.appName, "Got bye bye from: " + d.getName() );

				d.setAlive( false );

				if ( d instanceof Renderer )
				{
					synchronized ( rendererList )
					{
						if ( d == currentRenderer )
						{
							Renderer newRenderer = null;

							for ( Renderer r : rendererList )
								if ( r.isAlive() )
								{
									newRenderer = r;
									break;
								}

							setCurrentRenderer( newRenderer );
						}
					}

					for ( ControlPointListener listener : getListenersCopy() )
						listener.mediaRendererListChanged();
				}
				else
				{
					if ( d == currentServer )
					{
						currentServer = null;

						synchronized ( serverList )
						{
							Server newServer = null;

							for ( Server s : serverList )
								if ( s.isAlive() )
								{
									newServer = s;
									break;
								}

							setCurrentServer( newServer );
						}
					}

					for ( ControlPointListener listener : getListenersCopy() )
						listener.mediaServerListChanged();
				}
			}
		}
	}

	public static Device deviceFromInputStream( InputStream descriptionStream, String location ) throws ParserException
	{
		Parser parser = UPnP.getXMLParser();
		Node rootNode = parser.parse( descriptionStream );
		Node devNode = rootNode.getNode( Device.ELEM_NAME );
		Device rootDev = new Device( rootNode, devNode );

		String data = "Location: " + location;
		SSDPPacket ssdpPacket = new SSDPPacket( data.getBytes(), data.getBytes().length );

		String localAddr = HostInterface.getHostAddress( 0 );
		ssdpPacket.setLocalAddress( localAddr );
		rootDev.setSSDPPacket( ssdpPacket );

		// PlugPlayerControlPoint.getInstance().controlPoint.addDevice( rootNode );

		return rootDev;
	}

	public boolean addDevice( String location )
	{
		Device d = deviceFromLocation( location, 2 );

		if ( d != null )
		{
			addDevice( d );
			return true;
		}
		else
			return false;
	}

	public static Device deviceFromLocation( String location, int searchTimeout )
	{
		URL locationUrl;
		try
		{
			locationUrl = new URL( location );

			// Check to make sure we can connect to this without blocking for a minute
			try
			{
				URLConnection connection = locationUrl.openConnection();
				connection.setRequestProperty( "User-Agent", HTTPRequest.UserAgent );

				String userInfo = locationUrl.getUserInfo();
				if ( userInfo != null )
				{
					String authStringEnc = Base64.encode( userInfo.getBytes() );
					connection.setRequestProperty( "Authorization", "Basic " + authStringEnc );
				}

				connection.setConnectTimeout( searchTimeout * 1000 );
				connection.connect();
				Device rootDev = deviceFromInputStream( connection.getInputStream(), location );
				return rootDev;
			}
			catch ( SocketTimeoutException e )
			{
				return null;
			}
		}
		catch ( FileNotFoundException e )
		{
			// e.printStackTrace();
			return null;
		}
		catch ( Exception e )
		{
			Log.e( MainActivity.appName, "Couln't create device at '" + location + "'", e );
			return null;
		}
	}

	public void addControlPointListener( ControlPointListener listener )
	{
		synchronized ( listeners )
		{
			listeners.add( listener );
		}
	}

	public void removeControlPointListener( ControlPointListener listener )
	{
		synchronized ( listeners )
		{
			listeners.remove( listener );
		}
	}

	public void removeDevice( MediaDevice d )
	{
		if ( d instanceof Renderer )
			removeRenderer( (Renderer)d );
		else
			removeServer( (Server)d );
	}

	private void addServer( Server device )
	{
		final SharedPreferences preferences = MainActivity.me.getSharedPreferences( SettingsEditor.ADVANCED_SETTINGS, Activity.MODE_PRIVATE );
		boolean discovery = preferences.getBoolean( SettingsEditor.DEVICE_DISCOVERY, true );

		if ( device == null )
			return;

		synchronized ( serverList )
		{
			for ( Server server : serverList )
				if ( server.getUDN().equals( device.getUDN() ) )
				{
					if ( !discovery )
					{
						if ( server.getLocation().equals( device.getLocation() ) )
						{
							return;
						}
					}
					else
					{
						return;
					}
				}

			device.addMediaDeviceListener( this );
			serverList.add( device );
			Log.i( "MIT_DBG", "inside addServer(dev),added to serverList=" + serverList.toString() );
		}

		for ( ControlPointListener listener : getListenersCopy() )
			listener.mediaServerListChanged();

		if ( getCurrentServer() == null )
		{
			device.updateAlive();
			if ( device.isAlive() )
				setCurrentServer( device );
		}
	}

	private void removeServer( Server device )
	{
		device.removeMediaDeviceListener( this );

		synchronized ( serverList )
		{
			serverList.remove( device );
		}

		for ( ControlPointListener listener : getListenersCopy() )
			listener.mediaServerListChanged();

		if ( getCurrentServer() == device )
			setCurrentServer( null );
	}

	public List<Server> getServerList()
	{
		return serverList;
	}

	public Server getCurrentServer()
	{
		return currentServer;
	}

	public void setCurrentServer( Server device )
	{
		if ( currentServer == device )
			return;

		if ( currentServer != null )
			currentServer.setActive( false, false );

		Server oldServer = currentServer;
		currentServer = device;

		for ( ControlPointListener listener : getListenersCopy() )
			listener.mediaServerChanged( currentServer, oldServer );

		if ( isStarted && currentServer != null )
			currentServer.setActive( true, false );
	}

	private void addRenderer( Renderer device )
	{
		final SharedPreferences preferences = MainActivity.me.getSharedPreferences( SettingsEditor.ADVANCED_SETTINGS, Activity.MODE_PRIVATE );
		boolean discovery = preferences.getBoolean( SettingsEditor.DEVICE_DISCOVERY, true );

		if ( device == null )
			return;

		synchronized ( rendererList )
		{
			for ( Renderer renderer : rendererList )
				if ( renderer.getUDN().equals( device.getUDN() ) )
				{
					if ( !discovery )
					{
						if ( renderer.getLocation().equals( device.getLocation() ) )
						{
							return;
						}
					}
					else
					{
						return;
					}
				}

			device.addMediaDeviceListener( this );
			rendererList.add( device );
			Log.i( "MIT_DBG", "inside addRendererdev,added to rendererList (size =" + rendererList.size() + " )=" + rendererList.toString() );
		}

		for ( ControlPointListener listener : getListenersCopy() )
		{
			Log.i( android.util.PlugPlayerUtil.DBG_TAG, "calling mediaRendererChanged()from addRenderer : PPControlPoint: PP" );
			listener.mediaRendererListChanged();
		}

		if ( getCurrentRenderer() == null )
		{
			device.updateAlive();
			if ( device.isAlive() )
				setCurrentRenderer( device );
		}
	}

	public void removeRenderer( Renderer device )
	{
		device.removeMediaDeviceListener( this );

		synchronized ( serverList )
		{
			rendererList.remove( device );

			for ( ControlPointListener listener : getListenersCopy() )
				listener.mediaRendererListChanged();

			if ( getCurrentRenderer() == device )
				setCurrentRenderer( rendererList.get( 0 ) );
		}
	}

	public List<Renderer> getRendererList()
	{
		return rendererList;
	}

	public Renderer getCurrentRenderer()
	{
		return currentRenderer;
	}

	public void setCurrentRenderer( Renderer device )
	{
		if ( currentRenderer == device )
			return;

		if ( currentRenderer != null )
			currentRenderer.setActive( false, false );

		Renderer oldRenderer = currentRenderer;
		currentRenderer = device;

		for ( ControlPointListener listener : getListenersCopy() )
		{
			Log.i( android.util.PlugPlayerUtil.DBG_TAG, "calling mediaRendererChanged()from setCurrentRenderer (old = curr) of PPControlPoint: PP" );
			listener.mediaRendererChanged( currentRenderer, oldRenderer );
		}
		if ( isStarted && currentRenderer != null )
			currentRenderer.setActive( true, false );
	}

	private List<ControlPointListener> getListenersCopy()
	{
		List<ControlPointListener> listenersCopy = null;

		synchronized ( listeners )
		{
			listenersCopy = new ArrayList<ControlPointListener>( listeners );
		}

		return listenersCopy;
	}

	public void search()
	{
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "inside PPCP search" );
		controlPoint.search();
	}

	public void checkAliveSync()
	{
		boolean rendererListChanged = false;
		ArrayList<Renderer> renderersCopy;
		synchronized ( getRendererList() )
		{
			renderersCopy = new ArrayList<Renderer>( getRendererList() );
		}

		for ( Renderer renderer : renderersCopy )
		{
			rendererListChanged = rendererListChanged || renderer.updateAlive();
			if ( renderer == getCurrentRenderer() && renderer.isAlive() == false )
				setCurrentRenderer( null );
		}

		if ( getCurrentRenderer() == null )
		{
			for ( Renderer renderer : getRendererList() )
				if ( renderer.isAlive() )
					setCurrentRenderer( renderer );
		}

		if ( rendererListChanged )
			for ( ControlPointListener listener : getListenersCopy() )
				listener.mediaRendererListChanged();

		boolean serverListChanged = false;
		ArrayList<Server> serversCopy;
		synchronized ( getServerList() )
		{
			serversCopy = new ArrayList<Server>( getServerList() );
		}

		for ( Server server : serversCopy )
			serverListChanged = serverListChanged || server.updateAlive();

		if ( serverListChanged )
			for ( ControlPointListener listener : getListenersCopy() )
				listener.mediaServerListChanged();

		if ( getCurrentServer() == null )
		{
			for ( Server server : getServerList() )
				if ( server.isAlive() )
					setCurrentServer( server );
		}
	}

	public void checkAlive()
	{
		new AsyncTask<Object, Object, Object>()
		{
			@Override
			protected Object doInBackground( Object... arg0 )
			{
				checkAliveSync();
				return null;
			}
		}.execute( (Object)null );
	}

	public void eventNotifyReceived( String uuid, long seq, String varName, String value )
	{
		// Log.i( MainActivity.appName, "Event recieved: " + uuid + " : " + varName + "," + value );

		MediaDevice mediaDevice = subscriptionMap.get( uuid );
		if ( mediaDevice != null )
			mediaDevice.eventNotifyReceived( uuid, seq, varName, value );
		else
			Log.e( MainActivity.appName, "Got event for device we won't know about: " + uuid );
	}

	public void subscribe( MediaDevice device, Service service )
	{
		boolean subRet = controlPoint.subscribe( service, 1800 );
		if ( subRet == true )
		{
			String sid = service.getSID();
			subscriptionMap.put( sid, device );
		}
	}

	public void unsubscribe( MediaDevice device, Service service )
	{
		String sid = service.getSID();
		boolean subRet = controlPoint.unsubscribe( service );
		if ( subRet == true )
		{
			subscriptionMap.remove( sid );
		}
	}

	Timer timer = null;

	public void start()
	{
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "PPcontrolPoint start()" );
		if ( controlPoint != null )
		{
			controlPoint.start();
			isStarted = true;

			if ( currentRenderer != null )
				currentRenderer.setActive( true, false );

			if ( currentServer != null )
				currentServer.setActive( true, false );

			timer = new Timer();
			timer.schedule( new TimerTask()
			{
				@Override
				public void run()
				{
					Log.i( android.util.PlugPlayerUtil.DBG_TAG, "PPcontrolPoint start()->run()" );
					search();
					checkAliveSync();
				}
			}, 0, 15 * 1000 );

		}
	}

	public void stop( boolean hardStop )
	{
		if ( controlPoint != null )
		{
			if ( timer != null )
				timer.cancel();

			timer = null;

			if ( currentRenderer != null )
				currentRenderer.setActive( false, hardStop );

			if ( currentServer != null )
				currentServer.setActive( false, hardStop );

			controlPoint.stop();
			isStarted = true;
		}
	}

	public void error( String message )
	{
		for ( ControlPointListener listener : getListenersCopy() )
			listener.onErrorFromDevice( message );

		try
		{
			throw new RuntimeException();
		}
		catch ( Exception e )
		{
			Log.e( MainActivity.appName, message, e );
		}
	}
}