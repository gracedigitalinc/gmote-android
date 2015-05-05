package com.plugplayer.plugplayer.upnp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import org.cybergarage.http.HTTPRequest;
import org.cybergarage.http.HTTPResponse;
import org.cybergarage.http.HTTPServer;
import org.cybergarage.http.HTTPStatus;
import org.cybergarage.upnp.Action;
import org.cybergarage.upnp.Device;
import org.cybergarage.upnp.Service;
import org.cybergarage.upnp.ServiceStateTable;
import org.cybergarage.upnp.StateVariable;
import org.cybergarage.upnp.UPnP;
import org.cybergarage.upnp.UPnPStatus;
import org.cybergarage.upnp.control.ActionListener;
import org.cybergarage.upnp.control.QueryListener;
import org.cybergarage.xml.Node;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.Settings.Secure;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.android.imagedownloader.ImageDownloader;
import com.plugplayer.plugplayer.R;
import com.plugplayer.plugplayer.activities.MainActivity;
import com.plugplayer.plugplayer.activities.SettingsEditor;
import com.plugplayer.plugplayer.activities.SlideShowViewer;
import com.plugplayer.plugplayer.activities.SlideShowViewer.TimerListener;
import com.plugplayer.plugplayer.activities.VideoViewer;
import com.plugplayer.plugplayer.upnp.Item.ContentType;
import com.plugplayer.plugplayer.utils.StateList;
import com.plugplayer.plugplayer.utils.StateMap;

public class AndroidRenderer extends Renderer implements OnErrorListener, OnBufferingUpdateListener, OnCompletionListener, OnPreparedListener, ActionListener,
		QueryListener, TimerListener
{
	public static final String STATENAME = "AndroidRenderer";

	private static final String AVTRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1";
	private static final String RENDERINGCONTROL = "urn:schemas-upnp-org:service:RenderingControl:1";
	private static final String CONNECTIONMANAGER = "urn:schemas-upnp-org:service:ConnectionManager:1";

	private static final String PLAYLIST = "urn:av-openhome-org:service:Playlist:1";
	private static final String TIME = "urn:av-openhome-org:service:Time:1";

	private final MediaPlayer mp;
	protected static Device rendererDevice = null;
	private boolean allowControl = false;

	public boolean getAllowControl()
	{
		return allowControl;
	}

	public void setAllowControl( boolean allowControl )
	{
		this.allowControl = allowControl;
	}

	@Override
	public String getLocation()
	{
		if ( AndroidRenderer.rendererDevice == null || AndroidRenderer.rendererDevice.getHTTPServerList().size() == 0 )
			return "";
		HTTPServer s = AndroidRenderer.rendererDevice.getHTTPServerList().getHTTPServer( 0 );
		return "http://" + s.getBindAddress() + ":" + s.getBindPort() + "/RendererDescription.xml";
	}

	@Override
	public boolean isAlive()
	{
		return true;
	}

	@Override
	public boolean updateAlive()
	{
		return false;
	}

	// This is used for sync blocks instead of mp so that we don't mess up mp's internal locking
	private final Object mplock;

	public AndroidRenderer()
	{
		setOriginalName( Build.MODEL );

		mplock = new Object();

		mp = new MediaPlayer();
		mp.setOnErrorListener( this );
		mp.setOnBufferingUpdateListener( this );
		mp.setOnCompletionListener( this );
		mp.setOnPreparedListener( this );
		mp.setAudioStreamType( AudioManager.STREAM_MUSIC );

		updateRendererFiles();
	}

	@Override
	public void setNameOverride( String nameOverride )
	{
		super.setNameOverride( nameOverride );

		final SharedPreferences preferences = MainActivity.me.getSharedPreferences( "lastVersion", Activity.MODE_PRIVATE );
		preferences.edit().putString( "lastVersion", "" ).commit();

		updateRendererFiles();

		if ( getActive() )
		{
			setActive( false, false );
			setActive( true, false );
		}
	}

	private void updateRendererFiles()
	{
		File webDir = new File( MainActivity.me.getFilesDir() + "/web" );

		String version = MainActivity.appVersion;

		final SharedPreferences preferences = MainActivity.me.getSharedPreferences( "lastVersion", Activity.MODE_PRIVATE );

		String lastVersion = preferences.getString( "lastVersion", "" );

		if ( !version.equals( lastVersion ) )
		{
			preferences.edit().putString( "lastVersion", version ).commit();

			if ( !webDir.exists() )
				webDir.mkdir();

			File descriptionFile = new File( webDir + "/RendererDescription.xml" );

			String deviceId = Secure.getString( MainActivity.me.getContentResolver(), Secure.ANDROID_ID ) + "_uuid";
			String uuid = "uuid:" + new UUID( deviceId.hashCode(), "PlugPlayer_Renderer".hashCode() ).toString();
			// String uuid = "uuid:" + UUID.randomUUID().toString();
			if ( descriptionFile.exists() )
			{
				try
				{
					Node desc = UPnP.getXMLParser().parse( descriptionFile );
					uuid = desc.getNode( "device" ).getNodeValue( "UDN" );
				}
				catch ( Exception e )
				{
					e.printStackTrace();
				}
			}

			setUDN( uuid );

			String filenames[] = { "RendererDescription.xml", "AVTransport.xml", "RenderingControl.xml", "ConnectionManager.xml", "Playlist.xml", "Time.xml" };
			for ( String filename : filenames )
			{
				try
				{
					InputStream is = MainActivity.me.getAssets().open( "web/" + filename );
					BufferedReader r = new BufferedReader( new InputStreamReader( is ) );

					OutputStream os = new FileOutputStream( new File( webDir + "/" + filename ) );
					BufferedWriter w = new BufferedWriter( new OutputStreamWriter( os ) );

					String line = null;
					while ( (line = r.readLine()) != null )
					{
						line = line.replace( "$REPLACEME_NAME$", getName() );
						line = line.replace( "$REPLACEME_VERSION$", version );
						line = line.replace( "$REPLACEME_UDN$", uuid );

						w.write( line );
						w.newLine();
					}

					w.flush();

					os.close();
					is.close();
				}
				catch ( IOException e )
				{
					e.printStackTrace();
				}
			}
		}
	}

	@Override
	public void toState( StateMap state )
	{
		super.toState( state );

		state.setName( STATENAME );

		StateList playlistState = new StateList();
		for ( Item item : playlist )
		{
			StateMap itemState = new StateMap();
			item.toState( itemState );
			playlistState.add( itemState );
		}
		state.setList( "playlist", playlistState );

		state.setValue( "allowControl", allowControl );
	}

	@Override
	public void fromState( StateMap state )
	{
		super.fromState( state );

		StateList playlistState = state.getList( "playlist" );
		for ( StateMap itemState : playlistState )
		{
			Item item = Item.createFromState( itemState );
			item.setLinnId( ++nextLinnId );
			playlist.add( item );
		}

		allowControl = state.getValue( "allowControl", false );
	}

	public static AndroidRenderer createFromState( StateMap state )
	{
		AndroidRenderer rval = new AndroidRenderer();
		rval.fromState( state );
		return rval;
	}

	@Override
	public void updateServices( Device dev )
	{
	}

	@Override
	public void loadIcon( ImageView imageView )
	{
		imageView.setImageResource( R.drawable.icon );
	}

	// XXX No doubt this is not correct for Android; we may have to build this dynamically based on the device
	// static final String protocolInfo =
	// "http-get:*:audio/flac:*,http-get:*:application/ogg:*,http-get:*:audio/ogg:*,http-get:*:audio/vorbis:*,http-get:*:audio/mp4:*,http-get:*:audio/x-m4a:*,http-get:*:audio/x-m4b:*,http-get:*:audio/x-m4p:*,http-get:*:audio/mpeg:*,http-get:*:audio/mpeg3:*,http-get:*:audio/mpg:*,http-get:*:audio/x-mp3:*,http-get:*:audio/x-mpeg3:*,http-get:*:audio/x-mpeg:*,http-get:*:audio/x-mpg:*,http-get:*:audio/aac:*,http-get:*:audio/x-aac:*,http-get:*:audio/aiff:*,http-get:*:audio/x-aiff:*,http-get:*:audio/aifc:*,http-get:*:audio/x-aifc:*,http-get:*:audio/amr:*,http-get:*:audio/wav:*,http-get:*:audio/x-wav:*,http-get:*:audio/x-caf:*,http-get:*:video/quicktime:*,http-get:*:video/mp4:*,http-get:*:video/mpeg:*,http-get:*:video/3gpp:*,http-get:*:video/x-m4v:*,http-get:*:image/tiff:*,http-get:*:image/jpeg:*,http-get:*:image/gif:*,http-get:*:image/png:*,http-get:*:image/bmp:*,http-get:*:image/x-icon:*,http-get:*:image/x-win-bitmap:*,http-get:*:image/x-xbitmap:*";
	// static final String protocolInfo =
	// "http-get:*:application/ogg:*,http-get:*:audio/ogg:*,http-get:*:audio/vorbis:*,http-get:*:audio/mp4:*,http-get:*:audio/x-m4a:*,http-get:*:audio/x-m4b:*,http-get:*:audio/x-m4p:*,http-get:*:audio/mpeg:*,http-get:*:audio/mpeg3:*,http-get:*:audio/mpg:*,http-get:*:audio/x-mp3:*,http-get:*:audio/x-mpeg3:*,http-get:*:audio/x-mpeg:*,http-get:*:audio/x-mpg:*,http-get:*:audio/aac:*,http-get:*:audio/x-aac:*,http-get:*:audio/aiff:*,http-get:*:audio/x-aiff:*,http-get:*:audio/aifc:*,http-get:*:audio/x-aifc:*,http-get:*:audio/amr:*,http-get:*:audio/wav:*,http-get:*:audio/x-wav:*,http-get:*:audio/x-caf:*,http-get:*:video/quicktime:*,http-get:*:video/mp4:*,http-get:*:video/mpeg:*,http-get:*:video/3gpp:*,http-get:*:video/x-m4v:*,http-get:*:image/tiff:*,http-get:*:image/jpeg:*,http-get:*:image/gif:*,http-get:*:image/png:*,http-get:*:image/bmp:*,http-get:*:image/x-icon:*,http-get:*:image/x-win-bitmap:*,http-get:*:image/x-xbitmap:*";
	static final String protocolInfo = "http-get:*:application/ogg:*,http-get:*:audio/ogg:*,http-get:*:audio/vorbis:*,http-get:*:audio/mp4:*,http-get:*:audio/x-m4a:*,http-get:*:audio/x-m4b:*,http-get:*:audio/x-m4p:*,http-get:*:audio/mpeg:*,http-get:*:audio/mpeg3:*,http-get:*:audio/mpg:*,http-get:*:audio/x-mp3:*,http-get:*:audio/x-mpeg3:*,http-get:*:audio/x-mpeg:*,http-get:*:audio/x-mpg:*,http-get:*:audio/aac:*,http-get:*:audio/x-aac:*,http-get:*:audio/aiff:*,http-get:*:audio/x-aiff:*,http-get:*:audio/aifc:*,http-get:*:audio/x-aifc:*,http-get:*:audio/amr:*,http-get:*:audio/wav:*,http-get:*:audio/x-wav:*,http-get:*:audio/x-caf:*,http-get:*:video/quicktime:*,http-get:*:video/mp4:*,http-get:*:video/mpeg:*,http-get:*:video/3gpp:*,http-get:*:video/x-m4v:*,http-get:*:image/tiff:*,http-get:*:image/jpeg:*,http-get:*:image/gif:*,http-get:*:image/png:*,http-get:*:image/bmp:*,http-get:*:image/x-icon:*,http-get:*:image/x-win-bitmap:*,http-get:*:image/x-xbitmap:*"
			+
			// Additional video files: MTS, M2TS, TS, MKV, AVI, WMV, ASF, VOB, FLV, DAT, RM, RMVB
			",http-get:*:video/avchd:*,http-get:*:video/MP2T:*,http-get:*:video/x-matroska:*,http-get:*:video/x-msvideo:*,http-get:*:video/x-ms-wmv:*,http-get:*:video/x-ms-asf:*,http-get:*:video/dvd:*,http-get:*:video/x-flv:*,http-get:*:video/vnd.rn-realvideo:*,http-get:*:application/vnd.rn-realmedia-vbr:*"
			+
			// Additional audio files: WMA, APE, AMR, MIDI/MID
			",http-get:*:audio/x-ms-wma:*,http-get:*:audio/ape:*,http-get:*:audio/amr:*,http-get:*:application/x-midi:*";

	@Override
	public void setActive( boolean active, boolean hardStop )
	{
		if ( active == getActive() )
			return;

		super.setActive( active, hardStop );

		if ( active )
		{
			if ( allowControl )
			{
				rendererDevice = new Device()
				{
					@Override
					public void httpRequestRecieved( HTTPRequest httpReq )
					{
						if ( !AndroidRenderer.this.httpRequestRecieved( httpReq ) )
							super.httpRequestRecieved( httpReq );
					}
				};
				try
				{
					File webDir = new File( MainActivity.me.getFilesDir() + "/web" );

					rendererDevice.loadDescription( new File( webDir + "/RendererDescription.xml" ) );

					Service avtransportService = rendererDevice.getService( AVTRANSPORT );
					avtransportService.loadSCPD( new File( webDir + "/AVTransport.xml" ) );

					Service renderingControlService = rendererDevice.getService( RENDERINGCONTROL );
					renderingControlService.loadSCPD( new File( webDir + "/RenderingControl.xml" ) );

					Service connectionManagerService = rendererDevice.getService( CONNECTIONMANAGER );
					connectionManagerService.loadSCPD( new File( webDir + "/ConnectionManager.xml" ) );

					Service playlistService = rendererDevice.getService( PLAYLIST );
					playlistService.loadSCPD( new File( webDir + "/Playlist.xml" ) );

					Service timeService = rendererDevice.getService( TIME );
					timeService.loadSCPD( new File( webDir + "/Time.xml" ) );

					// INITIALIZE STATE VARIABLES HERE
					updateVariable( "Volume", "" + (int)(getVolume() * 100) );
					updateVariable( "Mute", "" + (isMute() ? 1 : 0) );
					updateVariable( "CurrentPlayMode", getPlayMode().upnpValue() );
					updateVariable( "TransportState", getPlayState().upnpValue() );
					updateVariable( "SourceProtocolInfo", "" );
					updateVariable( "SinkProtocolInfo", protocolInfo );
					updateVariable( "CurrentConnectionIDs", "0" );
					updateVariable( "CurrentTransportActions", "Play,Pause,Stop,Next,Previous,Seek" );

					// updateVariable( "Repeat", getPlayMode() == PlayMode.RepeatAll ? "true" : "false" );
					// updateVariable( "Shuffle", getPlayMode() == PlayMode.Shuffle ? "true" : "false" );
					updateVariable( "Id", "0" );
					updateVariable( "IdArray", generateIdArray() );

					rendererDevice.setDescriptionURI( "/RendererDescription.xml" );
					rendererDevice.setActionListener( this );
					rendererDevice.setQueryListener( this );
					rendererDevice.start();
				}
				catch ( Exception e )
				{
					e.printStackTrace();
				}
			}
		}
		else
		{
			if ( rendererDevice != null )
			{
				rendererDevice.stop();
				rendererDevice = null;
			}
		}
	}

	@Override
	protected void updateCurrentTimeStamp()
	{
		// Might as well make sure our volume is correct while we're at it.
		if ( !isMute() )
			getVolume();

		Item entry = getPlaylistEntry( getTrackNumber() );

		if ( entry == null )
			return;

		if ( entry.getContentType() == ContentType.Audio )
		{
			if ( mp.isPlaying() )
			{
				int totalSecs = (int)(mp.getDuration() / 1000.0);
				AndroidRenderer.this.setTotalSeconds( totalSecs );

				int secs = (int)(mp.getCurrentPosition() / 1000.0);
				AndroidRenderer.this.setCurrentSeconds( secs );
			}
		}
		else if ( entry.getContentType() == ContentType.Video )
		{
			if ( VideoViewer.isPlaying() )
			{
				int totalSecs = VideoViewer.getTotalSeconds();
				AndroidRenderer.this.setTotalSeconds( totalSecs );

				int secs = VideoViewer.getCurrentSeconds();
				AndroidRenderer.this.setCurrentSeconds( secs );
			}
		}
		else if ( entry.getContentType() == ContentType.Image )
		{
			int totalSecs = SlideShowViewer.getTotalSeconds();
			AndroidRenderer.this.setTotalSeconds( totalSecs );

			int secs = SlideShowViewer.getCurrentSeconds();
			AndroidRenderer.this.setCurrentSeconds( secs );
		}
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
			currentTrack = -1;

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

		if ( index == currentTrack )
			setTrackNumber( -1 );

		setTrackNumber( index );
	}

	public void stopAndResetAudioPlayer()
	{
		synchronized ( mplock )
		{
			// Log.i( "TEST", "TEST: stopAndResetAudioPlayer" );

			if ( preparedState != PrepareState.Not_Prepared )
			{
				mp.stop();
				mp.reset();
				preparedState = PrepareState.Not_Prepared;
			}
		}
	}

	private enum PrepareState
	{
		Not_Prepared, Preparing, Prepared
	};

	private PrepareState preparedState = PrepareState.Not_Prepared;

	public void playAudioEntry( Item entry )
	{
		synchronized ( mplock )
		{
			try
			{
				String urlString = entry.getResourceURL();
				URL url = new URL( urlString );
				String userInfo = url.getUserInfo();

				if ( userInfo == null )
				{
					if ( urlString.startsWith( "file://" ) )
						mp.setDataSource( Uri.decode( urlString.replace( "file://", "" ) ) );
					else if ( urlString.startsWith( "file:" ) )
						mp.setDataSource( Uri.decode( urlString.replace( "file:", "" ) ) );
					// else if ( entry.getMimeType().equals( "audio/x-wav" ) )
					// mp.setWAVDataSource( urlString );
					else
						mp.setDataSource( urlString );
				}
				else
				{
					File mediaFile = entry.copyToTmpFile();
					FileInputStream fis = new FileInputStream( mediaFile );
					mp.setDataSource( fis.getFD() );
				}
				mp.prepareAsync();
				preparedState = PrepareState.Preparing;
			}
			catch ( Exception e )
			{
				e.printStackTrace();
			}
		}
	}

	@Override
	public void play()
	{
		// Log.i( "TEST", "TEST: play" );

		Item entry = getPlaylistEntry( getTrackNumber() );

		if ( entry == null )
			return;

		if ( entry.getContentType() == ContentType.Audio )
		{
			final SharedPreferences preferences = MainActivity.me.getSharedPreferences( SettingsEditor.ADVANCED_SETTINGS, Activity.MODE_PRIVATE );
			if ( preferences.getBoolean( SettingsEditor.INTERNAL_AUDIO, true ) )
			{
				synchronized ( mplock )
				{
					if ( preparedState == PrepareState.Prepared )
						mp.start();
					else if ( preparedState == PrepareState.Not_Prepared )
					{
						stopAndResetAudioPlayer();
						playAudioEntry( entry );
					}
				}
			}
		}
		else if ( entry.getContentType() == ContentType.Video )
		{
			final SharedPreferences preferences = MainActivity.me.getSharedPreferences( SettingsEditor.ADVANCED_SETTINGS, Activity.MODE_PRIVATE );
			if ( preferences.getBoolean( SettingsEditor.INTERNAL_VIDEO, true ) )
			{
				if ( !VideoViewer.isShown() )
				{
					VideoViewer.show();
					VideoViewer.setURI( entry.getResourceURL() );
					VideoViewer.setNextPrev( this.hasNext() ? videoNextListener : null, this.hasPrev() ? videoPrevListener : null );
				}

				VideoViewer.play();
			}
		}
		else if ( entry.getContentType() == ContentType.Image )
		{
			final SharedPreferences preferences = MainActivity.me.getSharedPreferences( SettingsEditor.ADVANCED_SETTINGS, Activity.MODE_PRIVATE );
			if ( preferences.getBoolean( SettingsEditor.INTERNAL_IMAGE, true ) )
			{
				if ( !SlideShowViewer.isShown() )
				{
					SlideShowViewer.setURI( entry.getMaxResResourceURL() );
					SlideShowViewer.show();
				}

				SlideShowViewer.play();
			}
		}

		setPlayState( PlayState.Playing );
	}

	@Override
	public void pause()
	{
		Item entry = getPlaylistEntry( getTrackNumber() );

		if ( entry == null )
			return;

		if ( entry.getContentType() == ContentType.Audio )
		{
			synchronized ( mplock )
			{
				if ( preparedState == PrepareState.Prepared )
					mp.pause();
			}
		}
		else if ( entry.getContentType() == ContentType.Video )
		{
			final SharedPreferences preferences = MainActivity.me.getSharedPreferences( SettingsEditor.ADVANCED_SETTINGS, Activity.MODE_PRIVATE );
			if ( preferences.getBoolean( SettingsEditor.INTERNAL_VIDEO, true ) )
				VideoViewer.pause();
		}
		else if ( entry.getContentType() == ContentType.Image )
		{
			final SharedPreferences preferences = MainActivity.me.getSharedPreferences( SettingsEditor.ADVANCED_SETTINGS, Activity.MODE_PRIVATE );
			if ( preferences.getBoolean( SettingsEditor.INTERNAL_IMAGE, true ) )
				SlideShowViewer.pause();
		}

		setPlayState( PlayState.Paused );
	}

	@Override
	public void stop()
	{
		// Log.i( "TEST", "TEST: stop" );

		Item entry = getPlaylistEntry( getTrackNumber() );

		if ( entry == null )
			return;

		if ( entry.getContentType() == ContentType.Audio )
		{
			stopAndResetAudioPlayer();
		}
		else if ( entry.getContentType() == ContentType.Video )
		{
			VideoViewer.hide();
		}
		else if ( entry.getContentType() == ContentType.Image )
		{
			SlideShowViewer.hide();
		}

		if ( getPlayState() != PlayState.Stopped )
			setPlayState( PlayState.Stopped );
	}

	PlayState playState = PlayState.Stopped;

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

		updateVariable( "TransportState", getPlayState().upnpValue() );
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

		updateVariable( "CurrentPlayMode", getPlayMode().upnpValue() );
		// updateVariable( "Repeat", getPlayMode() == PlayMode.RepeatAll ? "true" : "false" );
		// updateVariable( "Shuffle", getPlayMode() == PlayMode.Shuffle ? "true" : "false" );
	}

	@Override
	public PlayMode getPlayMode()
	{
		return playMode;
	}

	protected int currentTrack = -1;

	@Override
	public void setTrackNumber( int newTrackNumber )
	{
		// Log.i( "TEST", "TEST: setTrackNumber=" + newTrackNumber );

		if ( currentTrack == newTrackNumber || newTrackNumber < -1 || newTrackNumber >= getPlaylistEntryCount() )
			return;

		currentTrack = newTrackNumber;

		emitTrackNumberChanged( newTrackNumber );

		Item entry = getPlaylistEntry( getTrackNumber() );

		setCurrentSeconds( 0 );

		if ( entry == null || entry.getResourceURL() == null )
		{
			setTotalSeconds( 0 );
			return;
		}

		setTotalSeconds( (int)entry.getTotalSeconds() );

		final SharedPreferences preferences = MainActivity.me.getSharedPreferences( SettingsEditor.ADVANCED_SETTINGS, Activity.MODE_PRIVATE );

		if ( entry.getContentType() == ContentType.Image )
		{
			VideoViewer.hide();

			stopAndResetAudioPlayer();

			if ( !preferences.getBoolean( SettingsEditor.INTERNAL_IMAGE, true ) )
			{
				try
				{
					Intent i = new Intent( Intent.ACTION_VIEW );
					i.setDataAndType( Uri.parse( entry.getResourceURL() ), entry.getMimeType() );
					MainActivity.me.startActivity( i );
				}
				catch ( android.content.ActivityNotFoundException e )
				{
					Toast.makeText( (Context)MainActivity.me, "No Activity Registered for type '" + entry.getMimeType() + "'", Toast.LENGTH_LONG ).show();
				}
			}
			else
			{
				SlideShowViewer.show();
				SlideShowViewer.setURI( entry.getMaxResResourceURL() );
				SlideShowViewer.setTimerListener( this );
				SlideShowViewer.play();
				if ( hasNext() )
				{
					// Pre-load next image
					Item nextEntry = getPlaylistEntry( nextTrackNumber() );
					if ( nextEntry != null && nextEntry.getContentType() == ContentType.Image )
					{
						ImageDownloader.fullsize.download( nextEntry.getResourceURL(), new ImageView( (Context)MainActivity.me ) );
					}
				}
			}
		}
		else if ( entry.getContentType() == ContentType.Video )
		{
			SlideShowViewer.hide();

			stopAndResetAudioPlayer();

			if ( !preferences.getBoolean( SettingsEditor.INTERNAL_VIDEO, true ) )
			{
				try
				{
					Intent i = new Intent( Intent.ACTION_VIEW );
					i.setDataAndType( Uri.parse( entry.getResourceURL() ), entry.getMimeType() );
					MainActivity.me.startActivity( i );
				}
				catch ( android.content.ActivityNotFoundException e )
				{
					Toast.makeText( (Context)MainActivity.me, "No Activity Registered for type '" + entry.getMimeType() + "'", Toast.LENGTH_LONG ).show();
				}
			}
			else
			{
				VideoViewer.setURI( entry.getResourceURL() );
				VideoViewer.show();
				VideoViewer.setNextPrev( this.hasNext() ? videoNextListener : null, this.hasPrev() ? videoPrevListener : null );
				VideoViewer.setOnCompletionListener( this );
				VideoViewer.setOnErrorListener( this );
				VideoViewer.play();
			}
		}
		else if ( entry.getContentType() == ContentType.Audio )
		{
			SlideShowViewer.hide();
			VideoViewer.hide();

			stopAndResetAudioPlayer();

			try
			{
				if ( !preferences.getBoolean( SettingsEditor.INTERNAL_AUDIO, true ) )
				{
					try
					{
						Intent i = new Intent( Intent.ACTION_VIEW );
						i.setDataAndType( Uri.parse( entry.getResourceURL() ), entry.getMimeType() );
						MainActivity.me.startActivity( i );
					}
					catch ( android.content.ActivityNotFoundException e )
					{
						Toast.makeText( (Context)MainActivity.me, "No Activity Registered for type '" + entry.getMimeType() + "'", Toast.LENGTH_LONG ).show();
					}
				}
				else
				{
					playAudioEntry( entry );
				}
			}
			catch ( IllegalArgumentException e )
			{
				e.printStackTrace();
			}
			catch ( IllegalStateException e )
			{
				e.printStackTrace();
			}
		}
		else if ( entry.getContentType() == ContentType.Application )
		{
			SlideShowViewer.hide();
			VideoViewer.hide();

			stopAndResetAudioPlayer();

			try
			{
				Intent i = new Intent( Intent.ACTION_VIEW );

				if ( entry.getResourceURL().startsWith( "file:" ) )
					i.setDataAndType( Uri.parse( entry.getResourceURL() ), entry.getMimeType() );
				else
				{
					File f = entry.copyToExternalFile();
					i.setDataAndType( Uri.fromFile( f ), entry.getMimeType() );
				}

				MainActivity.me.startActivity( i );
			}
			catch ( android.content.ActivityNotFoundException e )
			{
				Toast.makeText( (Context)MainActivity.me, "No Activity Registered for type '" + entry.getMimeType() + "'", Toast.LENGTH_LONG ).show();
			}
			catch ( NullPointerException e )
			{
				Toast.makeText( (Context)MainActivity.me, "Error copying document to external storage", Toast.LENGTH_LONG ).show();
			}
		}

		updateVariable( "Id", "" + entry.getLinnId() );
	}

	OnClickListener videoPrevListener = new OnClickListener()
	{
		public void onClick( View v )
		{
			prev();
		}
	};

	OnClickListener videoNextListener = new OnClickListener()
	{
		public void onClick( View v )
		{
			next();
		}
	};

	@Override
	public int getTrackNumber()
	{
		return currentTrack;
	}

	private float volume = 0.0f;

	@Override
	public float getVolume()
	{
		AudioManager am = (AudioManager)MainActivity.me.getSystemService( Context.AUDIO_SERVICE );
		float newVolume = am.getStreamVolume( AudioManager.STREAM_MUSIC ) / (float)am.getStreamMaxVolume( AudioManager.STREAM_MUSIC );

		// This will do all the right things if the volume was different than the internal number
		setVolume( newVolume );

		return volume;
	}

	@Override
	public void setVolume( float newVolume )
	{
		if ( volume == newVolume )
			return;

		volume = newVolume;

		if ( isMute() )
			setMute( false );

		AudioManager am = (AudioManager)MainActivity.me.getSystemService( Context.AUDIO_SERVICE );
		am.setStreamVolume( AudioManager.STREAM_MUSIC, (int)(volume * am.getStreamMaxVolume( AudioManager.STREAM_MUSIC )), 0 );

		emitVolumeChanged( volume );

		updateVariable( "Volume", "" + (int)(volume * 100) );
	}

	Timer idArrayTimer = new Timer();
	TimerTask idArrayTimerTask = null;

	protected void updateVariable( final String varName, final String value )
	{
		if ( rendererDevice == null )
			return;

		final Service service;
		String header = null;
		String data = "";
		String footer = null;
		String variable = "LastChange";

		if ( varName.equals( "Volume" ) || varName.equals( "Mute" ) )
		{
			rendererDevice.getStateVariable( varName ).setValue( value );
			service = rendererDevice.getService( RENDERINGCONTROL );
			header = "<Event xmlns=\"urn:schemas-upnp-org:metadata-1-0/RCS\"><InstanceID val=\"0\">";
			footer = "</InstanceID></Event>";
		}
		else if ( varName.equals( "CurrentPlayMode" ) || varName.equals( "TransportState" ) || varName.equals( "CurrentTransportActions" ) )
		{
			rendererDevice.getStateVariable( varName ).setValue( value );
			service = rendererDevice.getService( AVTRANSPORT );
			header = "<Event xmlns=\"urn:schemas-upnp-org:metadata-1-0/AVT\"><InstanceID val=\"0\">";
			footer = "</InstanceID></Event>";
		}
		else if ( varName.equals( "SinkProtocolInfo" ) || varName.equals( "CurrentConnectionIDs" ) )
		{
			rendererDevice.getStateVariable( varName ).setValue( value );
			service = rendererDevice.getService( CONNECTIONMANAGER );
			header = "";
			data = value;
			footer = "";
			variable = varName;
		}
		else
			service = null;

		/* not an "else" to catch TransportState twice */
		if ( varName.equals( "Repeat" ) || varName.equals( "Shuffle" ) || varName.equals( "Id" ) || varName.equals( "IdArray" )
				|| varName.equals( "TransportState" ) )
		{
			final String linnValue;
			if ( varName.equals( "TransportState" ) )
			{
				PlayState state = PlayState.fromUpnpValue( value );

				if ( state == PlayState.Paused )
					linnValue = "Paused";
				else if ( state == PlayState.Playing )
					linnValue = "Playing";
				else
					linnValue = "Stopped";
			}
			else
				linnValue = value;

			// rendererDevice.getStateVariable( varName ).setValue( value );
			final Service finalService = rendererDevice.getService( PLAYLIST );

			if ( varName.equals( "IdArray" ) )
			{
				if ( idArrayTimerTask != null )
					idArrayTimerTask.cancel();

				idArrayTimerTask = new TimerTask()
				{
					@Override
					public void run()
					{
						String idArray = generateIdArray();
						finalService.getStateVariable( varName ).setValue( idArray );
					}
				};

				idArrayTimer.schedule( idArrayTimerTask, 750 );
			}
			else
			{
				new AsyncTask<Object, Object, Object>()
				{
					@Override
					protected Object doInBackground( Object... arg0 )
					{
						// Log.w( "TEST", "Sending '" + varName + "'='" + linnValue + "'" );
						finalService.getStateVariable( varName ).setValue( linnValue );
						return null;
					}
				}.execute( (Object)null );
			}
			// return;
		}

		if ( service == null )
			return;

		if ( data.length() == 0 )
		{
			ServiceStateTable stateTable = service.getServiceStateTable();
			int tableSize = stateTable.size();
			for ( int n = 0; n < tableSize; n++ )
			{
				StateVariable var = stateTable.getStateVariable( n );

				if ( var.getName().equals( "Volume" ) )
					data += "<Volume channel=\"Master\" val=\"" + var.getValue() + "\"/>";
				else if ( var.getName().equals( "Mute" ) )
					data += "<Mute channel=\"Master\" val=\"" + var.getValue() + "\"/>";
				else if ( var.getName().equals( "CurrentPlayMode" ) )
					data += "<CurrentPlayMode val=\"" + var.getValue() + "\"></CurrentPlayMode>";
				else if ( var.getName().equals( "TransportState" ) )
					data += "<TransportState val=\"" + var.getValue() + "\"></TransportState>";
				else if ( var.getName().equals( "CurrentTransportActions" ) )
					data += "<CurrentTransportActions val=\"" + var.getValue() + "\"></CurrentTransportActions>";
			}
		}

		final String stateVariable = variable;
		final String lastChangeValue = header + data + footer;

		// Run this on another thread to break any deadlocks with control points running within their recieving thread
		new AsyncTask<Object, Object, Object>()
		{
			@Override
			protected Object doInBackground( Object... arg0 )
			{
				service.getStateVariable( stateVariable ).setValue( lastChangeValue );
				return null;
			}
		}.execute( (Object)null );
		// System.out.println( header + data + footer );
	}

	@Override
	public void volumeInc()
	{
		AudioManager am = (AudioManager)MainActivity.me.getSystemService( Context.AUDIO_SERVICE );
		int m = am.getStreamMaxVolume( AudioManager.STREAM_MUSIC );
		int v = am.getStreamVolume( AudioManager.STREAM_MUSIC ) + 1;
		if ( v > m )
			v = m;
		am.setStreamVolume( AudioManager.STREAM_MUSIC, v, 0 );
		volume = v / (float)m;
		emitVolumeChanged( volume );
	}

	@Override
	public void volumeDec()
	{
		AudioManager am = (AudioManager)MainActivity.me.getSystemService( Context.AUDIO_SERVICE );
		int m = am.getStreamMaxVolume( AudioManager.STREAM_MUSIC );
		int v = am.getStreamVolume( AudioManager.STREAM_MUSIC ) - 1;
		if ( v < 0 )
			v = 0;
		am.setStreamVolume( AudioManager.STREAM_MUSIC, v, 0 );
		volume = v / (float)m;
		emitVolumeChanged( volume );
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

		mute = newMute;

		float newVolume = volume;

		if ( mute )
			newVolume = 0;

		AudioManager am = (AudioManager)MainActivity.me.getSystemService( Context.AUDIO_SERVICE );
		am.setStreamVolume( AudioManager.STREAM_MUSIC, (int)(newVolume * am.getStreamMaxVolume( AudioManager.STREAM_MUSIC )), 0 );

		emitVolumeChanged( newVolume );

		updateVariable( "Mute", "" + (mute ? 1 : 0) );
	}

	public final List<Item> playlist = new ArrayList<Item>();

	@Override
	public void emitPlaylistChanged()
	{
		super.emitPlaylistChanged();

		updateVariable( "IdArray", null );
	}

	@Override
	public void insertPlaylistEntry( Item newEntry, int index )
	{
		if ( index < 0 )
			index = 0;

		if ( index > playlist.size() )
			index = playlist.size();

		newEntry.setLinnId( ++nextLinnId );

		playlist.add( index, newEntry );

		if ( index <= currentTrack )
			currentTrack++;

		emitPlaylistChanged();
	}

	@Override
	public void removePlaylistEntry( int index )
	{
		if ( index < 0 || index >= playlist.size() )
			return;

		playlist.remove( index );

		int playingTrack = currentTrack;

		if ( index < playingTrack )
			currentTrack--;

		emitPlaylistChanged();

		if ( index == playingTrack )
		{
			currentTrack = -1;
			setTrackNumber( index );
		}
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
	public boolean supportsSeek()
	{
		return true;
	}

	@Override
	public boolean seekToSecond( int second )
	{
		Item entry = getPlaylistEntry( getTrackNumber() );

		if ( entry == null )
			return true;

		if ( entry.getContentType() == ContentType.Audio )
		{
			synchronized ( mplock )
			{
				if ( preparedState == PrepareState.Prepared )
					mp.seekTo( second * 1000 );
			}
		}
		else if ( entry.getContentType() == ContentType.Video )
		{
			VideoViewer.seekTo( second );
		}

		return true;
	}

	public void onPrepared( MediaPlayer mp )
	{
		synchronized ( mplock )
		{
			// Log.i( "TEST", "TEST: OnPrepared: playing=" + (getPlayState() == PlayState.Playing) );
			preparedState = PrepareState.Prepared;

			if ( getPlayState() == PlayState.Playing )
				mp.start();
		}
	}

	public void onCompletion( MediaPlayer mp )
	{
		// Log.i( "TEST", "TEST: OnCompletion" );
		if ( mp != null )
		{
			synchronized ( mplock )
			{
				mp.reset();
				preparedState = PrepareState.Not_Prepared;
			}
		}

		// If we got an error playing a video, stop right here.
		Item entry = getPlaylistEntry( getTrackNumber() );
		if ( entry == null || (entry.getContentType() == ContentType.Video && getPlayState() != PlayState.Playing) )
			return;

		if ( hasNext() )
		{
			next();

			// If it's audio and we're not playing, we got an error, but we should continue on.
			if ( getPlayState() != PlayState.Playing )
				play();
		}
		else
		{
			stop();
		}
	}

	public void onBufferingUpdate( MediaPlayer mp, int percent )
	{
		// Log.i( "TEST", "TEST: OnBufferingUpdate:" + percent );
	}

	public boolean onError( MediaPlayer mp, int what, int extra )
	{
		// Log.i( "TEST", "TEST: OnError" );
		Item entry = getPlaylistEntry( getTrackNumber() );

		if ( entry == null )
			return false;

		if ( entry.getContentType() == ContentType.Video )
		{
			VideoViewer.hide();

			// force state so we don't emit it so control points don't skip to the next track
			playState = PlayState.Stopped;
			stop();
			emitPlayStateChanged( PlayState.Stopped );

			Toast.makeText( (Context)MainActivity.me, "Error Playing Video '" + entry.getTitle() + "'", Toast.LENGTH_LONG ).show();

			return true;
		}

		stop();

		return false;
	}

	public boolean actionControlReceived( Action action )
	{
		String service = action.getService().getServiceType();

		boolean rval = false;
		if ( service.equals( AVTRANSPORT ) )
			rval = avTransportActionReceived( action );
		else if ( service.equals( RENDERINGCONTROL ) )
			rval = renderingControlActionReceived( action );
		else if ( service.equals( CONNECTIONMANAGER ) )
			rval = connectionManagerActionReceived( action );
		else if ( service.equals( PLAYLIST ) )
			rval = playlistActionReceived( action );
		else if ( service.equals( TIME ) )
			rval = timeActionReceived( action );

		if ( !rval )
		{
			action.setStatus( UPnPStatus.INVALID_ACTION, "" );
			Log.w( MainActivity.appName, "unhandled action '" + action.getName() + "'" );
		}

		return rval;
	}

	long nextLinnId = 0;

	private boolean playlistActionReceived( Action action )
	{
		String name = action.getName();

		if ( name.equals( "Play" ) )
		{
			play();
			return true;
		}
		else if ( name.equals( "Pause" ) )
		{
			pause();
			return true;
		}
		else if ( name.equals( "Stop" ) )
		{
			stop();
			return true;
		}
		else if ( name.equals( "Next" ) )
		{
			if ( hasNext() )
				next();
			return true;
		}
		else if ( name.equals( "Previous" ) )
		{
			if ( hasPrev() )
				prev();
			return true;
		}
		else if ( name.equals( "SetRepeat" ) )
		{
			String repeat = action.getArgumentValue( "Value" );
			setPlayMode( repeat.equals( "true" ) ? PlayMode.RepeatAll : PlayMode.Normal );
			return true;
		}
		else if ( name.equals( "Repeat" ) )
		{
			action.setArgumentValue( "Value", getPlayMode() == PlayMode.RepeatAll ? "true" : "false" );
			return true;
		}
		else if ( name.equals( "SetShuffle" ) )
		{
			String repeat = action.getArgumentValue( "Value" );
			setPlayMode( repeat.equals( "true" ) ? PlayMode.Shuffle : PlayMode.Normal );
			return true;
		}
		else if ( name.equals( "Shuffle" ) )
		{
			action.setArgumentValue( "Value", getPlayMode() == PlayMode.Shuffle ? "true" : "false" );
			return true;
		}
		else if ( name.equals( "SeekSecondAbsolute" ) )
		{
			int seconds = action.getArgumentIntegerValue( "Value" );
			seekToSecond( seconds );
			return true;
		}
		else if ( name.equals( "SeekSecondRelative" ) )
		{
			return false;
		}
		else if ( name.equals( "SeekId" ) )
		{
			return false;
		}
		else if ( name.equals( "SeekIndex" ) )
		{
			final int index = action.getArgumentIntegerValue( "Value" );
			MainActivity.me.getHandler().post( new Runnable()
			{
				public void run()
				{
					if ( currentTrack == index )
						currentTrack = -1;

					setTrackNumber( index );
				}
			} );
			return true;
		}
		else if ( name.equals( "TransportState" ) )
		{
			PlayState state = getPlayState();
			String value = "Stopped";
			if ( state == PlayState.Paused )
				value = "Paused";
			else if ( state == PlayState.Playing )
				value = "Playing";
			action.setArgumentValue( "Value", value );
			return true;
		}
		else if ( name.equals( "Id" ) )
		{
			long rval = 0;
			int index = getTrackNumber();
			if ( index < getPlaylistEntryCount() && index > -1 )
			{
				Item entry = getPlaylistEntry( index );
				if ( entry != null )
					rval = entry.getLinnId();
			}

			action.setArgumentValue( "Value", "" + rval );
			return true;
		}
		else if ( name.equals( "Read" ) )
		{
			long id = Long.parseLong( action.getArgumentValue( "Id" ) );
			for ( int i = 0; i < getPlaylistEntryCount(); ++i )
			{
				Item entry = getPlaylistEntry( i );
				if ( entry.getLinnId() == id )
				{
					action.setArgumentValue( "Uri", entry.getResourceURL() );
					action.setArgumentValue( "Metadata", entry.generateExternalMetadata( AndroidServer.getBaseURL() ) );
					return true;
				}
			}

			return true;
		}
		else if ( name.equals( "ReadList" ) )
		{
			return false;
		}
		else if ( name.equals( "Insert" ) )
		{
			long afterId = Long.parseLong( action.getArgumentValue( "AfterId" ) );
			String metadata = action.getArgumentValue( "Metadata" );
			int index = 0;

			if ( afterId != 0 )
				for ( int i = 0; i < getPlaylistEntryCount(); ++i )
				{
					Item entry = getPlaylistEntry( i );
					if ( entry.getLinnId() == afterId )
					{
						index = i + 1;
						break;
					}
				}

			Item entry = new Item();
			entry.setMetadata( metadata );
			if ( entry.getArtURL() == null && entry.getContentType() == ContentType.Image )
			{
				entry.setArtURL( entry.getResourceURL() );
			}

			insertPlaylistEntry( entry, index );

			action.setArgumentValue( "NewId", "" + entry.getLinnId() );
			return true;
		}
		else if ( name.equals( "DeleteId" ) )
		{
			long id = Long.parseLong( action.getArgumentValue( "Value" ) );
			for ( int i = 0; i < getPlaylistEntryCount(); ++i )
			{
				Item entry = getPlaylistEntry( i );
				if ( entry.getLinnId() == id )
				{
					removePlaylistEntry( i );
					return true;
				}
			}

			return true;
		}
		else if ( name.equals( "DeleteAll" ) )
		{
			removePlaylistEntries( 0, getPlaylistEntryCount() );
			return true;
		}
		else if ( name.equals( "TracksMax" ) )
		{
			return false;
		}
		else if ( name.equals( "IdArray" ) )
		{
			action.setArgumentValue( "Token", 0 );
			action.setArgumentValue( "Array", generateIdArray() );
			return true;
		}
		else if ( name.equals( "IdArrayChanged" ) )
		{
			return false;
		}
		else if ( name.equals( "ProtocolInfo" ) )
		{
			return false;
		}

		return false;
	}

	private String generateIdArray()
	{
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream( bos );

		try
		{
			// synchronized ( playlist )
			{
				for ( Item entry : playlist )
				{
					int id = (int)entry.getLinnId();
					dos.writeInt( id );
				}
			}

			dos.flush();
		}
		catch ( IOException e )
		{
			e.printStackTrace();
		}

		byte bytes[] = bos.toByteArray();
		return android.util.Base64.encodeToString( bytes, android.util.Base64.NO_WRAP );
	}

	private boolean timeActionReceived( Action action )
	{
		String name = action.getName();

		if ( name.equals( "Time" ) )
		{
			action.setArgumentValue( "TrackCount", 0 );
			action.setArgumentValue( "Duration", totalSeconds );
			action.setArgumentValue( "Seconds", currentSeconds );
			return true;
		}

		return false;
	}

	private boolean connectionManagerActionReceived( Action action )
	{
		String name = action.getName();

		if ( name.equals( "GetCurrentConnectionIDs" ) )
		{
			action.setArgumentValue( "ConnectionIDs", 0 );
			return true;
		}
		else if ( name.equals( "GetProtocolInfo" ) )
		{
			action.setArgumentValue( "Source", "" );
			action.setArgumentValue( "Sink", protocolInfo );
			return true;
		}
		else if ( name.equals( "ConnectionComplete" ) )
		{
			return true;
		}
		else if ( name.equals( "GetCurrentConnectionInfo" ) )
		{
			action.setArgumentValue( "RcsID", 0 );
			action.setArgumentValue( "AVTransportID", 0 );
			action.setArgumentValue( "ProtocolInfo", protocolInfo );
			action.setArgumentValue( "PeerConnectionManager", "" );
			action.setArgumentValue( "PeerConnectionID", 0 );
			action.setArgumentValue( "Direction", "Input" );
			action.setArgumentValue( "Status", "OK" );
			return true;
		}

		return false;
	}

	private boolean renderingControlActionReceived( Action action )
	{
		String name = action.getName();

		if ( name.equals( "GetVolume" ) )
		{
			action.setArgumentValue( "CurrentVolume", (int)(100 * getVolume()) );
			return true;
		}
		else if ( name.equals( "SetVolume" ) )
		{
			int newVolume = action.getArgumentIntegerValue( "DesiredVolume" );
			setVolume( newVolume / 100.0f );
			return true;
		}
		else if ( name.equals( "GetMute" ) )
		{
			action.setArgumentValue( "CurrentMute", isMute() ? 1 : 0 );
			return true;
		}
		else if ( name.equals( "SetMute" ) )
		{
			String newMute = action.getArgumentValue( "DesiredMute" );
			setMute( newMute.equalsIgnoreCase( "true" ) );
			return true;
		}

		return false;
	}

	private String getTrackDuration()
	{
		int dursecs = getTotalSeconds();
		int hr = dursecs / 3600;
		int mn = (dursecs - (hr * 3600)) / 60;
		int sec = dursecs - (hr * 3600) - (mn * 60);

		return String.format( "%02d:%02d:%02d", hr, mn, sec );
	}

	private String getTime()
	{
		int dursecs = getCurrentSeconds();
		int hr = dursecs / 3600;
		int mn = (dursecs - (hr * 3600)) / 60;
		int sec = dursecs - (hr * 3600) - (mn * 60);

		return String.format( "%02d:%02d:%02d", hr, mn, sec );
	}

	private boolean avTransportActionReceived( Action action )
	{
		String name = action.getName();

		if ( name.equals( "Seek" ) )
		{
			String targetTime = action.getArgumentValue( "Target" );
			int targetSecs = secsFromString( targetTime );

			if ( targetSecs < 0 || targetSecs > totalSeconds )
				Log.w( MainActivity.appName, "Bad time for seek '" + targetTime + "': 0<" + targetSecs + "<" + totalSeconds );
			else
				seekToSecond( targetSecs );

			return true;
		}
		else if ( name.equals( "Stop" ) )
		{
			stop();
			return true;
		}
		else if ( name.equals( "Play" ) )
		{
			play();
			return true;
		}
		else if ( name.equals( "Pause" ) )
		{
			pause();
			return true;
		}
		else if ( name.equals( "SetAVTransportURI" ) )
		{
			pause();

			String metadata = action.getArgumentValue( "CurrentURIMetaData" );
			if ( metadata != null )
			{
				final Item entry = new Item();
				entry.setMetadata( metadata );

				MainActivity.me.getHandler().post( new Runnable()
				{
					public void run()
					{
						removePlaylistEntries( 0, getPlaylistEntryCount() );
						insertPlaylistEntry( entry, 0 );
						setTrackNumber( 0 );
					}
				} );
			}

			return true;
		}
		// else if ( name.equals( "SetNextAVTransportURI" ) )
		// {
		// const char *metadata = GetFirstDocumentItem( event->ActionRequest, "CurrentURIMetaData" );
		// if ( metadata )
		// {
		// Item *entry = [[[Item alloc] init] autorelease];
		// entry.metadata = strdup( metadata );
		//
		// [self insertPlaylistEntry:entry at:[self getPlaylistEntryCount]];
		// }
		//
		// event->ActionResult = ixmlParseBuffer(
		// "<u:SetNextAVTransportURIResponse xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\"></u:SetNextAVTransportURIResponse>" );
		// event->ErrCode = UPNP_E_SUCCESS;
		// }
		else if ( name.equals( "SetPlayMode" ) )
		{
			String modeString = action.getArgumentValue( "NewPlayMode" );

			if ( modeString != null )
				setPlayMode( PlayMode.fromUpnpValue( modeString ) );

			return true;
		}
		else if ( name.equals( "GetPositionInfo" ) )
		{
			Item item = getPlaylistEntry( getTrackNumber() );

			String url = "";
			String metadata = "";
			if ( item != null )
			{
				metadata = item.generateExternalMetadata( AndroidServer.getBaseURL() );
				url = item.generateExternalURL( AndroidServer.getBaseURL() );
			}

			action.setArgumentValue( "Track", getTrackNumber() );
			action.setArgumentValue( "TrackDuration", getTrackDuration() );
			action.setArgumentValue( "TrackMetaData", metadata );
			action.setArgumentValue( "TrackURI", url );
			action.setArgumentValue( "RelTime", getTime() );
			action.setArgumentValue( "AbsTime", getTime() );
			action.setArgumentValue( "RelCount", getCurrentSeconds() );
			action.setArgumentValue( "AbsCount", getCurrentSeconds() );
			return true;
		}
		else if ( name.equals( "GetTransportSettings" ) )
		{
			action.setArgumentValue( "PlayMode", getPlayMode().upnpValue() );
			action.setArgumentValue( "RecQualityMode", "NOT_IMPLEMENTED" );
			return true;
		}
		else if ( name.equals( "GetTransportInfo" ) )
		{
			action.setArgumentValue( "CurrentTransportState", getPlayState().upnpValue() );
			action.setArgumentValue( "CurrentTransportStatus", "OK" );
			action.setArgumentValue( "CurrentSpeed", 1 );
			return true;
		}
		else if ( name.equals( "GetCurrentTransportActions" ) )
		{
			action.setArgumentValue( "Actions", "Play,Pause,Stop,Next,Previous,Seek" );
			return true;
		}

		return false;
	}

	public boolean queryControlReceived( StateVariable stateVar )
	{
		return false;
	}

	protected boolean httpRequestRecieved( HTTPRequest httpReq )
	{
		if ( httpReq.isGetRequest() == true )
		{
			String uri = httpReq.getURI();

			if ( uri == null )
				return false;

			if ( uri.equals( "/plugicon.png" ) )
			{
				HTTPResponse httpRes = new HTTPResponse();
				httpRes.setContentType( "image/png" );
				httpRes.setStatusCode( HTTPStatus.OK );

				try
				{
					// XXX Hardcoded length because CHUNKED encoding isn't working
					// httpRes.setTransferEncoding( HTTP.CHUNKED );

					int read;
					int size = 0;
					InputStream is = MainActivity.me.getAssets().open( "web/icon.png" );
					while ( (read = is.read( new byte[1024] )) == 1024 )
						size += read;
					size += read;

					httpRes.setContentInputStream( MainActivity.me.getAssets().open( "web/icon.png" ) );
					httpRes.setContentLength( size );
				}
				catch ( IOException e )
				{
					e.printStackTrace();
				}

				httpReq.post( httpRes );
				return true;
			}
		}

		return false;
	}

	public void imageDoneShowing()
	{
		if ( hasNext() )
			next();
		else
			stop();
	}
}
