package com.plugplayer.plugplayer.upnp;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.cybergarage.upnp.Action;
import org.cybergarage.upnp.AllowedValue;
import org.cybergarage.upnp.AllowedValueList;
import org.cybergarage.upnp.AllowedValueRange;
import org.cybergarage.upnp.Device;
import org.cybergarage.upnp.Service;
import org.cybergarage.upnp.StateVariable;
import org.cybergarage.upnp.UPnP;
import org.cybergarage.xml.Node;

import android.os.AsyncTask;
import android.util.Log;

import com.plugplayer.plugplayer.utils.StateMap;

public class RecivaRenderer extends UPNPRenderer
{
	private final List<RecivaRendererListener> listeners = new ArrayList<RecivaRendererListener>();

	public static class MenuEntry
	{
		public static enum MenuType
		{
			Unknown( "unknown" ), Menu( "menu" ), Deferred( "deferred" ), Station( "station" ), Function( "function" ), TextInput( "text-input-control" ), Search(
					"search-control" ), EnqueueMedia( "enqueue-media-track-control" ), Script( "script" );

			private final String string;

			private MenuType( String string )
			{
				this.string = string;
			}

			String getString()
			{
				return string;
			}

			public static MenuType stringToType( String typeString )
			{
				if ( typeString != null )
				{
					if ( typeString.equals( Menu.getString() ) )
						return Menu;
					else if ( typeString.equals( Deferred.getString() ) )
						return Deferred;
					else if ( typeString.equals( Station.getString() ) )
						return Station;
					else if ( typeString.equals( Function.getString() ) )
						return Function;
					else if ( typeString.equals( TextInput.getString() ) )
						return TextInput;
					else if ( typeString.equals( Search.getString() ) )
						return Search;
					else if ( typeString.equals( EnqueueMedia.getString() ) )
						return EnqueueMedia;
					else if ( typeString.equals( Script.getString() ) )
						return Script;
				}

				return MenuType.Unknown;
			}
		};

		public String title;
		public String prompt;
		public String uuid;
		public MenuType type;
		public int number;
	}

	public static class Alarm
	{
		public Date time;
		public int day;
		public String rep;
		public boolean enabled;
		public String type;
		public String stationId;
		public String menuId;
		public String URL;
		public String name;
	}

	public static class Preset
	{
		public String name;
		public String URL;
		public int stationId;
		public int menuId;
	}

	public static final String STATENAME = "RecivaRenderer";

	private Service recivaRadioService;
	private Service recivaSimpleRemoteService;
	private int playlistCount = -1;
	private int _currentTrack = -1;
	private int _stationId;
	private boolean powerState;
	private final Lock resultsLock;
	private final Condition resultsCondition;
	private String lastPlaybackXMLString;
	private float muteVolume = 0;
	private boolean ignoreMenuResult = false;
	// private final int intVolume = 0;

	private boolean ingorePlaylistChanges = false;

	public RecivaRenderer()
	{
		super();
		resultsLock = new ReentrantLock();
		resultsCondition = resultsLock.newCondition();
		powerState = false;
		mute = false;
		lastPlaybackXMLString = null;
	}

	@Override
	public void toState( StateMap state )
	{
		super.toState( state );

		state.setName( STATENAME );
	}

	@Override
	public void fromState( StateMap state )
	{
		super.fromState( state );

	}

	public static RecivaRenderer createFromState( StateMap state )
	{
		RecivaRenderer rval = new RecivaRenderer();
		rval.fromState( state );
		return rval;
	}

	@Override
	protected int getCurrentTime()
	{
		return -1;
	}

	@Override
	public void insertPlaylistEntry( Item newEntry, int index )
	{
		Action insertPlaylistTrack = recivaRadioService.getAction( "InsertPlaylistTrack" );
		insertPlaylistTrack.setArgumentValue( "InsertPosition", index );
		insertPlaylistTrack.setArgumentValue( "TrackData", newEntry.getMetadata() );

		ingorePlaylistChanges = true;

		if ( insertPlaylistTrack.postControlAction() )
		{
			super.insertPlaylistEntry( newEntry, index );
			playlistCount++;
		}
		else
		{
			handleError( insertPlaylistTrack );
		}

		ingorePlaylistChanges = false;
	}

	@Override
	public void removePlaylistEntries( int index, int count )
	{
		ingorePlaylistChanges = true;

		for ( int i = 0; i < count; ++i )
		{
			Action deletePlaylistTrack = recivaRadioService.getAction( "DeletePlaylistTrack" );
			deletePlaylistTrack.setArgumentValue( "PlaylistTrackID", index );

			if ( deletePlaylistTrack.postControlAction() )
			{
				super.removePlaylistEntry( index );
				playlistCount--;
			}
			else
			{
				handleError( deletePlaylistTrack );
			}
		}

		ingorePlaylistChanges = false;
	}

	@Override
	public void removePlaylistEntry( int index )
	{
		removePlaylistEntries( index, 1 );
	}

	@Override
	public void movePlaylistEntry( int fromIndex, int toIndex )
	{
		Action movePlaylistTrack = recivaRadioService.getAction( "MovePlaylistTrack" );
		movePlaylistTrack.setArgumentValue( "FromIndex", fromIndex );
		movePlaylistTrack.setArgumentValue( "ToIndex", toIndex );

		ingorePlaylistChanges = true;

		if ( movePlaylistTrack.postControlAction() )
		{
			super.movePlaylistEntry( fromIndex, toIndex );
		}
		else
		{
			handleError( movePlaylistTrack );
		}

		ingorePlaylistChanges = false;
	}

	private Item getPlaylistEntry_internal( int index )
	{
		Item rval = null;

		Action getPlaylistTrackDetails = recivaRadioService.getAction( "GetPlaylistTrackDetails" );
		getPlaylistTrackDetails.setArgumentValue( "StartTrackID", index );
		getPlaylistTrackDetails.setArgumentValue( "TrackCount", 1 );

		if ( getPlaylistTrackDetails.postControlAction() )
		{
			String tracksXML = getPlaylistTrackDetails.getArgumentValue( "TracksXML" );
			if ( tracksXML != null )
			{
				try
				{
					Node playlistEntryNode = UPnP.getXMLParser().parse( tracksXML ).getNode( "playlist" ).getNode( "playlist-entry" );
					String artist = playlistEntryNode.getNodeValue( "artist" );
					if ( artist == null || artist == "" )
						artist = "Unknown Artist";

					String title = playlistEntryNode.getNodeValue( "title" );
					if ( title == null || title == "" )
						title = "Unknown Title";

					String album = playlistEntryNode.getNodeValue( "album" );
					if ( album == null || album == "" )
						album = "Unknown Album";

					rval = new Item();
					rval.setMetadata( "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"><item><dc:title>"
							+ title + "</dc:title><upnp:artist>" + artist + "</upnp:artist><upnp:album>" + album + "</upnp:album></item></DIDL-Lite>" );
				}
				catch ( Exception e )
				{
					e.printStackTrace();
				}
			}
			else
			{
				handleError( getPlaylistTrackDetails );
			}
		}

		return rval;
	}

	@Override
	public Item getPlaylistEntry( int index )
	{
		int internalCount = super.getPlaylistEntryCount();

		if ( index >= internalCount )
		{
			for ( int i = internalCount; i <= index; ++i )
			{
				Item newItem = getPlaylistEntry_internal( i );
				super.insertPlaylistEntry( newItem, i, false );
			}
		}

		return super.getPlaylistEntry( index );
	}

	private int getPlaylistEntryCount_internal()
	{
		int rval = -1;

		Action getPlaylistLength = recivaRadioService.getAction( "GetPlaylistLength" );

		if ( getPlaylistLength != null && getPlaylistLength.postControlAction() )
		{
			rval = getPlaylistLength.getArgumentIntegerValue( "PlaylistLength" );
		}
		else
		{
			handleError( getPlaylistLength );
		}

		return rval;
	}

	@Override
	public int getPlaylistEntryCount()
	{
		if ( playlistCount < 0 )
			playlistCount = getPlaylistEntryCount_internal();

		return playlistCount;
	}

	@Override
	public void setTrackNumber( int newTrackNumber )
	{
		_currentTrack = newTrackNumber;

		Action setCurrentPlaylistTrack = recivaRadioService.getAction( "SetCurrentPlaylistTrack" );
		setCurrentPlaylistTrack.setArgumentValue( "CurrentPlaylistTrackID", newTrackNumber );

		if ( setCurrentPlaylistTrack.postControlAction() )
		{
		}
		else
		{
			handleError( setCurrentPlaylistTrack );
		}
	}

	@Override
	public int getTrackNumber()
	{
		return _stationId != -1 ? -1 : _currentTrack;
	}

	@Override
	public void next()
	{
		if ( !hasNext() )
			return;

		setTrackNumber( _currentTrack + 1 );
	}

	@Override
	public void prev()
	{
		if ( !hasPrev() )
			return;

		setTrackNumber( _currentTrack - 1 );
	}

	@Override
	public boolean hasNext()
	{
		return getTrackNumber() < (playlistCount - 1) && playlistCount != 0;
	}

	@Override
	public boolean hasPrev()
	{
		return (getTrackNumber() > 0 && playlistCount != 0);
	}

	@Override
	public void play()
	{
		// use discrete play/pause if the radio doesn't support the RecivaSimpleRemote service
		if ( recivaSimpleRemoteService == null )
		{
			super.play();
			return;
		}

		Action play = recivaSimpleRemoteService.getAction( "KeyPressed" );
		play.setArgumentValue( "Key", "PLAY_PAUSE" );
		play.setArgumentValue( "Duration", "SHORT" );

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
		// use discrete play/pause if the radio doesn't support the RecivaSimpleRemote service
		if ( recivaSimpleRemoteService == null )
		{
			super.pause();
			return;
		}

		Action pause = recivaSimpleRemoteService.getAction( "KeyPressed" );
		pause.setArgumentValue( "Key", "PLAY_PAUSE" );
		pause.setArgumentValue( "Duration", "SHORT" );

		if ( pause.postControlAction() == true )
		{
			setPlayState( PlayState.Paused );
		}
		else
		{
			handleError( pause );
		}
	}

	// private int GetStationId_UPNP()
	// {
	// int rval = -1;
	//
	// Action getStationId = recivaRadioService.getAction( "GetStationId" );
	//
	// if ( getStationId.postControlAction() )
	// {
	// rval = getStationId.getArgumentIntegerValue( "RetStationIdValue" );
	// }
	// else
	// {
	// handleError( getStationId );
	// }
	//
	// return rval;
	// }

	// private int GetMenuId_UPNP()
	// {
	// int rval = -1;
	//
	// Action getStationId = recivaRadioService.getAction( "GetStationId" );
	//
	// if ( getStationId.postControlAction() )
	// {
	// rval = getStationId.getArgumentIntegerValue( "RetMenuIdValue" );
	// }
	// else
	// {
	// handleError( getStationId );
	// }
	//
	// return rval;
	// }

	// private int getStationId()
	// {
	// return stationId;
	// }

	// private int getMenuId()
	// {
	// return GetMenuId_UPNP();
	// // return menuId;
	// }

	public boolean isPowerOn()
	{
		return powerState;
	}

	public void setPowerState( boolean on )
	{
		Action setPowerState = recivaRadioService.getAction( "SetPowerState" );
		// mit fix crash 3: for at com.plugplayer.plugplayer.upnp.RecivaRenderer.setPowerState(RecivaRenderer.java:485
		if ( setPowerState != null )
		{
			setPowerState.setArgumentValue( "NewPowerStateValue", on ? "On" : "Off" );

			if ( setPowerState.postControlAction() )
			{
			}
			else
			{
				handleError( setPowerState );
			}
		}
		else
		{
			Log.i( android.util.PlugPlayerUtil.DBG_TAG, "setPowerState==null, RecivaRender:PlugPlayer" );
			handleError( setPowerState );
		}
	}

	// private void wakeUp()
	// {
	// setPowerState( true );
	// }

	// private void goToSleep()
	// {
	// setPowerState( false );
	// }

	public Alarm getAlarm( int num )
	{
		Alarm rval = null;

		Action getAlarm = recivaRadioService.getAction( "GetAlarm" );
		getAlarm.setArgumentValue( "AlarmNumber", num );

		if ( getAlarm.postControlAction() )
		{
			rval = new Alarm();

			String timeStr = getAlarm.getArgumentValue( "RetAlarmTimeValue" );
			if ( timeStr != null )
			{
				try
				{
					DateFormat formatter = new SimpleDateFormat( "HH:mm:ss" );
					rval.time = formatter.parse( timeStr.substring( 0, 8 ) ); // ignore the +00:00 DateFormatter chokes on it
				}
				catch ( ParseException e )
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			rval.day = getAlarm.getArgumentIntegerValue( "RetAlarmDayOfWeekValue" );
			rval.rep = getAlarm.getArgumentValue( "RetAlarmRepetitionValue" );
			rval.enabled = Boolean.parseBoolean( getAlarm.getArgumentValue( "RetAlarmEnabledValue" ) );
			rval.type = getAlarm.getArgumentValue( "RetAlarmTypeValue" );
			rval.stationId = getAlarm.getArgumentValue( "RetAlarmStationIdValue" );
			rval.menuId = getAlarm.getArgumentValue( "RetAlarmMenuIdValue" );
			rval.URL = getAlarm.getArgumentValue( "RetAlarmStationURLValue" );
			rval.name = getAlarm.getArgumentValue( "RetAlarmStationNameValue" );
		}
		else
		{
			handleError( getAlarm );
		}

		return rval;
	}

	public int getNumberOfAlarms()
	{
		int rval = 0;

		Action getNumberOfAlarms = recivaRadioService.getAction( "GetNumberOfAlarms" );

		if ( getNumberOfAlarms.postControlAction() )
		{
			rval = getNumberOfAlarms.getArgumentIntegerValue( "RetNumberOfAlarmsValue" );
		}
		else
		{
			handleError( getNumberOfAlarms );
		}

		return rval;
	}

	// private List<Alarm> getAlarms()
	// {
	// ArrayList<Alarm> rval = new ArrayList<Alarm>();
	//
	// int num = getNumberOfAlarms();
	// for ( int i = 0; i < num; ++i )
	// {
	// Alarm alarm = getAlarm( i );
	// if ( alarm != null )
	// rval.add( alarm );
	// }
	//
	// return rval;
	// }

	public void setAlarmEnabled( int num, boolean enabled )
	{
		Action setAlarmEnabled = recivaRadioService.getAction( "SetAlarmEnabled" );
		setAlarmEnabled.setArgumentValue( "AlarmNumber", num );
		setAlarmEnabled.setArgumentValue( "NewAlarmEnabledValue", enabled ? "true" : "false" );

		if ( setAlarmEnabled.postControlAction() )
		{
		}
		else
		{
			handleError( setAlarmEnabled );
		}
	}

	public void setAlarmTime( int num, Date date )
	{
		String timeStr = new SimpleDateFormat( "HH:mm:ss" ).format( date );

		Action setAlarmTime = recivaRadioService.getAction( "SetAlarmTime" );
		setAlarmTime.setArgumentValue( "AlarmNumber", num );
		setAlarmTime.setArgumentValue( "NewAlarmTimeValue", timeStr );

		if ( setAlarmTime.postControlAction() )
		{
		}
		else
		{
			handleError( setAlarmTime );
		}
	}

	public void setAlarmDay( int num, int day )
	{
		Action setAlarmDayOfWeek = recivaRadioService.getAction( "SetAlarmDayOfWeek" );
		setAlarmDayOfWeek.setArgumentValue( "AlarmNumber", num );
		setAlarmDayOfWeek.setArgumentValue( "NewAlarmDayOfWeekValue", day );

		if ( setAlarmDayOfWeek.postControlAction() )
		{
		}
		else
		{
			handleError( setAlarmDayOfWeek );
		}
	}

	public void setAlarmRep( int num, String rep )
	{
		Action setAlarmRepetition = recivaRadioService.getAction( "SetAlarmRepetition" );
		setAlarmRepetition.setArgumentValue( "AlarmNumber", num );
		setAlarmRepetition.setArgumentValue( "NewAlarmRepetitionValue", rep );

		if ( setAlarmRepetition.postControlAction() )
		{
		}
		else
		{
			handleError( setAlarmRepetition );
		}
	}

	public void setAlarmType( int num, String type )
	{
		Action setAlarmType = recivaRadioService.getAction( "SetAlarmType" );
		setAlarmType.setArgumentValue( "AlarmNumber", num );
		setAlarmType.setArgumentValue( "NewAlarmTypeValue", type );

		if ( setAlarmType.postControlAction() )
		{
		}
		else
		{
			handleError( setAlarmType );
		}
	}

	public void setAlarmStation( int num, Preset station )
	{
		Action setAlarmStation = recivaRadioService.getAction( "SetAlarmStation" );
		setAlarmStation.setArgumentValue( "AlarmNumber", num );
		setAlarmStation.setArgumentValue( "NewAlarmStationIdValue", station.stationId );
		setAlarmStation.setArgumentValue( "NewAlarmMenuIdValue", station.menuId );

		if ( setAlarmStation.postControlAction() )
		{
		}
		else
		{
			handleError( setAlarmStation );
		}
	}

	public void setAlarmStationPreset( int num, int presetNum )
	{
		Action SetAlarmStationPreset = recivaRadioService.getAction( "SetAlarmStationPreset" );
		if ( SetAlarmStationPreset != null )
		{
			SetAlarmStationPreset.setArgumentValue( "AlarmNumber", num );
			SetAlarmStationPreset.setArgumentValue( "NewAlarmStationPresetValue", presetNum );

			if ( SetAlarmStationPreset.postControlAction() )
			{
			}
			else
			{
				handleError( SetAlarmStationPreset );
			}
		}
	}

	// private boolean isUpgradeAvailable()
	// {
	// boolean rval = false;
	//
	// Action getIsUpgradeAvailable = recivaRadioService.getAction( "GetIsUpgradeAvailable" );
	//
	// if ( getIsUpgradeAvailable.postControlAction() )
	// {
	// rval = "1".equals( getIsUpgradeAvailable.getArgument( "RetIsUpgradeAvailableValue" ) );
	// }
	// else
	// {
	// handleError( getIsUpgradeAvailable );
	// }
	//
	// return rval;
	// }

	// private String getHardwareConfig()
	// {
	// String rval = null;
	//
	// Action getHardwareConfig = recivaRadioService.getAction( "GetHardwareConfig" );
	//
	// if ( getHardwareConfig.postControlAction() )
	// {
	// rval = getHardwareConfig.getArgumentValue( "RetHardwareConfigValue" );
	// }
	// else
	// {
	// handleError( getHardwareConfig );
	// }
	//
	// return rval;
	// }

	// private String getServicePack()
	// {
	// String rval = null;
	//
	// Action getCurrentServicePack = recivaRadioService.getAction( "GetCurrentServicePack" );
	//
	// if ( getCurrentServicePack.postControlAction() )
	// {
	// rval = getCurrentServicePack.getArgumentValue( "RetCurrentServicePackValue" );
	// }
	// else
	// {
	// handleError( getCurrentServicePack );
	// }
	//
	// return rval;
	// }

	// private String getSerialNumber()
	// {
	// String rval = null;
	//
	// Action getSerialNumber = recivaRadioService.getAction( "GetSerialNumber" );
	//
	// if ( getSerialNumber.postControlAction() )
	// {
	// rval = getSerialNumber.getArgumentValue( "RetSerialNumberValue" );
	// }
	// else
	// {
	// handleError( getSerialNumber );
	// }
	//
	// return rval;
	// }

	public int getSleepTimeRemaining()
	{
		int rval = 0;
		try
		{
			Action getSleepTimeRemaining = recivaRadioService.getAction( "GetSleepTimeRemaining" );

			if ( getSleepTimeRemaining.postControlAction() )
			{
				rval = getSleepTimeRemaining.getArgumentIntegerValue( "RetSleepTimeRemainingValue" );
			}
			else
			{
				handleError( getSleepTimeRemaining );
			}
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}

		return rval;
	}

	public void setSleepTime( int sleepTime )
	{
		Action setSleepTime = recivaRadioService.getAction( "SetSleepTimer" );
		setSleepTime.setArgumentValue( "NewSleepTimeValue", sleepTime );

		if ( setSleepTime.postControlAction() )
		{
		}
		else
		{
			handleError( setSleepTime );
		}
	}

	private int getNumberOfPresets()
	{
		int rval = 0;

		Action getNumberOfPresets = recivaRadioService.getAction( "GetNumberOfPresets" );

		if ( getNumberOfPresets.postControlAction() )
		{
			rval = getNumberOfPresets.getArgumentIntegerValue( "RetNumberOfPresetsValue" );
		}
		else
		{
			handleError( getNumberOfPresets );
		}

		return rval;
	}

	public static interface MenuListener
	{
		boolean isStillLoading();

		void addMenuEntries( int total, int count, List<MenuEntry> menu );

		void updateProgress( float f );
	}

	private String deferredId = null;
	private String navigatorId = null;
	MenuListener menuListener = null;
	boolean firstime = true;

	private boolean getNavigator()
	{
		Action registerNavigator = recivaRadioService.getAction( "RegisterNavigator" );
		/*
		 * if ( firstime == true ) { firstime = false; handleError( registerNavigator ); return false;
		 * 
		 * }
		 */
		if ( registerNavigator != null && registerNavigator.postControlAction() )
		{
			navigatorId = registerNavigator.getArgumentValue( "RetNavigatorId" );
			return true;
		}
		else
		{
			handleError( registerNavigator );
			return false;
		}
	}

	private String pushNavigatorId = null;
	MenuListener pushMenuListener = null;

	private void pushNavigator()
	{
		pushNavigatorId = navigatorId;
		pushMenuListener = menuListener;
		menuListener = null;
		getNavigator();
	}

	private void popNavigator()
	{
		doneMenu();
		navigatorId = pushNavigatorId;
		menuListener = pushMenuListener;
	}

	public void saveCurrentStationAsPreset( int preset )
	{
		if ( navigatorId == null )
			getNavigator();

		Action saveCurrentStationAsPreset = recivaRadioService.getAction( "SaveCurrentStationAsPreset" );
		saveCurrentStationAsPreset.setArgumentValue( "NavigatorId", navigatorId );
		saveCurrentStationAsPreset.setArgumentValue( "PresetNumber", preset );

		if ( saveCurrentStationAsPreset.postControlAction() )
		{
		}
		else
		{
			handleError( saveCurrentStationAsPreset );
		}
	}

	public Preset getPreset( int num )
	{
		Preset rval = new Preset();

		Action getPreset = recivaRadioService.getAction( "GetPreset" );
		// mit fix Crash 2: for at com.plugplayer.plugplayer.upnp.RecivaRenderer.getPreset(RecivaRenderer.java:876
		if ( getPreset != null )
		{
			getPreset.setArgumentValue( "RetPresetNumberValue", num );

			if ( getPreset.postControlAction() )
			{
				rval.name = getPreset.getArgumentValue( "RetPresetName" );
				rval.URL = getPreset.getArgumentValue( "RetPresetURL" );
				rval.stationId = getPreset.getArgumentIntegerValue( "RetPresetStationIdValue" );
				rval.menuId = getPreset.getArgumentIntegerValue( "RetPresetMenuIdValue" );
			}
			else
			{
				handleError( getPreset );
			}
		}
		else
		{
			Log.i( android.util.PlugPlayerUtil.DBG_TAG, "getPreset==null, RecivaRender:PlugPlayer" );
			handleError( getPreset );
		}
		return rval;
	}

	public List<Preset> getPresets()
	{
		int num = getNumberOfPresets() - 1;

		if ( num < 0 )
			num = 0;

		ArrayList<Preset> rval = new ArrayList<Preset>( num );

		for ( int i = 1; i < num; ++i )
		{
			Preset preset = getPreset( i );
			rval.add( preset );
		}

		return rval;
	}

	public void playPreset( int num )
	{
		Action playPreset = recivaRadioService.getAction( "PlayPreset" );
		playPreset.setArgumentValue( "NewPresetNumberValue", num );

		if ( playPreset.postControlAction() )
		{
			// stationId = getPreset( num ).stationId;
		}
		else
		{
			handleError( playPreset );
		}
	}

	public void goBackMenu()
	{
		Action goBackAndGetResponse = recivaRadioService.getAction( "GoBackAndGetResponse" );
		goBackAndGetResponse.setArgumentValue( "NavigatorId", navigatorId );

		if ( goBackAndGetResponse.postControlAction() )
		{
		}
		else
		{
			handleError( goBackAndGetResponse );
		}
	}

	private void doneMenu()
	{
		if ( navigatorId != null )
		{
			Action releaseNavigator = recivaRadioService.getAction( "ReleaseNavigator" );
			try
			{
				releaseNavigator.setArgumentValue( "NavigatorId", navigatorId );

				if ( releaseNavigator.postControlAction() )
				{
				}
				else
				{
					handleError( releaseNavigator );
				}
			}
			catch ( Exception e )
			{
				e.printStackTrace();
			}
			navigatorId = null;
		}
	}

	List<MenuEntry> menuResults = null;

	private Iterable<Node> findNodes( final Node parent, final String name )
	{
		return new Iterable<Node>()
		{
			public Iterator<Node> iterator()
			{
				return new Iterator<Node>()
				{
					int index = 0;
					private Node next = null;

					public boolean hasNext()
					{
						while ( next == null && index < parent.getNNodes() )
						{
							Node child = parent.getNode( index );

							if ( child.getName().equals( name ) )
								next = child;

							++index;
						}

						return next != null;
					}

					public Node next()
					{
						Node rval = next;
						next = null;
						return rval;
					}

					public void remove()
					{
						throw new UnsupportedOperationException();
					}
				};
			}
		};
	}

	private int processMenuXML( Node menuDoc, List<MenuEntry> results )
	{
		int count = 0;

		menuDoc = menuDoc.getNode( "menu" ).getNode( "items" );
		for ( Node tmpNode : findNodes( menuDoc, "item" ) )
		{
			count++;
			MenuEntry menuEntry = new MenuEntry();
			String itemid = tmpNode.getAttributeValue( "id" );
			String itemType = tmpNode.getAttributeValue( "type" );
			if ( itemType.equals( "deferred" ) )
			{
				String deferredItemType = tmpNode.getAttributeValue( "deferred-type" );
				if ( deferredItemType != "" )
					itemType = deferredItemType;
			}

			menuEntry.title = tmpNode.getValue();
			menuEntry.type = MenuEntry.MenuType.stringToType( itemType );
			// if ( menuEntry.type == MenuType_TextInput )
			// {
			// menuEntry.uuid = [NSString stringWithUTF8String:GetElementAttribute( (IXML_Element*)tmpNode, "uuid" )];
			// menuEntry.prompt = [NSString stringWithUTF8String:GetElementAttribute( (IXML_Element*)tmpNode, "prompt" )];
			// }
			menuEntry.number = Integer.parseInt( itemid );
			results.add( menuEntry );
		}

		return count;
	}

	private int getMoreMenu( int total, int offset, List<MenuEntry> results )
	{
		int rval = 0;
		int count = Math.min( 10, total - offset );

		Action getMenuAtOffset = recivaRadioService.getAction( "GetMenuAtOffset" );
		getMenuAtOffset.setArgumentValue( "NavigatorId", navigatorId );
		getMenuAtOffset.setArgumentValue( "Count", count );
		getMenuAtOffset.setArgumentValue( "Offset", offset );

		if ( getMenuAtOffset.postControlAction() )
		{
			try
			{
				String menuDocString = getMenuAtOffset.getArgumentValue( "RetMenuXML" );
				Node menuDoc = UPnP.getXMLParser().parse( menuDocString );
				rval = processMenuXML( menuDoc, results );
			}
			catch ( Exception e )
			{
				e.printStackTrace();
			}
		}
		else
		{
			handleError( getMenuAtOffset );
		}

		return rval;
	}

	public boolean isLoadingError()
	{
		return ignoreMenuResult;
	}

	public boolean getMenu( int choice, List<MenuEntry> results, MenuEntry entry )
	{
		return getMenu( choice, results, entry, null );
	}

	public boolean getMenu( int choice, List<MenuEntry> results, MenuEntry entry, MenuListener listener )
	{
		String deferredIdString = null;
		ignoreMenuResult = false;
		System.out.println( "getMenu: Selecting choice " + choice + " navigatorId " + navigatorId );
		boolean foundNavigatorId = true;
		if ( choice < 0 )
		{
			if ( navigatorId != null && choice == -1 )
				doneMenu();

			if ( navigatorId == null )
			{
				foundNavigatorId = getNavigator();
				if ( foundNavigatorId == false )
				{
					System.out.println( "Error doing RegisterNavigator navigatorId " + navigatorId );
					ignoreMenuResult = true;
				}
			}

			Action getMenu = recivaRadioService.getAction( "GetMenu" );
			getMenu.setArgumentValue( "NavigatorId", navigatorId );

			if ( getMenu.postControlAction() )
			{
				try
				{
					String menuDocString = getMenu.getArgumentValue( "RetMenuXML" );
					Node menuDoc = UPnP.getXMLParser().parse( menuDocString );
					int count = processMenuXML( menuDoc, results );
					if ( listener != null )
						listener.addMenuEntries( count, count, results );
				}
				catch ( Exception e )
				{
					ignoreMenuResult = true;
					e.printStackTrace();
				}
			}
			else
			{
				ignoreMenuResult = true;
				handleError( getMenu );
			}
		}
		else
		{
			boolean errorFallback = false;
			while ( true )
			{
				boolean output = false;
				System.out.println( "Selecting choice " + choice );
				Action selectItemAndGetResponse = recivaRadioService.getAction( "SelectItemAndGetResponse" );
				selectItemAndGetResponse.setArgumentValue( "NavigatorId", navigatorId );

				selectItemAndGetResponse.setArgumentValue( "NewMenuItemId", choice );

				resultsLock.lock();
				System.out.println( "Selecting choice : Inside Lock" + choice );
				if ( selectItemAndGetResponse.postControlAction() )
				{
					System.out.println( "Selecting choice : postControlAction" + choice );
					if ( results != null )
					{
						try
						{
							String innerDocString = selectItemAndGetResponse.getArgumentValue( "RetNavigationResponse" );
							Node innerDoc = UPnP.getXMLParser().parse( innerDocString );
							deferredIdString = getNodeAttribute( innerDoc, "deferred", "id" );
							System.out.println( "deferredIdString=" + deferredIdString );
							System.out.println( "innerDoc=" + innerDoc );
							System.out.println( "menuResults=" + results );
							if ( deferredIdString != null )
							{
								deferredId = deferredIdString;
								menuListener = listener;
								menuResults = results;
								System.out.println( "resultsCondition waiting for 10 secs: " + System.currentTimeMillis() );
								output = resultsCondition.await( 10, TimeUnit.SECONDS );
								System.out.println( "resultsCondition after wait result=" + output + "time:" + System.currentTimeMillis() );
								if ( output == false )
								{
									System.out.println( "resultsCondition Continuing..." );
									resultsLock.unlock();
									ignoreMenuResult = true;
									return false;
									// goBackMenu();
									// continue;
								}
								deferredId = null;
								// resultsCondition.awaitNanos( 5 * 100000000L ); // 1/2 sec
							}
							else
							{
								int total = 0;
								String totalStr = innerDoc.getNode( "menu" ).getNode( "items" ).getAttributeValue( "count" );
								if ( totalStr != null )
									total = Integer.parseInt( totalStr );

								results.clear();
								int count = processMenuXML( innerDoc, results );
								if ( listener != null )
									listener.addMenuEntries( total, count, results );

								while ( count < total )
								{
									ArrayList<MenuEntry> menu = new ArrayList<MenuEntry>();
									count += getMoreMenu( total, count, menu );
									if ( listener != null )
										listener.addMenuEntries( total, count, menu );
								}
							}
						}
						catch ( Exception e )
						{
							ignoreMenuResult = true;
							e.printStackTrace();
						}
					}
					else if ( entry != null )
					{
						try
						{
							String innerDocString = selectItemAndGetResponse.getArgumentValue( "RetNavigationResponse" );
							Node innerDoc = UPnP.getXMLParser().parse( innerDocString );

							Node tmpNode = innerDoc.getNode( "text-input" );
							if ( tmpNode != null )
							{
								entry.uuid = tmpNode.getAttributeValue( "uuid" );
								entry.prompt = tmpNode.getAttributeValue( "prompt" );
							}
						}
						catch ( Exception e )
						{
							ignoreMenuResult = true;
							e.printStackTrace();
						}
					}
				}
				else
				{
					System.out.println( "Selecting choice : handleError" + selectItemAndGetResponse );
					ignoreMenuResult = true;
					handleError( selectItemAndGetResponse );
				}
				// if ( deferredIdString == null )
				{
					System.out.println( "resetting menuResults" );
					menuResults = null;
				}
				System.out.println( "Selecting choice : Inside unlock Choice= " + choice + "ignoreMenuResult = " + ignoreMenuResult );
				resultsLock.unlock();
				break;
			}
		}
		if ( ignoreMenuResult == true )
		{
			return false;
		}
		return true;
	}

	private void enterReplyMenu( List<MenuEntry> results )
	{
		Action enterReplyMenu = recivaRadioService.getAction( "EnterReplyMenu" );
		enterReplyMenu.setArgumentValue( "NavigatorId", navigatorId );

		resultsLock.lock();

		if ( enterReplyMenu.postControlAction() )
		{
			menuResults = results;
			try
			{
				resultsCondition.awaitNanos( 10 * 1000000000L );
			}
			catch ( InterruptedException e )
			{
				e.printStackTrace();
			}
		}
		else
		{
			handleError( enterReplyMenu );
		}

		menuResults = null;
		resultsLock.unlock();
	}

	public void enterText( String text, String uuid, List<MenuEntry> results, MenuListener listener )
	{
		Action submitTextInput = recivaRadioService.getAction( "SubmitTextInput" );
		submitTextInput.setArgumentValue( "NavigatorId", navigatorId );
		submitTextInput.setArgumentValue( "UUID", uuid );
		submitTextInput.setArgumentValue( "Text", text );

		resultsLock.lock();

		if ( submitTextInput.postControlAction() && text.length() != 0 )
		{
			menuResults = results;
			menuListener = listener;

			try
			{
				resultsCondition.awaitNanos( 10 * 1000000000L );
			}
			catch ( InterruptedException e )
			{
				e.printStackTrace();
			}
		}
		else
		{
			handleError( submitTextInput );
		}

		menuResults = null;
		resultsLock.unlock();
	}

	// private void searchForStationByName( String name )
	// {
	// Action searchForStationByName = recivaRadioService.getAction( "SearchForStationByName" );
	// searchForStationByName.setArgumentValue( "StationName", name );
	//
	// if ( searchForStationByName.postControlAction() )
	// {
	// String result = searchForStationByName.getArgumentValue( "ResultXML" );
	// // printf("%s\n",[result UTF8String]);
	// }
	// else
	// {
	// handleError( searchForStationByName );
	// }
	// }

	// private void getAudioSources()
	// {
	// Action getAudioSources = recivaRadioService.getAction( "GetAudioSources" );
	//
	// if ( getAudioSources.postControlAction() )
	// {
	// }
	// else
	// {
	// handleError( getAudioSources );
	// }
	// }

	@Override
	public void setActive( boolean active, boolean hardStop )
	{
		if ( active == this.getActive() )
			return;

		super.setActive( active, hardStop );

		if ( !active )
		{
			if ( !hardStop )
				controlPoint.unsubscribe( this, recivaRadioService );
		}
		else
		{
			controlPoint.subscribe( this, recivaRadioService );

			// stationId = GetStationId_UPNP();
			// menuId = GetMenuId_UPNP();
		}
	}

	private boolean mute = false;

	@Override
	public boolean isMute()
	{
		return mute;
	}

	// public void setIntVolume( int newVolume )
	// {
	// System.out.println( "Setting volume to " + newVolume );
	// mute = false;
	//
	// if ( intVolume == newVolume )
	// return;
	//
	// Action setVolume = recivaRadioService.getAction( "SetVolume" );
	// setVolume.setArgumentValue( "NewVolumeValue", newVolume );
	//
	// if ( setVolume.postControlAction() == true )
	// {
	// volume = newVolume / 100.0f;
	// intVolume = newVolume;
	// emitVolumeChanged( volume );
	// }
	// else
	// {
	// handleError( setVolume );
	// }
	// }

	// private void setVolumeBase( float newVolume )
	// {
	// if ( newVolume != 0 )
	// mute = false;
	//
	// volume = newVolume;
	// emitVolumeChanged( volume );
	// }

	// @Override
	// protected float getCurrentVolume()
	// {
	// float rval = 0;
	//
	// Action getVolume = recivaRadioService.getAction( "GetVolume" );
	// if ( getVolume.postControlAction() == true )
	// {
	// String volString = getVolume.getArgumentValue( "RetVolumeValue" );
	// if ( volString != null )
	// {
	// intVolume = Integer.parseInt( volString );
	// rval = intVolume / 100.0f;
	// }
	// }
	// else
	// {
	// handleError( getVolume );
	// }
	//
	// return rval;
	// }

	@Override
	public void volumeInc()
	{
		volume = getCurrentVolume();
		if ( mute )
		{
			toggleMute();
		}
		else
		{
			float tmp = volume + 0.07f;
			setVolume( Math.min( 1.0f, tmp ) );
		}
	}

	@Override
	public void volumeDec()
	{
		volume = getCurrentVolume();
		if ( mute )
		{
			toggleMute();
		}
		else
		{
			float tmp = volume - 0.07f;
			setVolume( Math.max( 0.0f, tmp ) );
		}
	}

	public void toggleMute()
	{
		if ( mute )
		{
			mute = false;
			super.setVolume( muteVolume );
		}
		else
		{
			mute = true;
			muteVolume = getCurrentVolume() + 0.05f;
			super.setVolume( 0 );
		}
	}

	public String lastPlaybackXMLString()
	{
		return lastPlaybackXMLString;
	}

	private void loadMenuInBackground( final int total, final int loadCount )
	{
		new AsyncTask<Object, Object, Object>()
		{
			@Override
			protected Object doInBackground( Object... params )
			{
				int count = loadCount;

				while ( count < total && menuListener != null && menuListener.isStillLoading() )
				{
					ArrayList<MenuEntry> menu = new ArrayList<MenuEntry>();
					count += getMoreMenu( total, count, menu );
					menuListener.addMenuEntries( total, count, menu );
				}

				return null;
			}
		}.execute( (Object)null );
	}

	@Override
	public synchronized void eventNotifyReceived( String uuid, long seq, String varName, String value )
	{
		// System.out.println( navigatorId + " - " + menuResults );
		// System.out.println( varName + ": " + value );
		// System.out.println( seq + ": " + varName + " = " + navigatorId );
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "eventNotifyReceived for " + varName + " = " + value + ": uuid=" + uuid );
		if ( recivaRadioService == null || !uuid.equals( recivaRadioService.getSID() ) )
		{
			super.eventNotifyReceived( uuid, seq, varName, value );
			return;
		}
		System.out.println( "eventNotifyReceived " + varName + " navigatorId: " + navigatorId + " menuResults: " + menuResults );

		/*
		 * if ( varName.equals( "Volume" ) ) { intVolume = Integer.parseInt( value ); setVolumeBase( intVolume / 100.0f ); } else
		 */if ( varName.equals( "PowerState" ) )
		{
			boolean power = false;
			if ( value.equals( "On" ) )
				power = true;

			if ( power != powerState )
			{
				powerState = power;

				emitPowerChanged( powerState );
			}
		}
		else if ( varName.equals( "PlaylistLength" ) && !ingorePlaylistChanges )
		{
			playlist.clear();
			playlistCount = Integer.parseInt( value );
			emitPlaylistChanged();
		}
		else if ( varName.equals( "CurrentPlaylistTrackID" ) )
		{
			_currentTrack = Integer.parseInt( value );
			emitTrackNumberChanged( getTrackNumber() );
		}
		else if ( varName.equals( "StationId" ) )
		{
			_stationId = Integer.parseInt( value );
			emitTrackNumberChanged( getTrackNumber() );
		}
		else if ( varName.equals( "PlaybackXML" ) )
		{
			lastPlaybackXMLString = value;
			emitRadioStationChanged( value );
		}
		else if ( varName.equals( "NavigatorStatusXML" ) && navigatorId != null )
		{
			try
			{
				Node statusDoc = UPnP.getXMLParser().parse( value );
				for ( Node navigator : findNodes( statusDoc.getNode( "navigators" ), "navigator" ) )
				{
					String thisid = navigator.getAttributeValue( "id" );

					if ( !thisid.equals( navigatorId ) )
						continue;

					System.out.println( "NavigatorStatusXML :" + navigatorId + navigator.getNodeValue( "last-message" ) );
					String status = navigator.getNodeValue( "status" );
					if ( status != null )
					{
					}
				}
			}
			catch ( Exception e )
			{
				e.printStackTrace();
			}
		}
		else if ( varName.equals( "MenuXML" ) && navigatorId != null && menuResults != null )
		{

			resultsLock.lock();
			if ( ignoreMenuResult == true )
			{
				ignoreMenuResult = false;
				System.out.println( "Skipping MenuXML" );
				resultsLock.unlock();
				return;
			}
			try
			{
				Node menuDoc = UPnP.getXMLParser().parse( value );
				for ( Node navigator : findNodes( menuDoc.getNode( "navigators" ), "navigator" ) )
				{
					String thisid = navigator.getAttributeValue( "id" );

					System.out.println( "MenuXML: " + thisid + " == " + navigatorId );

					if ( !thisid.equals( navigatorId ) )
						continue;

					// System.out.println( navigator );

					int total = 0;
					String totalStr = navigator.getNode( "menu" ).getNode( "items" ).getAttributeValue( "count" );
					if ( totalStr != null )
						total = Integer.parseInt( totalStr );

					ArrayList<MenuEntry> subResults = new ArrayList<MenuEntry>();
					int count = processMenuXML( navigator, subResults );
					if ( menuListener != null )
						menuListener.addMenuEntries( total, count, subResults );
					menuResults.addAll( subResults );

					loadMenuInBackground( total, count );

					if ( count != 0 )
					{
						System.out.println( "resultsCondition.signal" );
						resultsCondition.signal();
						// if ( deferredId != null )
						// {
						// resultsLock.unlock();
						// Thread.sleep( 10 );
						// resultsLock.lock();
						// resultsCondition.signal();
						// }
					}
				}
			}
			catch ( Exception e )
			{
				e.printStackTrace();
			}

			resultsLock.unlock();
		}
		else if ( varName.equals( "DeferredXML" ) && navigatorId != null && deferredId != null )
		{
			resultsLock.lock();
			boolean found = false;

			try
			{
				Node deferredDoc = UPnP.getXMLParser().parse( value );
				for ( Node deferred : findNodes( deferredDoc.getNode( "deferred-list" ), "deferred" ) )
				{
					String thisnavigator = deferred.getAttributeValue( "navigator" );
					System.out.println( "DeferredXML for thisnavigator " + thisnavigator + "navigatorId " + navigatorId );

					if ( thisnavigator.equals( navigatorId ) )
					{
						found = true;
						break;
					}
					continue;

					// attr = ixmlElement_getAttributeNode( (IXML_Element*)deferred, "id" );
					// const char *thisid = ixmlNode_getNodeValue( &attr->n );
					// if ( thisid && strcmp( thisid, [deferredId UTF8String] ) ) continue;
					//
					// found = true;
				}
			}
			catch ( Exception e )
			{
				e.printStackTrace();
			}

			if ( !found )
			{
				System.out.println( "Got _NO_ defferedXML for " + deferredId );
				// resultsCondition.signal();

			}
			else
			{
				System.out.println( "Got defferedXML for " + deferredId );
				// resultsCondition.signal();
			}

			resultsLock.unlock();
		}
	}

	public static interface RecivaRendererListener extends RendererListener
	{
		void radioStationChanged( String playbackXMLString );

		void emitPowerChanged( boolean powerState );
	}

	private void emitRadioStationChanged( String playbackXMLString )
	{
		for ( RecivaRendererListener listener : listeners )
			listener.radioStationChanged( playbackXMLString );
	}

	private void emitPowerChanged( boolean powerState )
	{
		for ( RecivaRendererListener listener : listeners )
			listener.emitPowerChanged( powerState );
	}

	List<String> alarmTypes = Collections.EMPTY_LIST;

	public List<String> getAlarmTypes()
	{
		return alarmTypes;
	}

	@Override
	public void updateServices( Device dev )
	{
		super.updateServices( dev );

		recivaRadioService = dev.getService( "urn:reciva-com:service:RecivaRadio:0.0" );
		recivaSimpleRemoteService = dev.getService( "urn:reciva-com:service:RecivaSimpleRemote:1" );

		try
		{
			StateVariable volume = renderingControlService.getStateVariable( "Volume" );
			if ( volume != null && volume.hasAllowedValueRange() )
			{
				AllowedValueRange range = volume.getAllowedValueRange();
				maxVolume = Integer.parseInt( range.getMaximum() );
			}
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}

		try
		{
			alarmTypes = new ArrayList<String>();
			StateVariable alarmType = recivaRadioService.getStateVariable( "AlarmType" );
			if ( alarmType != null && alarmType.hasAllowedValueList() )
			{
				AllowedValueList typeList = alarmType.getAllowedValueList();
				for ( AllowedValue type : (Vector<AllowedValue>)typeList )
					alarmTypes.add( type.getValue() );
			}
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}
	}

	public static RecivaRenderer createDevice( Device dev )
	{
		Service recivaRadioService = dev.getService( "urn:reciva-com:service:RecivaRadio:0.0" );
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "creating Device1 = " + dev.getModelNumber() );
		if ( recivaRadioService != null )
		{
			RecivaRenderer renderer = new RecivaRenderer();
			renderer.updateServices( dev );

			try
			{
				if ( !dev.getIconList().isEmpty() )
					renderer.setIconURL( dev.getAbsoluteURL( dev.getIcon( 0 ).getURL() ) );

				renderer.WDTVLive = false;
			}
			catch ( Exception e )
			{
				e.printStackTrace();
			}

			try
			{
				StateVariable volume = renderer.renderingControlService.getStateVariable( "Volume" );
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
			return renderer;
		}

		return null;
	}

	public void addRecivaRendererListener( RecivaRendererListener listener )
	{
		super.addRendererListener( listener );
		listeners.add( listener );
	}

	public void removeRecivaRendererListener( RecivaRendererListener listener )
	{
		super.removeRendererListener( listener );
		listeners.add( listener );
	}

	private void pandoraUpDown( boolean up )
	{
		List<MenuEntry> menu = new ArrayList<MenuEntry>();

		pushNavigator();

		enterReplyMenu( menu );

		for ( MenuEntry entry : menu )
		{
			if ( "Give Feedback".equals( entry.title ) )
			{
				int num = entry.number;
				menu.clear();
				getMenu( num, menu, null );
				break;
			}
		}

		String updown = up ? "Thumbs Up" : "Thumbs Down";
		for ( MenuEntry entry : menu )
		{
			if ( updown.equals( entry.title ) )
			{
				getMenu( entry.number, null, null );
				break;
			}
		}

		popNavigator();
	}

	public void pandoraThumbsDown()
	{
		pandoraUpDown( false );
	}

	public void pandoraThumbsUp()
	{
		pandoraUpDown( true );
	}

	public void pandoraNext()
	{
		List<MenuEntry> menu = new ArrayList<MenuEntry>();

		pushNavigator();

		enterReplyMenu( menu );

		for ( MenuEntry entry : menu )
		{
			if ( "Skip".equals( entry.title ) )
			{
				getMenu( entry.number, null, null );
				break;
			}
		}

		popNavigator();
	}
}
