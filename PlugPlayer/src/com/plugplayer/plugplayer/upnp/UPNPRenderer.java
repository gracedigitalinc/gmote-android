package com.plugplayer.plugplayer.upnp;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.cybergarage.upnp.Action;
import org.cybergarage.upnp.AllowedValueRange;
import org.cybergarage.upnp.Device;
import org.cybergarage.upnp.Service;
import org.cybergarage.upnp.StateVariable;
import org.cybergarage.upnp.UPnP;
import org.cybergarage.xml.Node;
import org.cybergarage.xml.Parser;
import org.cybergarage.xml.ParserException;

import android.app.Activity;
import android.content.SharedPreferences;
import android.util.Log;

import com.plugplayer.plugplayer.activities.MainActivity;
import com.plugplayer.plugplayer.activities.SettingsEditor;
import com.plugplayer.plugplayer.utils.StateList;
import com.plugplayer.plugplayer.utils.StateMap;

public class UPNPRenderer extends Renderer
{
	protected boolean WDTVLive = false;
	private transient boolean nostop = false;
	private transient boolean noplay = false;
	protected transient boolean noplayevent = false;
	private transient boolean supportsSeek = false;

	private static final int lastcount = 5;

	public static final String STATENAME = "UPNPRenderer";

	private transient int lasttime[] = new int[lastcount];

	protected transient Service avtransportService;
	protected transient Service renderingControlService;
	protected int maxVolume = 100;

	// private boolean resubscribeAfterPlay = false;

	public UPNPRenderer()
	{
		super();
		playMode = PlayMode.Normal;

		final SharedPreferences preferences = MainActivity.me.getSharedPreferences( SettingsEditor.ADVANCED_SETTINGS, Activity.MODE_PRIVATE );
		nostop = preferences.getBoolean( SettingsEditor.IGNORE_STOP, false );
		noplayevent = preferences.getBoolean( SettingsEditor.IGNORE_PLAY, false );
	}

	public static UPNPRenderer createDevice( Device dev )
	{
		Service avtransportService = dev.getService( "urn:schemas-upnp-org:service:AVTransport:1" );
		Service renderingControlService = dev.getService( "urn:schemas-upnp-org:service:RenderingControl:1" );

		if ( avtransportService != null && renderingControlService != null )
		{
			UPNPRenderer renderer = new UPNPRenderer();
			renderer.updateServices( dev );

			try
			{
				if ( !dev.getIconList().isEmpty() )
					renderer.setIconURL( dev.getAbsoluteURL( dev.getIcon( 0 ).getURL() ) );

				if ( "WD TV HD Live".equals( dev.getModelName() ) )
					renderer.WDTVLive = true;
				else
					renderer.WDTVLive = false;
			}
			catch ( Exception e )
			{
				e.printStackTrace();
			}

			try
			{
				StateVariable volume = renderingControlService.getStateVariable( "Volume" );
				if ( volume != null && volume.hasAllowedValueRange() )
				{
					AllowedValueRange range = volume.getAllowedValueRange();
					renderer.maxVolume = Integer.parseInt( range.getMaximum() );
				}

				return renderer;
			}
			catch ( Exception e )
			{
				e.printStackTrace();
			}
		}

		return null;
	}

	@Override
	public void updateServices( Device dev )
	{
		avtransportService = dev.getService( "urn:schemas-upnp-org:service:AVTransport:1" );
		renderingControlService = dev.getService( "urn:schemas-upnp-org:service:RenderingControl:1" );

		setUDN( dev.getUDN() );
		setLocation( dev.getLocation() );
		setOriginalName( dev.getFriendlyName() );

		// Some devices don't have a valid renderingcontrol.xml
		// Use PlugPlayers instead!
		try
		{
			renderingControlService.getServiceStateTable();
		}
		catch ( Exception e )
		{
			try
			{
				File webDir = new File( MainActivity.me.getFilesDir() + "/web" );
				renderingControlService.loadSCPD( new File( webDir + "/RenderingControl.xml" ) );
			}
			catch ( ParserException e1 )
			{
				e1.printStackTrace();
			}
		}

		try
		{
			avtransportService.getServiceStateTable();
		}
		catch ( Exception e )
		{
			try
			{
				File webDir = new File( MainActivity.me.getFilesDir() + "/web" );
				avtransportService.loadSCPD( new File( webDir + "/AVTransport.xml" ) );
			}
			catch ( ParserException e1 )
			{
				e1.printStackTrace();
			}
		}

		// resubscribeAfterPlay = "Royal Philips Electronics".equals( dev.getManufacture() );
		noplay = "Royal Philips Electronics".equals( dev.getManufacture() );

		PlugPlayerControlPoint.getInstance().controlPoint.addDevice( dev.getRootNode() );
	}

	@Override
	public void toState( StateMap state )
	{
		super.toState( state );

		state.setName( STATENAME );

		state.setValue( "WDTVLive", WDTVLive );
		state.setValue( "maxVolume", maxVolume );

		StateList playlistState = new StateList();
		for ( Item item : playlist )
		{
			StateMap itemState = new StateMap();
			item.toState( itemState );
			playlistState.add( itemState );
		}
		state.setList( "playlist", playlistState );
	}

	@Override
	public void fromState( StateMap state )
	{
		super.fromState( state );

		WDTVLive = state.getValue( "WDTVLive", false );
		maxVolume = state.getValue( "maxVolume", 100 );

		StateList playlistState = state.getList( "playlist" );
		for ( StateMap itemState : playlistState )
		{
			Item item = Item.createFromState( itemState );
			playlist.add( item );
		}
	}

	public static UPNPRenderer createFromState( StateMap state )
	{
		UPNPRenderer rval = new UPNPRenderer();
		rval.fromState( state );
		return rval;
	}

	@Override
	public void setActive( boolean active, boolean hardStop )
	{
		if ( active == getActive() )
			return;

		if ( active )
		{
			supportsSeek = false;

			// Set the current volume
			volume = getCurrentVolume();
			emitVolumeChanged( volume );

			// Figure out what is currently playing and notify listeners
			updateCurrentTrack();

			controlPoint.subscribe( this, avtransportService );

			if ( renderingControlService != null )
				controlPoint.subscribe( this, renderingControlService );
		}
		else
		{
			System.out.println( "will usubscribe, hardStop = " + hardStop );
			if ( !hardStop )
				controlPoint.unsubscribe( this, avtransportService );

			if ( renderingControlService != null && !hardStop )
				controlPoint.unsubscribe( this, renderingControlService );
			System.out.println( "did usubscribe, hardStop = " + hardStop );
		}

		super.setActive( active, hardStop );
	}

	@Override
	protected void updateCurrentTimeStamp()
	{
		if ( noplay && !WDTVLive )
		{
			PlayState currentPlayState = getTransportState();
			if ( currentPlayState != getPlayState() )
			{
				playState = currentPlayState;
				emitPlayStateChanged( playState );
			}
		}

		int time = getCurrentTime();

		if ( WDTVLive && time > getCurrentSeconds() && getPlayState() != PlayState.Playing )
		{
			PlayState currentPlayState = getTransportState();
			if ( currentPlayState != getPlayState() )
			{
				playState = currentPlayState;
				emitPlayStateChanged( playState );
			}
		}

		setCurrentSeconds( time );
	}

	private PlayState getTransportState()
	{
		PlayState newPlayState = PlayState.NoMedia;

		Action getTransportInfo = avtransportService.getAction( "GetTransportInfo" );
		getTransportInfo.setArgumentValue( "InstanceID", 0 );

		if ( getTransportInfo.postControlAction() )
		{
			String state = getTransportInfo.getArgumentValue( "CurrentTransportState" );
			if ( state != null )
			{
				if ( state.equals( "STOPPED" ) || state.equals( "NO_MEDIA_PRESENT" ) )
					newPlayState = PlayState.Stopped;
				else if ( state.equals( "PLAYING" ) )
					newPlayState = PlayState.Playing;
				else if ( state.equals( "TRANSITIONING" ) )
					newPlayState = PlayState.Transitioning;
				else if ( state.equals( "PAUSED_PLAYBACK" ) )
					newPlayState = PlayState.Paused;
			}
		}
		else
		{
			handleError( getTransportInfo );
		}

		return newPlayState;
	}

	protected int getCurrentTime()
	{
		int rval = -1;
		Action getPositionInfo = avtransportService.getAction( "GetPositionInfo" );
		getPositionInfo.setArgumentValue( "InstanceID", 0 );

		if ( getPositionInfo.postControlAction() == true )
		{
			String secstr = getPositionInfo.getArgumentValue( "RelTime" );
			if ( secstr != null )
			{
				rval = secsFromString( secstr );

				if ( nostop )
				{
					if ( getPlayState() == PlayState.Playing && (rval > 0 || rval < lasttime[lastcount - 1]) )
					{
						int i = 0;
						for ( ; i < lastcount; ++i )
							if ( rval != lasttime[i] )
								break;

						if ( i == lastcount || (rval < lasttime[lastcount - 1] && rval < lasttime[lastcount - 2]) )
						{
							for ( i = 0; i < lastcount; ++i )
								lasttime[i] = -i;

							if ( hasNext() )
							{
								// [self performSelectorOnMainThread:@selector(next) withObject:NULL waitUntilDone:true];
								next();
								play();
							}
						}
						else
						{
							for ( i = 0; i < lastcount - 1; ++i )
								lasttime[i] = lasttime[i + 1];

							lasttime[lastcount - 1] = rval;
						}
					}
				}
			}

			String totalstr = getPositionInfo.getArgumentValue( "TrackDuration" );
			if ( totalstr != null )
			{
				int total = secsFromString( totalstr );

				if ( total > 0 )
					setTotalSeconds( total );
			}
		}
		else
		{
			handleError( getPositionInfo );
		}

		return rval;
	}

	private String getCurrentTransportActions()
	{
		String rval = "";

		try
		{
			Action getCurrentTransportActions = avtransportService.getAction( "GetCurrentTransportActions" );
			getCurrentTransportActions.setArgumentValue( "InstanceID", 0 );

			if ( getCurrentTransportActions.postControlAction() )
			{
				rval = getCurrentTransportActions.getArgumentValue( "Actions" );
			}
			else
			{
				handleError( getCurrentTransportActions );
			}
		}
		catch ( Exception e )
		{

		}

		return rval;
	}

	private void updateCurrentTrack()
	{
		String currentMetadata = getCurrentMetadata();
		setPlayState( getTransportState() );

		// GetCurrentTransportActions
		String transportActions = getCurrentTransportActions();
		if ( transportActions != null && transportActions.length() > 0 )
			supportsSeek = transportActions.contains( "Seek" );

		Item item = new Item();
		item.setMetadata( currentMetadata );

		String currentURL = item.getMaxResResourceURL();
		if ( currentURL == null || currentURL.length() == 0 )
			return;

		// Try to match by resource URL
		for ( int i = 0; i < getPlaylistEntryCount(); ++i )
		{
			Item existingItem = getPlaylistEntry( i );
			if ( existingItem.getMaxResResourceURL().equals( currentURL ) )
			{
				currentTrack = i;
				emitTrackNumberChanged( currentTrack );
				return;
			}
		}

		// That failed; try to match by?

		// Isn't currently in the list, so add it.
		int last = getPlaylistEntryCount();
		playlist.add( last, item );
		emitPlaylistChanged();
		currentTrack = last;
		emitTrackNumberChanged( currentTrack );
	}

	private String getCurrentMetadata()
	{
		String rval = null;
		try
		{
			Action getPositionInfo = avtransportService.getAction( "GetPositionInfo" );
			getPositionInfo.setArgumentValue( "InstanceID", 0 );
			if ( getPositionInfo.postControlAction() == true )
			{
				rval = getPositionInfo.getArgumentValue( "TrackMetaData" );
				// String uri = getPositionInfo.getArgumentValue( "TrackURI" );
			}
			else
			{
				handleError( getPositionInfo );
			}
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}
		return rval;
	}

	protected float getCurrentVolume()
	{
		float rval = 0;

		if ( renderingControlService == null )
		{
			handleError( "No Renderering Control service" );
			return 0f;
		}

		Action getVolume = renderingControlService.getAction( "GetVolume" );

		if ( getVolume == null )
		{
			handleError( "No GetVolume action" );
			return 0f;
		}

		getVolume.setArgumentValue( "InstanceID", 0 );
		getVolume.setArgumentValue( "Channel", "Master" );

		if ( getVolume.postControlAction() == true )
		{
			try
			{
				String volString = getVolume.getArgumentValue( "CurrentVolume" );
				if ( volString != null )
					rval = Integer.parseInt( volString ) / (float)maxVolume;
			}
			catch ( Exception e )
			{
				Log.w( MainActivity.appName, "Bad Volumne", e );
			}
		}
		else
		{
			handleError( getVolume );
		}

		return rval;
	}

	@Override
	public boolean hasNext()
	{
		int count = getPlaylistEntryCount();

		if ( playMode == PlayMode.Shuffle )
			return count > 0;
		else if ( playMode == PlayMode.RepeatOne )
			return count > 0;
		else if ( playMode == PlayMode.RepeatAll )
			return count > 0;

		// playMode == PlayMode_Normal
		return currentTrack < (count - 1) && count != 0;
	}

	@Override
	public int nextTrackNumber()
	{
		int count = getPlaylistEntryCount();

		if ( playMode == PlayMode.Shuffle )
			return (int)Math.floor( Math.random() * count );
		else if ( playMode == PlayMode.RepeatOne )
			return currentTrack;
		else if ( playMode == PlayMode.RepeatAll )
			return (currentTrack + 1) % count;

		// // playMode == PlayMode_Normal
		return currentTrack + 1;
	}

	@Override
	public void next()
	{
		if ( !hasNext() )
			return;

		int index = nextTrackNumber();

		if ( index == currentTrack )
			setTrackNumber( index );

		setTrackNumber( index );
	}

	@Override
	public boolean hasPrev()
	{
		int count = getPlaylistEntryCount();
		return (currentTrack > 0 || currentSeconds > 2) && count != 0;
	}

	@Override
	public int prevTrackNumber()
	{
		if ( currentSeconds > 2 )
			return currentTrack;
		else
			return currentTrack - 1;
	}

	@Override
	public void prev()
	{
		if ( !hasPrev() )
			return;

		int index = prevTrackNumber();

		// To make sure setTrackNumber 'sticks'
		currentTrack = -1;
		setTrackNumber( index );
	}

	@Override
	public void play()
	{
		Action play = avtransportService.getAction( "Play" );
		play.setArgumentValue( "InstanceID", 0 );
		play.setArgumentValue( "Speed", 1 );

		if ( play.postControlAction() == true )
		{
			// We never set the state to playing ourselves;
			if ( noplayevent )
				setPlayState( PlayState.Playing );
		}
		else
		{
			handleError( play );
		}
	}

	@Override
	public void pause()
	{
		Action pause = avtransportService.getAction( "Pause" );
		pause.setArgumentValue( "InstanceID", 0 );

		if ( pause.postControlAction() == true )
		{
			setPlayState( PlayState.Paused );
		}
		else
		{
			handleError( pause );
		}
	}

	@Override
	public void stop()
	{
		Action stop = avtransportService.getAction( "Stop" );
		stop.setArgumentValue( "InstanceID", 0 );

		if ( stop.postControlAction() == true )
		{
			// We never set the state to stopped ourselves;
			// setPlayState( PlayState.Stopped );
		}
		else
		{
			handleError( stop );
		}
	}

	PlayState playState = PlayState.NoMedia;

	@Override
	public PlayState getPlayState()
	{
		return playState;
	}

	@Override
	public void setPlayState( PlayState newState )
	{
		playState = newState;

		emitPlayStateChanged( newState );
	}

	int currentSeconds;

	@Override
	public int getCurrentSeconds()
	{
		return currentSeconds;
	}

	private void setCurrentSeconds( int secs )
	{
		currentSeconds = secs;

		emitTimeStampChanged( currentSeconds );
	}

	private int totalSeconds;

	@Override
	public int getTotalSeconds()
	{
		return totalSeconds;
	}

	private void setTotalSeconds( int totalSeconds )
	{
		this.totalSeconds = totalSeconds;
	}

	PlayMode playMode = PlayMode.Normal;

	@Override
	public void setPlayMode( PlayMode mode )
	{
		while ( mode == PlayMode.Unsupported || mode == PlayMode.Random || mode == PlayMode.Direct1 || mode == PlayMode.Intro )
			mode = mode.next();

		playMode = mode;

		emitPlayModeChanged( mode );
	}

	@Override
	public PlayMode getPlayMode()
	{
		return playMode;
	}

	private transient int currentTrack = -1;

	@Override
	public void setTrackNumber( int newTrackNumber )
	{
		if ( currentTrack == newTrackNumber || newTrackNumber < -1 || newTrackNumber >= getPlaylistEntryCount() )
			return;

		currentTrack = newTrackNumber;

		emitTrackNumberChanged( newTrackNumber );

		Item entry = getPlaylistEntry( getTrackNumber() );

		if ( entry == null )
			return;

		// NOTE: setTransportURI will set the state to "STOPPED"; at least on
		// most renderers
		// So, we set the current state to "STOPPED" ahead of time, so that
		// listeners don't get notified
		// XXXX Do we need to only call this for some renderers?!
		PlayState origState = getPlayState();
		playState = PlayState.Stopped;
		stop();
		setCurrentSeconds( 0 );

		int secs = -1;
		try
		{
			Parser parser = UPnP.getXMLParser();
			Node metaDoc = parser.parse( entry.getMetadata() ).getNode( 0 );
			String duration = getNodeAttribute( metaDoc, "res", "duration" );
			secs = secsFromString( duration );
		}
		catch ( Exception e )
		{
		}
		setTotalSeconds( secs );

		// String metadata = entry.getMetadata();
		String metadata = entry.generateExternalMetadata( AndroidServer.getBaseURL() );

		// XXX TODO: Get node.toString() to output namespaces
		// try
		// {
		// Parser parser = UPnP.getXMLParser();
		// Node metadoc = parser.parse( metadata );
		// for ( int i = 0; i < metadoc.getNode( 0 ).getNNodes(); ++i )
		// {
		// Node child = metadoc.getNode( 0 ).getNode( i );
		// if ( child.getName().equals( "desc" ) || child.getName().equals( "upnp:toc" ) )
		// {
		// metadoc.getNode( 0 ).removeNode( child );
		// i--;
		// }
		// // else if (child.getNamespace().equals( "pv") )
		// }
		//
		// // if( [[NSUserDefaults standardUserDefaults] boolForKey:@"filter_res"]
		// // {
		//
		// // XXX This is wrong. Instead of pulling them all out, we should pick
		// // which one to send based on mimetype
		// // XXX This should be done base on .resourceURL which should figure out
		// // which is the right one.
		//
		// // items = ixmlDocument_getElementsByTagName( metadoc, "res" );
		// // for( int i = 1; i < ixmlNodeList_length( items ); ++i )
		// // {
		// // IXML_Node *child = ixmlNodeList_item( items, i );
		// // ixmlNode_removeChild( ixmlNode_getFirstChild( (IXML_Node*)metadoc ),
		// // child, NULL );
		// // }
		// // ixmlNodeList_free( items );
		// // }
		//
		// metadata = metadoc.toString();
		// }
		// catch ( ParserException e2 )
		// {
		// e2.printStackTrace();
		// }

		String url = entry.getMaxResResourceURL();

		if ( nostop )
		{
			for ( int i = 0; i < lastcount; ++i )
				lasttime[i] = -i;
		}

		if ( WDTVLive )
		{
			supportsSeek = true;
			nostop = true;
		}

		// if ( WDTVLive )
		try
		{
			Thread.sleep( (int)(0.25 * 1000) );
		}
		catch ( InterruptedException e1 )
		{
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		Action setTransportURI = avtransportService.getAction( "SetAVTransportURI" );
		setTransportURI.setArgumentValue( "InstanceID", 0 );
		setTransportURI.setArgumentValue( "CurrentURI", url );
		setTransportURI.setArgumentValue( "CurrentURIMetaData", metadata );

		if ( setTransportURI.postControlAction() == true )
		{
		}
		else
		{
			handleError( setTransportURI );
		}

		// if ( WDTVLive )
		try
		{
			Thread.sleep( (int)(0.25 * 1000) );
		}
		catch ( InterruptedException e )
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if ( origState == PlayState.Playing )
			play();
		else
			playState = origState;

		// new Timer().schedule( new TimerTask()
		// {
		// @Override
		// public void run()
		// {
		// // Try to force re-subscribe after we starting playing.
		// if ( resubscribeAfterPlay && getActive() )
		// {
		// setActive( false );
		// setActive( true );
		// }
		// }
		// }, 1500 );
	}

	@Override
	public int getTrackNumber()
	{
		return currentTrack;
	}

	protected float volume = 0.0f;

	@Override
	public float getVolume()
	{
		return volume;
	}

	@Override
	public void setVolume( float newVolume )
	{
		if ( volume == newVolume )
			return;

		if ( WDTVLive && newVolume < 0.01f )
		{
			// setMute( true );
			newVolume = 0.01f;
			return;
		}

		if ( renderingControlService == null )
		{
			handleError( "No Renderering Control service" );
			return;
		}

		Action setVolume = renderingControlService.getAction( "SetVolume" );

		if ( setVolume == null )
			return;

		setVolume.setArgumentValue( "InstanceID", 0 );
		setVolume.setArgumentValue( "Channel", "Master" );
		setVolume.setArgumentValue( "DesiredVolume", (int)(maxVolume * newVolume) );

		if ( setVolume.postControlAction() == true )
		{
			volume = newVolume;
			emitVolumeChanged( volume );
		}
		else
		{
			handleError( setVolume );
		}
	}

	@Override
	public void volumeInc()
	{
		float inc = 1.0f / maxVolume;

		float tmp = getVolume() + inc;
		setVolume( Math.min( 1.0f, tmp ) );
	}

	@Override
	public void volumeDec()
	{
		float inc = 1.0f / maxVolume;

		float tmp = getVolume() - inc;
		setVolume( Math.max( 0.0f, tmp ) );
	}

	boolean mute = false;

	@Override
	public boolean isMute()
	{
		return mute;
	}

	@Override
	public void setMute( boolean newMute )
	{
		if ( mute == newMute )
			return;

		if ( renderingControlService == null )
		{
			handleError( "No Renderering Control service" );
			return;
		}

		Action setMute = renderingControlService.getAction( "SetMute" );

		if ( setMute == null )
			return;

		setMute.setArgumentValue( "InstanceID", 0 );
		setMute.setArgumentValue( "Channel", "Master" );
		setMute.setArgumentValue( "DesiredMute", newMute ? "true" : "false" );

		if ( setMute.postControlAction() == true )
		{
			mute = newMute;

			if ( mute )
				emitVolumeChanged( 0 );
			else
				emitVolumeChanged( volume );
		}
		else
		{
			handleError( setMute );
		}
	}

	protected final List<Item> playlist = new ArrayList<Item>();

	@Override
	public void insertPlaylistEntry( Item newEntry, int index )
	{
		if ( index < 0 )
			index = 0;

		if ( index > playlist.size() )
			index = playlist.size();

		playlist.add( index, newEntry );

		if ( index <= currentTrack )
			currentTrack++;

		emitPlaylistChanged();
	}

	protected void insertPlaylistEntry( Item newEntry, int index, boolean emitChange )
	{
		if ( index < 0 )
			index = 0;

		if ( index > playlist.size() )
			index = playlist.size();

		playlist.add( index, newEntry );

		if ( index <= currentTrack )
			currentTrack++;

		if ( emitChange )
			emitPlaylistChanged();
	}

	@Override
	public void removePlaylistEntry( int index )
	{
		if ( index < 0 || index >= playlist.size() )
			return;

		playlist.remove( index );

		if ( index < currentTrack )
			currentTrack--;

		emitPlaylistChanged();
	}

	@Override
	public void movePlaylistEntry( int fromIndex, int toIndex )
	{
		Item fromItem = playlist.get( fromIndex );
		playlist.remove( fromIndex );
		playlist.add( toIndex, fromItem );

		if ( fromIndex < currentTrack && toIndex >= currentTrack )
			currentTrack--;
		else if ( fromIndex > currentTrack && toIndex <= currentTrack )
			currentTrack++;
		else if ( fromIndex == currentTrack )
			currentTrack = toIndex;

		emitPlaylistChanged();
	}

	@Override
	public void removePlaylistEntries( int index, int count )
	{
		if ( index < 0 || index + count > playlist.size() )
			return;

		for ( int i = 0; i < count; ++i )
		{
			playlist.remove( index );

			if ( index < currentTrack )
				currentTrack--;
		}

		emitPlaylistChanged();
	}

	@Override
	public Item getPlaylistEntry( int index )
	{
		if ( index < 0 || index >= playlist.size() )
			return null;

		return playlist.get( index );
	}

	@Override
	public int getPlaylistEntryCount()
	{
		return playlist.size();
	}

	@Override
	public void eventNotifyReceived( String uuid, long seq, String varName, String value )
	{
		if ( !varName.equals( "LastChange" ) )
			return;

		try
		{
			Parser parser = UPnP.getXMLParser();
			Node innerDoc = parser.parse( value ).getNode( 0 );

			if ( innerDoc == null )
				return;

			String state = getNodeAttribute( innerDoc, "TransportState", "val" );
			if ( state != null )
			{
				PlayState newPlayState = PlayState.NoMedia;

				if ( state.equals( "STOPPED" ) || state.equals( "NO_MEDIA_PRESENT" ) )
					newPlayState = PlayState.Stopped;
				else if ( state.equals( "PLAYING" ) )
					newPlayState = PlayState.Playing;
				else if ( state.equals( "TRANSITIONING" ) )
					newPlayState = PlayState.Transitioning;
				else if ( state.equals( "PAUSED_PLAYBACK" ) )
					newPlayState = PlayState.Paused;
				else
					Log.w( MainActivity.appName, "Unknown State:" + state );

				Log.d( MainActivity.appName, "Got New State: " + newPlayState + "; current state: " + getPlayState() );

				if ( newPlayState != PlayState.Transitioning && getPlayState() != newPlayState )
				{
					if ( getPlayState() == PlayState.Playing && newPlayState == PlayState.Stopped )
						next();

					if ( newPlayState != PlayState.Stopped || !nostop )
						setPlayState( newPlayState );
				}
			}

			if ( !isVolumeChanging() )
			{
				String volume = getNodeAttribute( innerDoc, "Volume", "val" );
				if ( volume != null )
				{
					float v = Integer.parseInt( volume ) / (float)maxVolume;
					if ( v != getVolume() )
					{
						this.volume = v;
						emitVolumeChanged( v );
					}
				}
			}
			String transportActions = getNodeAttribute( innerDoc, "CurrentTransportActions", "val" );
			if ( transportActions != null && transportActions.length() > 0 )
			{
				boolean newSeek = transportActions.contains( "Seek" );

				if ( !supportsSeek && newSeek )
				{
					supportsSeek = newSeek;
					emitTrackNumberChanged( getTrackNumber() );
				}
			}
		}
		catch ( Exception e )
		{
		}
	}

	@Override
	public boolean supportsSeek()
	{
		return supportsSeek;
	}

	@Override
	public boolean seekToSecond( int second )
	{
		int hour = second / 3600;
		int min = (second - hour * 3600) / 60;
		int sec = second - hour * 3600 - min * 60;

		String secString = String.format( "%02d:%02d:%02d", hour, min, sec );

		Action seek = avtransportService.getAction( "Seek" );
		seek.setArgumentValue( "InstanceID", 0 );
		seek.setArgumentValue( "Unit", "REL_TIME" );
		seek.setArgumentValue( "Target", secString );

		if ( seek.postControlAction() == true )
		{
			if ( nostop )
			{
				for ( int i = 0; i < lastcount; ++i )
					lasttime[i] = -i;
			}

			return true;
		}
		else
		{
			// supportsSeek = false;
			handleError( seek );
			return false;
		}
	}
}
