package com.plugplayer.plugplayer.upnp;

import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import org.cybergarage.http.HTTPRequest;
import org.cybergarage.upnp.Action;
import org.cybergarage.upnp.Device;
import org.cybergarage.upnp.UPnPStatus;
import org.cybergarage.xml.Node;

import android.util.Log;
import android.widget.ImageView;

import com.android.vending.licensing.util.Base64;
import com.example.android.imagedownloader.ImageDownloader;
import com.plugplayer.plugplayer.activities.MainActivity;
import com.plugplayer.plugplayer.utils.StateMap;

public abstract class MediaDevice
{
	protected PlugPlayerControlPoint controlPoint = PlugPlayerControlPoint.getInstance();
	private boolean alive = false;
	private final List<MediaDeviceListener> listeners;

	public void toState( StateMap state )
	{
		state.setValue( "UDN", UDN );
		state.setValue( "location", location );
		state.setValue( "iconURL", iconURL );
		state.setValue( "name", name );
		state.setValue( "searchTimeout", searchTimeout );
		state.setValue( "locationOverride", locationOverride );
		state.setValue( "nameOverride", nameOverride );
		state.setValue( "baseOverride", baseOverride );
	}

	public void fromState( StateMap state )
	{
		UDN = state.getValue( "UDN", "" );
		location = state.getValue( "location", "" );
		iconURL = state.getValue( "iconURL", (String)null );
		name = state.getValue( "name", "" );
		searchTimeout = state.getValue( "searchTimeout", 1 );
		locationOverride = state.getValue( "locationOverride", "" );
		nameOverride = state.getValue( "nameOverride", "" );
		baseOverride = state.getValue( "baseOverride", "" );
	}

	public MediaDevice()
	{
		listeners = new ArrayList<MediaDeviceListener>();
		controlPoint = PlugPlayerControlPoint.getInstance();
	}

	public boolean isAlive()
	{
		if ( getSearchTimeout() == 0 ) // means always assume it's alive
		{
			return true;
		}

		return alive;
	}

	public void setAlive( boolean alive )
	{
		this.alive = alive;
	}

	public boolean updateAlive()
	{
		boolean deviceChanged = false;

		try
		{
			URL locationUrl = new URL( getOverriddenLocation() );
			URLConnection connection = locationUrl.openConnection();
			connection.setRequestProperty( "User-Agent", HTTPRequest.UserAgent );

			String userInfo = locationUrl.getUserInfo();
			if ( userInfo != null )
			{
				String authStringEnc = Base64.encode( userInfo.getBytes() );
				connection.setRequestProperty( "Authorization", "Basic " + authStringEnc );
			}

			connection.setConnectTimeout( 1000 * getSearchTimeout() );
			connection.connect();

			if ( alive != true )
			{
				// XXX We use getLocation here so that we keep track of the "real" location.
				Device dev = PlugPlayerControlPoint.deviceFromInputStream( connection.getInputStream(), getLocation() );
				updateServices( dev );
				alive = true;
				deviceChanged = true;
			}
		}
		catch ( Exception e )
		{
			if ( alive )
				deviceChanged = true;

			alive = false;
		}

		return deviceChanged;
	}

	public abstract void updateServices( Device dev );

	private String UDN = "";

	public String getUDN()
	{
		return UDN;
	}

	public void setUDN( String UDN )
	{
		this.UDN = UDN;
	}

	private String location = "";

	public void setLocation( String location )
	{
		this.location = location;
	}

	public String getLocation()
	{
		return location;
	}

	private String locationOverride = "";

	public void setLocationOverride( String locationOverride )
	{
		this.locationOverride = locationOverride;
	}

	public String getLocationOverride()
	{
		return locationOverride;
	}

	private String baseOverride = "";

	public String getBaseOverride()
	{
		return baseOverride;
	}

	public void setBaseOverride( String baseOverride )
	{
		this.baseOverride = baseOverride;
	}

	public static String overrideBase( String baseURL, String url )
	{
		if ( url == null )
			return null;

		if ( baseURL == null || baseURL.length() == 0 )
			return url;
		else
		{
			int baseEnd = url.indexOf( '/', 8 );
			if ( baseEnd != -1 )
				url = baseURL + url.substring( baseEnd );
			return url;
		}
	}

	public String overrideBase( String url )
	{
		return overrideBase( baseOverride, url );
	}

	public String getOverriddenLocation()
	{
		if ( locationOverride == null || locationOverride.length() == 0 )
			return overrideBase( location );
		else
			return overrideBase( locationOverride );
	}

	private String iconURL;

	public void setIconURL( String iconURL )
	{
		this.iconURL = iconURL;
	}

	public String getIconURL()
	{
		return overrideBase( iconURL );
	}

	public void loadIcon( ImageView imageView )
	{
		ImageDownloader.thumbnail.download( getIconURL(), imageView, !isAlive() );
	}

	private String name;

	public String getName()
	{
		if ( nameOverride == null || nameOverride.length() == 0 )
			return name;
		else
			return nameOverride;
	}

	public String getOriginalName()
	{
		return name;
	}

	public void setOriginalName( String name )
	{
		this.name = name;
	}

	private String nameOverride = "";

	public void setNameOverride( String nameOverride )
	{
		this.nameOverride = nameOverride;
	}

	private int searchTimeout = 1;

	public int getSearchTimeout()
	{
		// TODO Auto-generated method stub
		return searchTimeout;
	}

	public void setSearchTimeout( int searchTimeout )
	{
		this.searchTimeout = searchTimeout;
	}

	private boolean active;

	public boolean getActive()
	{
		return active;
	}

	public void setActive( boolean active, boolean hardStop )
	{
		this.active = active;
	}

	public void addMediaDeviceListener( MediaDeviceListener listener )
	{
		listeners.add( listener );
	}

	public void removeMediaDeviceListener( MediaDeviceListener listener )
	{
		listeners.remove( listener );
	}

	protected void emitError( String message )
	{
		for ( MediaDeviceListener listener : listeners )
			listener.error( message );
	}

	protected void handleError( Action action )
	{
		try
		{
			UPnPStatus err = action.getControlStatus();
			emitError( "Error sending action '" + action.getName() + "': " + err.getCode() + " - " + err.getDescription() );
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}
	}

	protected void handleError( String error )
	{
		emitError( error );
	}

	public void eventNotifyReceived( String uuid, long seq, String varName, String value )
	{
	}

	public static String getNodeAttribute( Node node, String nodeName, String attrName )
	{
		Node child = node.getNode( nodeName );
		if ( child != null )
			return child.getAttributeValue( attrName );

		return null;
	}

	public static String stringFromSecs( int secs )
	{
		long hour = secs / 3600;
		long min = (secs - hour * 3600) / 60;
		long sec = secs - hour * 3600 - min * 60;

		return String.format( "%02d:%02d:%02d", hour, min, sec );
	}

	protected static int secsFromString( String secStr )
	{
		try
		{
			String pieces[] = secStr.split( ":" );
			int hour = Integer.parseInt( pieces[0] );
			int min = Integer.parseInt( pieces[1] );
			int sec = (int)Float.parseFloat( pieces[2] );
			return sec + min * 60 + hour * 60 * 60;
		}
		catch ( Exception e )
		{
			if ( !secStr.equals( "" ) )
				Log.w( MainActivity.appName, "Could not parse time '" + secStr + "'", e );

			return -1;
		}
	}
}
