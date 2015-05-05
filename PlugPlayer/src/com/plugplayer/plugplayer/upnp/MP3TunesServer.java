package com.plugplayer.plugplayer.upnp;

import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

import org.cybergarage.upnp.Device;
import org.cybergarage.upnp.UPnP;
import org.cybergarage.xml.Node;
import org.cybergarage.xml.Parser;

import android.app.Activity;
import android.content.SharedPreferences;
import android.widget.ImageView;

import com.plugplayer.plugplayer.R;
import com.plugplayer.plugplayer.activities.MainActivity;
import com.plugplayer.plugplayer.activities.SettingsEditor;
import com.plugplayer.plugplayer.upnp.Item.ContentType;
import com.plugplayer.plugplayer.utils.StateMap;

public class MP3TunesServer extends Server
{
	public MP3TunesServer()
	{
		setOriginalName( "MP3tunes" );

		final SharedPreferences preferences = MainActivity.me.getSharedPreferences( SettingsEditor.ADVANCED_SETTINGS, Activity.MODE_PRIVATE );

		username = preferences.getString( "MP3TUNES_USERNAME", null );
		password = preferences.getString( "MP3TUNES_PASSWORD", null );
	}

	public static final String STATENAME = "MP3TunesServer";

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

	public static MP3TunesServer createFromState( StateMap state )
	{
		MP3TunesServer rval = new MP3TunesServer();
		rval.fromState( state );
		return rval;
	}

	private static final String ROOT_ID = "0";

	// private static final String AUDIO_ID = "1";
	private static final String VIDEO_ID = "2";

	private static final String ARTISTS_ID = "3";
	private static final String ALBUMS_ID = "4";
	private static final String PLAYLISTS_ID = "5";

	private static final String ARTIST_PREFIX = "artist-";
	private static final String ALBUM_PREFIX = "album-";
	private static final String PLAYLIST_PREFIX = "playlist-";

	// private static final int REQUEST_COUNT = 50;

	private String session_id = null;
	private static final String partner_token = "3437733845";
	private String username = null;
	private String password = null;

	private synchronized void browseDir( Container parent, String search, BrowseResultsListener listener, int max )
	{
		if ( session_id == null )
			login();

		if ( session_id == null )
		{
			emitError( "Error logging into MP3tunes" );

			if ( listener != null )
				listener.onDone();

			password = null;

			if ( PlugPlayerControlPoint.getInstance().getCurrentServer() == this )
				PlugPlayerControlPoint.getInstance().setCurrentServer( null );

			return;
		}

		if ( listener != null && search == null )
		{
			// We copy because parent.getChildren() is used by the background thread
			ArrayList<DirEntry> copy = new ArrayList<DirEntry>();
			copy.addAll( parent.getChildren() );
			listener.onInitialChildren( copy, parent.getTotalCount() );
		}

		int entryCount = parent.getChildCount();
		int requestCount = 25;

		int entriesLeft;

		if ( parent.getTotalCount() == -1 )
			entriesLeft = requestCount;
		else
			entriesLeft = parent.getTotalCount() - entryCount;

		while ( entriesLeft > 0 && (max < 0 || parent.getChildCount() != max) )
		{
			ArrayList<DirEntry> subList = new ArrayList<DirEntry>();

			int numReturned = 0;

			// if ( parent.getObjectID().equals( ROOT_ID ) )
			// {
			// parent.setTotalCount( 2 );
			//
			// Container c = new Container();
			// c.setParent( parent );
			// c.setObjectID( AUDIO_ID );
			// c.setTitle( "Audio" );
			// parent.getChildren().add( c );
			// subList.add( c );
			//
			// c = new Container();
			// c.setParent( parent );
			// c.setObjectID( VIDEO_ID );
			// c.setTitle( "Video" );
			// parent.getChildren().add( c );
			// subList.add( c );
			//
			// numReturned = 2;
			// }
			// else if ( parent.getObjectID().equals( AUDIO_ID ) )
			if ( parent.getObjectID().equals( ROOT_ID ) )
			{
				parent.setTotalCount( 3 );

				Container c = new Container();
				c.setParent( parent );
				c.setObjectID( ARTISTS_ID );
				c.setTitle( "Artists" );
				parent.getChildren().add( c );
				subList.add( c );

				c = new Container();
				c.setParent( parent );
				c.setObjectID( ALBUMS_ID );
				c.setTitle( "Albums" );
				parent.getChildren().add( c );
				subList.add( c );

				c = new Container();
				c.setParent( parent );
				c.setObjectID( PLAYLISTS_ID );
				c.setTitle( "Playlists" );
				parent.getChildren().add( c );
				subList.add( c );

				numReturned = 3;
			}
			else if ( parent.getObjectID().equals( PLAYLISTS_ID ) )
			{
				try
				{
					Parser p = UPnP.getXMLParser();
					Node n = p.parse( new URL( "http://ws.mp3tunes.com/api/v1/lockerData?output=xml&sid=" + session_id + "&partner_token=" + partner_token
							+ "&type=playlist&count=" + requestCount + "&set=" + parent.getChildCount() / 25 ) );

					Node summary = n.getNode( "summary" );
					String totalResults = summary.getNodeValue( "totalResults" );
					parent.setTotalCount( Integer.parseInt( totalResults ) );

					Node artistList = n.getNode( "playlistList" );
					for ( int i = 0; i < artistList.getNNodes(); ++i )
					{
						Node item = artistList.getNode( i );
						String playlistId = item.getNodeValue( "playlistId" );
						String playlistTitle = item.getNodeValue( "playlistTitle" );
						// String albumCount = item.getNodeValue( "albumCount" );

						Container c = new Container();
						c.setParent( parent );
						c.setObjectID( PLAYLIST_PREFIX + playlistId );
						c.setTitle( playlistTitle );
						// c.setTotalCount( Integer.parseInt( albumCount ) );
						parent.getChildren().add( c );
						subList.add( c );
						numReturned++;
					}
				}
				catch ( Exception e )
				{
					e.printStackTrace();
					emitError( "error getting mp3tunes data" );
				}
			}
			else if ( parent.getObjectID().equals( ARTISTS_ID ) )
			{
				try
				{
					Parser p = UPnP.getXMLParser();
					Node n = p.parse( new URL( "http://ws.mp3tunes.com/api/v1/lockerData?output=xml&sid=" + session_id + "&partner_token=" + partner_token
							+ "&type=artist&count=" + requestCount + "&set=" + parent.getChildCount() / 25 ) );

					Node summary = n.getNode( "summary" );
					String totalResults = summary.getNodeValue( "totalResults" );
					parent.setTotalCount( Integer.parseInt( totalResults ) );

					Node artistList = n.getNode( "artistList" );
					for ( int i = 0; i < artistList.getNNodes(); ++i )
					{
						Node item = artistList.getNode( i );
						String artistid = item.getNodeValue( "artistId" );
						String artistname = item.getNodeValue( "artistName" );
						// String albumCount = item.getNodeValue( "albumCount" );

						Container c = new Container();
						c.setParent( parent );
						c.setObjectID( ARTIST_PREFIX + artistid );
						c.setTitle( artistname );
						// c.setTotalCount( Integer.parseInt( albumCount ) );
						parent.getChildren().add( c );
						subList.add( c );
						numReturned++;
					}
				}
				catch ( Exception e )
				{
					e.printStackTrace();
					emitError( "error getting mp3tunes data" );
				}
			}
			else if ( parent.getObjectID().equals( ALBUMS_ID ) )
			{
				try
				{
					Parser p = UPnP.getXMLParser();
					Node n = p.parse( new URL( "http://ws.mp3tunes.com/api/v1/lockerData?output=xml&sid=" + session_id + "&partner_token=" + partner_token
							+ "&type=album&count=" + requestCount + "&set=" + parent.getChildCount() / 25 ) );

					Node summary = n.getNode( "summary" );
					String totalResults = summary.getNodeValue( "totalResults" );
					parent.setTotalCount( Integer.parseInt( totalResults ) );

					Node artistList = n.getNode( "albumList" );
					for ( int i = 0; i < artistList.getNNodes(); ++i )
					{
						Node item = artistList.getNode( i );
						String albumId = item.getNodeValue( "albumId" );
						String albumTitle = item.getNodeValue( "albumTitle" );
						String albumArtURL = item.getNodeValue( "albumArtURL" );
						// String trackCount = item.getNodeValue( "trackCount" );

						Container c = new Container();
						c.setParent( parent );
						c.setObjectID( ALBUM_PREFIX + albumId );
						c.setTitle( albumTitle );
						// c.setTotalCount( Integer.parseInt( trackCount ) );
						if ( albumArtURL != null && albumArtURL.length() != 0 )
							c.setArtURL( albumArtURL );
						parent.getChildren().add( c );
						subList.add( c );
						numReturned++;
					}
				}
				catch ( Exception e )
				{
					e.printStackTrace();
					emitError( "error getting mp3tunes data" );
				}
			}
			else if ( parent.getObjectID().equals( VIDEO_ID ) )
			{
				try
				{
					Parser p = UPnP.getXMLParser();
					Node n = p.parse( new URL( "http://ws.mp3tunes.com/api/v1/lockerData?output=xml&sid=" + session_id + "&partner_token=" + partner_token
							+ "&type=video&count=" + requestCount + "&set=" + parent.getChildCount() / 25 ) );

					// Node summary = n.getNode( "summary" );
					// String totalResults = summary.getNodeValue( "totalResults" );
					// parent.setTotalCount( Integer.parseInt( totalResults ) );

					Node trackList = n.getNode( "trackList" );
					for ( int i = 0; i < trackList.getNNodes(); ++i )
					{
						Node itemNode = trackList.getNode( i );
						String trackId = itemNode.getNodeValue( "trackId" );
						String trackTitle = itemNode.getNodeValue( "trackTitle" );
						// String trackFileSize = itemNode.getNodeValue( "trackFileSize" );
						String trackFileMimeType = itemNode.getNodeValue( "trackFileMimeType" );
						String playURL = itemNode.getNodeValue( "playURL" );
						String trackLength = itemNode.getNodeValue( "trackLength" );

						Item item = new Item();
						item.setInternal( true );
						item.setObjectID( "video-" + trackId );
						item.setUpnpClass( "object.item.videoItem" );
						item.setContentType( ContentType.Video );
						item.setTitle( trackTitle );
						// item.setSize( trackFileSize );
						item.setMimeType( trackFileMimeType );

						if ( trackLength != null && trackLength.length() != 0 )
							item.setTotalSeconds( (long)(Double.parseDouble( trackLength ) / 1000) );

						item.setResourceURL( playURL );

						parent.getChildren().add( item );

						parent.setTotalCount( parent.getChildCount() );

						subList.add( item );
						numReturned++;
					}
				}
				catch ( Exception e )
				{
					e.printStackTrace();
					emitError( "error getting mp3tunes data" );
				}
			}
			else if ( parent.getObjectID().startsWith( ARTIST_PREFIX ) )
			{
				String artistkey = parent.getObjectID().replace( ARTIST_PREFIX, "" );

				try
				{
					Parser p = UPnP.getXMLParser();
					Node n = p.parse( new URL( "http://ws.mp3tunes.com/api/v1/lockerData?output=xml&sid=" + session_id + "&partner_token=" + partner_token
							+ "&type=album&count=" + requestCount + "&set=" + parent.getChildCount() / 25 + "&artist_id=" + artistkey ) );

					Node summary = n.getNode( "summary" );
					String totalResults = summary.getNodeValue( "totalResults" );
					parent.setTotalCount( Integer.parseInt( totalResults ) );

					Node artistList = n.getNode( "albumList" );
					for ( int i = 0; i < artistList.getNNodes(); ++i )
					{
						Node item = artistList.getNode( i );
						String albumId = item.getNodeValue( "albumId" );
						String albumTitle = item.getNodeValue( "albumTitle" );
						String albumArtURL = item.getNodeValue( "albumArtURL" );
						// String trackCount = item.getNodeValue( "trackCount" );

						Container c = new Container();
						c.setParent( parent );
						c.setObjectID( ALBUM_PREFIX + albumId );
						c.setTitle( albumTitle );
						// c.setTotalCount( Integer.parseInt( trackCount ) );
						if ( albumArtURL != null && albumArtURL.length() != 0 )
							c.setArtURL( albumArtURL );
						parent.getChildren().add( c );
						subList.add( c );
						numReturned++;
					}
				}
				catch ( Exception e )
				{
					e.printStackTrace();
					emitError( "error getting mp3tunes data" );
				}
			}
			else if ( parent.getObjectID().startsWith( ALBUM_PREFIX ) )
			{
				String albumkey = parent.getObjectID().replace( ALBUM_PREFIX, "" );

				try
				{
					Parser p = UPnP.getXMLParser();
					Node n = p.parse( new URL( "http://ws.mp3tunes.com/api/v1/lockerData?output=xml&sid=" + session_id + "&partner_token=" + partner_token
							+ "&type=track&count=" + requestCount + "&set=" + parent.getChildCount() / 25 + "&album_id=" + albumkey ) );

					Node summary = n.getNode( "summary" );
					String totalResults = summary.getNodeValue( "totalResults" );
					parent.setTotalCount( Integer.parseInt( totalResults ) );

					Node trackList = n.getNode( "trackList" );
					for ( int i = 0; i < trackList.getNNodes(); ++i )
					{
						Node itemNode = trackList.getNode( i );
						String trackId = itemNode.getNodeValue( "trackId" );
						String trackTitle = itemNode.getNodeValue( "trackTitle" );
						// String trackFileSize = itemNode.getNodeValue( "trackFileSize" );
						String trackFileMimeType = itemNode.getNodeValue( "trackFileMimeType" );
						String playURL = itemNode.getNodeValue( "playURL" );
						String albumTitle = itemNode.getNodeValue( "albumTitle" );
						String artistName = itemNode.getNodeValue( "artistName" );
						String trackLength = itemNode.getNodeValue( "trackLength" );

						Item item = new Item();
						item.setInternal( true );
						item.setObjectID( "audio-" + trackId );
						item.setUpnpClass( "object.item.audioItem.musicTrack" );
						item.setContentType( ContentType.Audio );
						item.setTitle( trackTitle );
						item.setAlbum( albumTitle );
						item.setArtist( artistName );
						// item.setSize( trackFileSize );
						item.setMimeType( trackFileMimeType );

						if ( trackLength != null && trackLength.length() != 0 )
							item.setTotalSeconds( (long)(Double.parseDouble( trackLength ) / 1000) );

						item.setResourceURL( playURL );
						if ( parent.getArtURL() != null && parent.getArtURL().length() != 0 )
							item.setArtURL( parent.getArtURL() );

						// item.setArtURL( "thumbnail:" + item.getObjectID() );
						// if ( parent.getArtURL() == DirEntry.NOART || parent.getArtURL() == null )
						// parent.setArtURL( "thumbnail:" + item.getObjectID() );

						parent.getChildren().add( item );
						subList.add( item );
						numReturned++;
					}
				}
				catch ( Exception e )
				{
					e.printStackTrace();
					emitError( "error getting mp3tunes data" );
				}
			}
			else if ( parent.getObjectID().startsWith( PLAYLIST_PREFIX ) )
			{
				String playlistId = parent.getObjectID().replace( PLAYLIST_PREFIX, "" );

				try
				{
					Parser p = UPnP.getXMLParser();
					Node n = p.parse( new URL( "http://ws.mp3tunes.com/api/v1/lockerData?output=xml&sid=" + session_id + "&partner_token=" + partner_token
							+ "&type=track&count=" + requestCount + "&set=" + parent.getChildCount() / 25 + "&playlist_id=" + playlistId ) );

					Node summary = n.getNode( "summary" );
					String totalResults = summary.getNodeValue( "totalResults" );
					parent.setTotalCount( Integer.parseInt( totalResults ) );

					Node trackList = n.getNode( "trackList" );
					for ( int i = 0; i < trackList.getNNodes(); ++i )
					{
						Node itemNode = trackList.getNode( i );
						String trackId = itemNode.getNodeValue( "trackId" );
						String trackTitle = itemNode.getNodeValue( "trackTitle" );
						// String trackFileSize = itemNode.getNodeValue( "trackFileSize" );
						String trackFileMimeType = itemNode.getNodeValue( "trackFileMimeType" );
						String playURL = itemNode.getNodeValue( "playURL" );
						String albumTitle = itemNode.getNodeValue( "albumTitle" );
						String artistName = itemNode.getNodeValue( "artistName" );
						String hasArt = itemNode.getNodeValue( "hasArt" );
						String trackFileKey = itemNode.getNodeValue( "trackFileKey" );
						String trackLength = itemNode.getNodeValue( "trackLength" );

						Item item = new Item();
						item.setInternal( true );
						item.setObjectID( "audio-" + trackId );
						item.setUpnpClass( "object.item.audioItem.musicTrack" );
						item.setContentType( ContentType.Audio );
						item.setTitle( trackTitle );
						item.setAlbum( albumTitle );
						item.setArtist( artistName );
						// item.setSize( trackFileSize );
						item.setMimeType( trackFileMimeType );

						if ( trackLength != null && trackLength.length() != 0 )
							item.setTotalSeconds( (long)(Double.parseDouble( trackLength ) / 1000) );

						item.setResourceURL( playURL );

						if ( hasArt != null && hasArt.length() != 0 && !hasArt.equals( "0" ) )
						{
							item.setArtURL( "http://content.mp3tunes.com/storage/albumartget/" + trackFileKey + "?sid=" + session_id + "&partner_token="
									+ partner_token );
							if ( parent.getArtURL() == DirEntry.NOART || parent.getArtURL() == null )
								parent.setArtURL( item.getArtURL() );
						}

						parent.getChildren().add( item );
						subList.add( item );
						numReturned++;
					}
				}
				catch ( Exception e )
				{
					e.printStackTrace();
					emitError( "error getting mp3tunes data" );
				}
			}
			entryCount += numReturned;

			if ( numReturned < 25 ) // MP3tunes sometimes returns inaccurate total counts (e.g. "most played" playlist always reports 50)
			{
				parent.setTotalCount( parent.getChildCount() );
				entriesLeft = 0;
			}

			entriesLeft = parent.getTotalCount() - entryCount;

			// if ( requestCount == 10 )
			// requestCount = REQUEST_COUNT;

			if ( listener != null && !listener.loadMore() )
			{
				return;
			}

			if ( listener != null )
				listener.onMoreChildren( subList, parent.getTotalCount() );
		}

		if ( listener != null )
			listener.onDone();
	}

	private void login()
	{
		try
		{
			String url = "https://shop.mp3tunes.com/api/v1/login?output=xml&username=" + URLEncoder.encode( username ) + "&password="
					+ URLEncoder.encode( password ) + "&partner_token=" + partner_token;
			// System.out.println( url );
			Parser p = UPnP.getXMLParser();
			Node n = p.parse( new URL( url ) );
			session_id = n.getNodeValue( "session_id" );
			if ( session_id.length() == 0 || session_id.equals( "0" ) )
				session_id = null;
		}
		catch ( Exception e )
		{
			e.printStackTrace();
			emitError( "error logging into mp3tunes" );
		}
	}

	@Override
	public void browseDir( Container parent )
	{
		browseDir( parent, null, null, -1 );
	}

	@Override
	public void browseDir( Container parent, int max )
	{
		browseDir( parent, null, null, max );
	}

	@Override
	public void browseDir( final Container parent, final BrowseResultsListener listener )
	{
		if ( listener != null )
		{
			// We copy because parent.getChildren() is used by the background thread
			ArrayList<DirEntry> copy = new ArrayList<DirEntry>();
			copy.addAll( parent.getChildren() );
			listener.onMoreChildren( copy, parent.getTotalCount() );
		}

		new Thread( new Runnable()
		{
			public void run()
			{
				browseDir( parent, null, listener, -1 );
			}
		} ).start();
	}

	@Override
	public boolean canSearch()
	{
		return false;
	}

	@Override
	public void searchDir( Container parent, String query )
	{
		browseDir( parent, query, null, -1 );
	}

	@Override
	public void searchDir( final Container parent, final String query, final BrowseResultsListener listener )
	{
		new Thread( new Runnable()
		{
			public void run()
			{
				browseDir( parent, query, listener, -1 );
			}
		} ).start();
	}

	@Override
	public void updateServices( Device dev )
	{
	}

	@Override
	public void loadIcon( ImageView imageView )
	{
		imageView.setImageResource( R.drawable.mp3tunes_vertical );
	}

	@Override
	public boolean isAlive()
	{
		return username != null && password != null;
	}

	public String getUsername()
	{
		return username;
	}

	public String getPassword()
	{
		return password;
	}

	public void setUsername( String username )
	{
		this.username = username;

		final SharedPreferences preferences = MainActivity.me.getSharedPreferences( SettingsEditor.ADVANCED_SETTINGS, Activity.MODE_PRIVATE );
		preferences.edit().putString( "MP3TUNES_USERNAME", username ).commit();
	}

	public boolean setPassword( String password )
	{
		this.password = password;

		session_id = null;

		login();

		if ( session_id != null )
		{
			final SharedPreferences preferences = MainActivity.me.getSharedPreferences( SettingsEditor.ADVANCED_SETTINGS, Activity.MODE_PRIVATE );
			preferences.edit().putString( "MP3TUNES_PASSWORD", password ).commit();
			return true;
		}
		else
		{
			return false;
		}
	}
}
