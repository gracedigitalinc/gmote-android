package com.plugplayer.plugplayer.upnp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.cybergarage.http.HTTPRequest;
import org.cybergarage.http.HTTPResponse;
import org.cybergarage.http.HTTPServer;
import org.cybergarage.http.HTTPStatus;
import org.cybergarage.upnp.Action;
import org.cybergarage.upnp.Device;
import org.cybergarage.upnp.Service;
import org.cybergarage.upnp.StateVariable;
import org.cybergarage.upnp.UPnP;
import org.cybergarage.upnp.UPnPStatus;
import org.cybergarage.upnp.control.ActionListener;
import org.cybergarage.upnp.control.QueryListener;
import org.cybergarage.xml.Node;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings.Secure;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import com.plugplayer.plugplayer.R;
import com.plugplayer.plugplayer.activities.MainActivity;
import com.plugplayer.plugplayer.upnp.DirEntry.SelectionState;
import com.plugplayer.plugplayer.upnp.Item.ContentType;
import com.plugplayer.plugplayer.utils.StateMap;

public class AndroidServer extends Server implements ActionListener, QueryListener
{
	private final Map<String, String> typeMap = new HashMap<String, String>();

	private static final String searches[] = { "(upnp:class = \"object.container.album.musicAlbum\" and dc:title contains \"%s\")",
			"(upnp:class = \"object.container.person.musicArtist\" and dc:title contains \"%s\")", "(dc:title contains \"%s\")" };

	public AndroidServer()
	{
		typeMap.put( ".avi", "video/x-msvideo" );
		typeMap.put( ".mpg", "video/mpeg" );
		typeMap.put( ".mov", "video/quicktime" );
		typeMap.put( ".wmv", "video/x-ms-wmv" );
		typeMap.put( ".mpv", "video/mpeg" );
		typeMap.put( ".m4v", "video/x-m4v" );
		typeMap.put( ".mkv", "video/x-matroska" );
		typeMap.put( ".mp4", "video/mp4" );
		typeMap.put( ".3gp", "video/3gpp" );
		typeMap.put( ".flv", "video/x-flv" );
		typeMap.put( ".ts", "video/mp2t" );

		typeMap.put( ".png", "image/png" );
		typeMap.put( ".gif", "image/gif" );
		typeMap.put( ".jpeg", "image/jpeg" );
		typeMap.put( ".jpg", "image/jpeg" );
		typeMap.put( ".bmp", "image/bmp" );

		typeMap.put( ".mp3", "audio/mpeg" );
		typeMap.put( ".wma", "audio/x-ms-wma" );
		typeMap.put( ".aiff", "audio/x-aiff" );
		typeMap.put( ".aifc", "audio/x-aiff" );
		typeMap.put( ".aif", "audio/x-aiff" );
		typeMap.put( ".wav", "audio/x-wav" );
		typeMap.put( ".m4a", "audio/mp4" );
		typeMap.put( ".m4b", "audio/mp4" );
		typeMap.put( ".m4p", "audio/mp4" );
		typeMap.put( ".aac", "audio/x-aac" );
		typeMap.put( ".ogg", "audio/ogg" );
		typeMap.put( ".flac", "audio/x-flac" );
		typeMap.put( ".ape", "audio/x-monkeys-audio" );
		typeMap.put( ".mp3pro", "audio/mpeg" );

		typeMap.put( ".xls", "application/vnd.ms-excel" );
		typeMap.put( ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" );
		typeMap.put( ".doc", "application/msword" );
		typeMap.put( ".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document" );
		typeMap.put( ".ppt", "application/vnd.ms-powerpoint" );
		typeMap.put( ".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation" );
		typeMap.put( ".pdf", "application/pdf" );
		typeMap.put( ".txt", "text/plain" );
		typeMap.put( ".rtf", "text/richtext" );

		setOriginalName( Build.MODEL );
		updateServerFiles();
	}

	static String baseURL = "http://0.0.0.0:0";

	static public String getBaseURL()
	{
		return baseURL;
	}

	@Override
	public void setNameOverride( String nameOverride )
	{
		super.setNameOverride( nameOverride );

		final SharedPreferences preferences = MainActivity.me.getSharedPreferences( "lastVersionServer", Activity.MODE_PRIVATE );
		preferences.edit().putString( "lastVersionServer", "" ).commit();

		updateServerFiles();

		if ( getActive() )
		{
			if ( serverDevice != null )
			{
				serverDevice.stop();
				serverDevice = null;
			}

			setActive( false, false );
			setActive( true, false );
		}
	}

	private void updateServerFiles()
	{
		File webDir = new File( MainActivity.me.getFilesDir() + "/web" );

		String version = MainActivity.appVersion;

		final SharedPreferences preferences = MainActivity.me.getSharedPreferences( "lastVersionServer", Activity.MODE_PRIVATE );

		String lastVersion = preferences.getString( "lastVersionServer", "" );

		if ( !version.equals( lastVersion ) )
		{
			preferences.edit().putString( "lastVersionServer", version ).commit();

			if ( !webDir.exists() )
				webDir.mkdir();

			File descriptionFile = new File( webDir + "/ServerDescription.xml" );

			String deviceId = Secure.getString( MainActivity.me.getContentResolver(), Secure.ANDROID_ID ) + "_uuid";
			String uuid = "uuid:" + new UUID( deviceId.hashCode(), "PlugPlayer_Server".hashCode() ).toString();
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

			String filenames[] = { "ServerDescription.xml", "ContentDirectory.xml", "ConnectionManager.xml" };
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

	public static final String STATENAME = "AndroidServer";

	@Override
	public void toState( StateMap state )
	{
		super.toState( state );
		state.setName( STATENAME );
		state.setValue( "allowControl", allowControl );
	}

	@Override
	public void fromState( StateMap state )
	{
		super.fromState( state );
		allowControl = state.getValue( "allowControl", false );
	}

	public static AndroidServer createFromState( StateMap state )
	{
		AndroidServer rval = new AndroidServer();
		rval.fromState( state );
		return rval;
	}

	private static final String CONTENTDIRECTORY = "urn:schemas-upnp-org:service:ContentDirectory:1";
	private static final String CONNECTIONMANAGER = "urn:schemas-upnp-org:service:ConnectionManager:1";

	static Device serverDevice = null;

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
		if ( AndroidServer.serverDevice == null || AndroidServer.serverDevice.getHTTPServerList().size() == 0 )
			return "";
		HTTPServer s = AndroidServer.serverDevice.getHTTPServerList().getHTTPServer( 0 );
		return "http://" + s.getBindAddress() + ":" + s.getBindPort() + "/ServerDescription.xml";
	}

	@Override
	public void setActive( boolean active, boolean hardStop )
	{
		if ( active == getActive() )
			return;

		// We never shut off the server once it is on if allow control is set
		if ( allowControl && !active && serverDevice != null )
			return;

		super.setActive( active, hardStop );

		if ( active )
		{
			if ( allowControl )
			{
				serverDevice = new Device()
				{
					@Override
					public void httpRequestRecieved( HTTPRequest httpReq )
					{
						if ( !AndroidServer.this.httpRequestRecieved( httpReq ) )
							super.httpRequestRecieved( httpReq );
					}
				};
				try
				{
					File webDir = new File( MainActivity.me.getFilesDir() + "/web" );

					serverDevice.loadDescription( new File( webDir + "/ServerDescription.xml" ) );

					Service contentDirectoryService = serverDevice.getService( CONTENTDIRECTORY );
					contentDirectoryService.loadSCPD( new File( webDir + "/ContentDirectory.xml" ) );

					Service connectionManagerService = serverDevice.getService( CONNECTIONMANAGER );
					connectionManagerService.loadSCPD( new File( webDir + "/ConnectionManager.xml" ) );

					// INITIALIZE STATE VARIABLES HERE

					serverDevice.setDescriptionURI( "/ServerDescription.xml" );
					serverDevice.setActionListener( this );
					serverDevice.setQueryListener( this );
					serverDevice.start();

					HTTPServer s = serverDevice.getHTTPServerList().getHTTPServer( 0 );
					baseURL = "http://" + s.getBindAddress() + ":" + s.getBindPort();
				}
				catch ( Exception e )
				{
					e.printStackTrace();
				}
			}
		}
		else
		{
			if ( serverDevice != null )
			{
				serverDevice.stop();
				serverDevice = null;
			}
		}
	}

	public boolean queryControlReceived( StateVariable stateVar )
	{
		return false;
	}

	public boolean actionControlReceived( Action action )
	{
		String service = action.getService().getServiceType();

		boolean rval = false;
		if ( service.equals( CONTENTDIRECTORY ) )
			rval = contentDirectoryActionReceived( action );
		else if ( service.equals( CONNECTIONMANAGER ) )
			rval = connectionManagerActionReceived( action );

		if ( !rval )
		{
			action.setStatus( UPnPStatus.INVALID_ACTION, "" );
			Log.w( MainActivity.appName, "unhandled action '" + action.getName() + "'" );
		}

		return rval;
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
			action.setArgumentValue( "Sink", "" );
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
			action.setArgumentValue( "ProtocolInfo", "" );
			action.setArgumentValue( "PeerConnectionManager", "" );
			action.setArgumentValue( "PeerConnectionID", 0 );
			action.setArgumentValue( "Direction", "Input" );
			action.setArgumentValue( "Status", "OK" );
			return true;
		}

		return false;
	}

	private boolean contentDirectoryActionReceived( Action action )
	{
		String name = action.getName();

		if ( name.equals( "Browse" ) )
		{
			baseURL = "http://" + action.getActionRequest().getLocalAddress() + ":" + action.getActionRequest().getLocalPort();

			String ObjectID = action.getArgumentValue( "ObjectID" );
			String BrowseFlag = action.getArgumentValue( "BrowseFlag" );
			// String Filter = action.getArgumentValue( "Filter" );
			int StartingIndex = action.getArgumentIntegerValue( "StartingIndex" );
			int RequestedCount = action.getArgumentIntegerValue( "RequestedCount" );
			// String SortCriteria = action.getArgumentValue( "SortCriteria" );

			int max = StartingIndex + RequestedCount;

			Container parent = new Container();

			if ( BrowseFlag.equals( "BrowseMetadata" ) )
			{
				Item item = null;

				if ( ObjectID.startsWith( AUDIO_PREFIX ) )
				{
					String subid = ObjectID.replace( AUDIO_PREFIX, "" );

					String[] proj = { MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
							MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.MIME_TYPE,
							MediaStore.Audio.Media.TRACK };
					Cursor cursor = MainActivity.me.getContentResolver().query( MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
							MediaStore.Audio.Media._ID + "=?", new String[] { subid }, MediaStore.Audio.Media.TITLE );
					cursor.moveToFirst();

					int nameIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.TITLE );
					int artistIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.ARTIST );
					int albumIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.ALBUM );
					int idIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media._ID );
					int pathIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.DATA );
					int sizeIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.SIZE );
					int typeIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.MIME_TYPE );
					int secondsIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.DURATION );
					int trackIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.TRACK );

					item = audioItemFromCursor( cursor, parent, nameIndex, artistIndex, albumIndex, idIndex, pathIndex, sizeIndex, typeIndex, secondsIndex,
							trackIndex );
				}
				else if ( ObjectID.startsWith( VIDEO_PREFIX ) )
				{
					String subid = ObjectID.replace( VIDEO_PREFIX, "" );

					String[] proj = { MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DATA,
							MediaStore.Video.Media.SIZE, MediaStore.Video.Media.MIME_TYPE, MediaStore.Video.Media.DURATION };
					Cursor cursor = MainActivity.me.getContentResolver().query( MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj,
							MediaStore.Video.Media._ID + "=?", new String[] { subid }, null );
					cursor.moveToFirst();

					int nameIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media.DISPLAY_NAME );
					int idIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media._ID );
					int pathIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media.DATA );
					int sizeIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media.SIZE );
					int typeIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media.MIME_TYPE );
					int secondsIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media.DURATION );

					item = videoItemFromCursor( cursor, parent, nameIndex, idIndex, pathIndex, sizeIndex, typeIndex, secondsIndex );
				}
				else if ( ObjectID.startsWith( IMAGE_PREFIX ) )
				{
					String subid = ObjectID.replace( IMAGE_PREFIX, "" );

					String[] proj = { MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATA,
							MediaStore.Images.Media.SIZE, MediaStore.Images.Media.MIME_TYPE };
					Cursor cursor = MainActivity.me.getContentResolver().query( MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj,
							MediaStore.Images.Media._ID + "=?", new String[] { subid }, null );
					cursor.moveToFirst();

					int nameIndex = cursor.getColumnIndexOrThrow( MediaStore.Images.Media.DISPLAY_NAME );
					int idIndex = cursor.getColumnIndexOrThrow( MediaStore.Images.Media._ID );
					int pathIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media.DATA );
					int sizeIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media.SIZE );
					int typeIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media.MIME_TYPE );

					item = imagItemFromCursor( cursor, parent, idIndex, nameIndex, pathIndex, sizeIndex, typeIndex );
				}
				else if ( ObjectID.startsWith( FOLDER_PREFIX ) )
				{
					String externalDir = getExternalDir();

					String subid = ObjectID.replace( FOLDER_PREFIX, "" );
					String data = Uri.decode( externalDir + "/" + subid );
					File child = new File( data );

					item = itemFromPath( child, parent, externalDir );
				}

				if ( item != null )
				{
					parent.getChildren().add( item );
					parent.setTotalCount( 1 );
					max = 1;
				}
			}
			else
			{
				parent.setObjectID( ObjectID );
				browseDir( parent, max );
			}

			int NumberReturned = 0;
			Node Result = new Node( "DIDL-Lite" );
			Result.setNamespace( " xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\"" );

			for ( int i = StartingIndex; i < max && i < parent.getTotalCount(); ++i )
			{
				DirEntry entry = parent.getChild( i );

				if ( entry instanceof Container )
				{
					Node p = new Node( "container" );
					p.setAttribute( "id", entry.getObjectID() );
					p.setAttribute( "parentID", ObjectID );

					Node t = new Node( "dc:title" );
					t.setNamespace( " xmlns:dc=\"http://purl.org/dc/elements/1.1/\"" );
					t.setValue( entry.getTitle() );
					p.addNode( t );

					Node c = new Node( "upnp:class" );
					c.setNamespace( " xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"" );
					c.setValue( "object.container.storageFolder" );
					p.addNode( c );

					Result.addNode( p );
				}
				else
				{
					Item item = (Item)entry;

					Node p = item.generateExternalMetadata( baseURL, false );

					Result.addNode( p );
				}

				NumberReturned++;
			}

			action.setArgumentValue( "Result", Result.toString() );
			action.setArgumentValue( "NumberReturned", NumberReturned );
			action.setArgumentValue( "TotalMatches", parent.getTotalCount() );
			action.setArgumentValue( "UpdateID", "0" );

			return true;
		}
		else if ( name.equals( "GetSearchCapabilities" ) )
		{
			action.setArgumentValue( "SearchCaps", "dc:title,upnp:album,upnp:artist" );
			return true;
		}
		else if ( name.equals( "Search" ) )
		{
			baseURL = "http://" + action.getActionRequest().getLocalAddress() + ":" + action.getActionRequest().getLocalPort();

			String ContainerID = action.getArgumentValue( "ContainerID" );
			String SearchCriteria = action.getArgumentValue( "SearchCriteria" );
			int StartingIndex = action.getArgumentIntegerValue( "StartingIndex" );
			int RequestedCount = action.getArgumentIntegerValue( "RequestedCount" );

			int max = StartingIndex + RequestedCount;

			Container parent = new Container();
			parent.setObjectID( ContainerID );
			this.browseDir( parent, SearchCriteria, null, max );

			int NumberReturned = 0;
			Node Result = new Node( "DIDL-Lite" );
			Result.setNamespace( " xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\"" );

			for ( int i = StartingIndex; i < max && i < parent.getTotalCount(); ++i )
			{
				DirEntry entry = parent.getChild( i );

				if ( entry instanceof Container )
				{
					Node p = new Node( "container" );
					p.setAttribute( "id", entry.getObjectID() );
					p.setAttribute( "parentID", ContainerID );

					Node t = new Node( "dc:title" );
					t.setNamespace( " xmlns:dc=\"http://purl.org/dc/elements/1.1/\"" );
					t.setValue( entry.getTitle() );
					p.addNode( t );

					Node c = new Node( "upnp:class" );
					c.setNamespace( " xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"" );
					c.setValue( "object.container.storageFolder" );
					p.addNode( c );

					Result.addNode( p );
				}
				else
				{
					Item item = (Item)entry;

					Node p = item.generateExternalMetadata( baseURL, false );

					Result.addNode( p );
				}

				NumberReturned++;
			}

			action.setArgumentValue( "Result", Result.toString() );
			action.setArgumentValue( "NumberReturned", NumberReturned );
			action.setArgumentValue( "TotalMatches", parent.getTotalCount() );
			action.setArgumentValue( "UpdateID", "0" );

			return true;
		}

		return false;
	}

	private static class FileInputStreamCache
	{
		public String path;
		public long pos;
		public FileInputStream stream;
	}

	private final FileInputStreamCache lastFile = null;

	protected boolean httpRequestRecieved( HTTPRequest httpReq )
	{
		try
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
				else if ( uri.startsWith( "/media/" ) )
				{
					String externalDir = getExternalDir();
					if ( externalDir == null )
						return false;

					String id = uri.replace( "/media/", "" );
					id = id.substring( 0, id.indexOf( "." ) );

					String data = null;
					String type = null;
					long size = 0;

					if ( id.startsWith( AUDIO_PREFIX ) )
					{
						String subid = id.replace( AUDIO_PREFIX, "" );

						String[] proj = { MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.SIZE,
								MediaStore.Audio.Media.MIME_TYPE };
						Cursor cursor = MainActivity.me.getContentResolver().query( MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
								MediaStore.Audio.Media._ID + "=?", new String[] { subid }, null );

						cursor.moveToFirst();

						int dataIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.DATA );
						int sizeIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.SIZE );
						int typeIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.MIME_TYPE );

						data = cursor.getString( dataIndex );
						size = cursor.getLong( sizeIndex );
						type = cursor.getString( typeIndex );

						cursor.close();
					}
					else if ( id.startsWith( VIDEO_PREFIX ) )
					{
						String subid = id.replace( VIDEO_PREFIX, "" );

						String[] proj = { MediaStore.Video.Media._ID, MediaStore.Video.Media.DATA, MediaStore.Video.Media.SIZE,
								MediaStore.Video.Media.MIME_TYPE };
						Cursor cursor = MainActivity.me.getContentResolver().query( MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj,
								MediaStore.Video.Media._ID + "=?", new String[] { subid }, null );

						cursor.moveToFirst();

						int dataIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media.DATA );
						int sizeIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media.SIZE );
						int typeIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media.MIME_TYPE );

						data = cursor.getString( dataIndex );
						size = cursor.getLong( sizeIndex );
						type = cursor.getString( typeIndex );

						if ( type != null && type.toLowerCase().equals( "video/mp4" ) && data != null && data.toLowerCase().endsWith( ".mov" ) )
							type = "video/quicktime";

						cursor.close();
					}
					else if ( id.startsWith( IMAGE_PREFIX ) )
					{
						String subid = id.replace( IMAGE_PREFIX, "" );

						String[] proj = { MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA, MediaStore.Images.Media.SIZE,
								MediaStore.Images.Media.MIME_TYPE };
						Cursor cursor = MainActivity.me.getContentResolver().query( MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj,
								MediaStore.Images.Media._ID + "=?", new String[] { subid }, null );

						cursor.moveToFirst();

						int dataIndex = cursor.getColumnIndexOrThrow( MediaStore.Images.Media.DATA );
						int sizeIndex = cursor.getColumnIndexOrThrow( MediaStore.Images.Media.SIZE );
						int typeIndex = cursor.getColumnIndexOrThrow( MediaStore.Images.Media.MIME_TYPE );

						data = cursor.getString( dataIndex );
						size = cursor.getLong( sizeIndex );
						type = cursor.getString( typeIndex );

						cursor.close();
					}
					else if ( id.startsWith( FOLDER_PREFIX ) )
					{
						id = uri.replace( "/media/", "" );
						String subid = id.replace( FOLDER_PREFIX, "" );
						data = Uri.decode( externalDir + "/" + subid );
						size = new File( data ).length();
						type = typeFromFileName( data );
					}

					try
					{
						HTTPResponse httpRes = new HTTPResponse();
						httpRes.setContentType( type );
						httpRes.setStatusCode( HTTPStatus.OK );
						httpRes.setContentLength( size );

						long offset = 0;
						long length = size;
						if ( httpReq.hasContentRange() == true )
						{
							long firstPos = httpReq.getContentRangeFirstPosition();
							long lastPos = httpReq.getContentRangeLastPosition();

							if ( lastPos <= 0 )
								lastPos = length - 1;

							offset = firstPos;
							length = lastPos - firstPos + 1;
						}

						if ( lastFile != null && lastFile.path.equals( data ) && lastFile.pos <= offset )
						{
							httpRes.setContentInputStream( lastFile.stream );
							httpReq.post( httpRes, lastFile.pos );
						}
						else
						{
							if ( lastFile != null )
								lastFile.stream.close();

							FileInputStream fis = new FileInputStream( data );
							httpRes.setContentInputStream( fis );
							httpReq.post( httpRes );
							fis.close();
							// lastFile = new FileInputStreamCache();
							// lastFile.path = data;
							// lastFile.stream = fis;
						}

						// lastFile.pos = offset + length;
					}
					catch ( FileNotFoundException e )
					{
						e.printStackTrace();
						return false;
					}

					return true;
				}
				else if ( uri.startsWith( "/thumbnail/" ) )
				{
					String externalDir = getExternalDir();
					if ( externalDir == null )
						return false;

					String id = uri.replace( "/thumbnail/", "" );

					String data = null;

					if ( id.startsWith( AUDIO_PREFIX ) )
					{
						String subid = id.replace( AUDIO_PREFIX, "" );

						String[] proj = { MediaStore.Audio.Media._ID, MediaStore.Audio.Media.ALBUM };
						Cursor cursor = MainActivity.me.getContentResolver().query( MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
								MediaStore.Audio.Media._ID + "=?", new String[] { subid }, null );

						cursor.moveToFirst();

						int albumIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.ALBUM );

						String album = cursor.getString( albumIndex );
						data = getArtURLForAlbum( album );

						cursor.close();
					}
					else if ( id.startsWith( VIDEO_PREFIX ) )
					{
						data = getThumbnailForVideo( id );
					}
					else if ( id.startsWith( IMAGE_PREFIX ) )
					{
						String subid = id.replace( IMAGE_PREFIX, "" );

						String[] proj = { MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA };
						Cursor cursor = MainActivity.me.getContentResolver().query( MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj,
								MediaStore.Images.Media._ID + "=?", new String[] { subid }, null );

						cursor.moveToFirst();

						int dataIndex = cursor.getColumnIndexOrThrow( MediaStore.Images.Media.DATA );

						data = cursor.getString( dataIndex );

						cursor.close();
					}
					else if ( id.startsWith( FOLDER_PREFIX ) )
					{
						String subid = id.replace( FOLDER_PREFIX, "" );
						data = Uri.decode( externalDir + "/" + subid );
					}

					try
					{
						HTTPResponse httpRes = new HTTPResponse();
						httpRes.setContentType( "image/jpeg" );
						httpRes.setStatusCode( HTTPStatus.OK );
						httpRes.setContentInputStream( new FileInputStream( data ) );
						httpRes.setContentLength( new File( data ).length() );
						httpReq.post( httpRes );
					}
					catch ( FileNotFoundException e )
					{
						Log.e( MainActivity.appName, "Couldn't find '" + data + "'", e );
						return false;
					}
					catch ( Exception e )
					{
						Log.e( MainActivity.appName, "Couldn't load '" + data + "'", e );
						return false;
					}

					return true;
				}
			}
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}

		return false;
	}

	public static String generateInternalURL( String url )
	{
		url = url.replace( getBaseURL(), "" );

		String data = null;
		if ( url.startsWith( "/media/" ) )
		{
			String externalDir = getExternalDir();
			if ( externalDir == null )
				return null;

			String id = url.replace( "/media/", "" );
			id = id.substring( 0, id.indexOf( "." ) );

			if ( id.startsWith( AUDIO_PREFIX ) )
			{
				String subid = id.replace( AUDIO_PREFIX, "" );

				String[] proj = { MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.MIME_TYPE };
				Cursor cursor = MainActivity.me.getContentResolver().query( MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
						MediaStore.Audio.Media._ID + "=?", new String[] { subid }, null );

				if ( cursor.moveToFirst() )
				{
					int dataIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.DATA );
					data = cursor.getString( dataIndex );
				}

				cursor.close();
			}
			else if ( id.startsWith( VIDEO_PREFIX ) )
			{
				String subid = id.replace( VIDEO_PREFIX, "" );

				String[] proj = { MediaStore.Video.Media._ID, MediaStore.Video.Media.DATA, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.MIME_TYPE };
				Cursor cursor = MainActivity.me.getContentResolver().query( MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj,
						MediaStore.Video.Media._ID + "=?", new String[] { subid }, null );

				if ( cursor.moveToFirst() )
				{
					int dataIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media.DATA );
					data = cursor.getString( dataIndex );
				}

				cursor.close();
			}
			else if ( id.startsWith( IMAGE_PREFIX ) )
			{
				String subid = id.replace( IMAGE_PREFIX, "" );

				String[] proj = { MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA, MediaStore.Images.Media.SIZE, MediaStore.Images.Media.MIME_TYPE };
				Cursor cursor = MainActivity.me.getContentResolver().query( MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj,
						MediaStore.Images.Media._ID + "=?", new String[] { subid }, null );

				if ( cursor.moveToFirst() )
				{
					int dataIndex = cursor.getColumnIndexOrThrow( MediaStore.Images.Media.DATA );
					data = cursor.getString( dataIndex );
				}

				cursor.close();
			}
			else if ( id.startsWith( FOLDER_PREFIX ) )
			{
				id = url.replace( "/media/", "" );
				String subid = id.replace( FOLDER_PREFIX, "" );
				data = externalDir + "/" + subid;
			}

			// Not sure why data would be null, but this happened on the market and we shouldn't crash.
			if ( data != null )
				return Uri.fromFile( new File( data ) ).toString();
			else
				return null;
		}
		else if ( url.startsWith( "/thumbnail/" ) )
		{
			String externalDir = getExternalDir();
			if ( externalDir == null )
				return null;

			String id = url.replace( "/thumbnail/", "" );

			if ( id.startsWith( AUDIO_PREFIX ) )
			{
				String subid = id.replace( AUDIO_PREFIX, "" );

				String[] proj = { MediaStore.Audio.Media._ID, MediaStore.Audio.Media.ALBUM };
				Cursor cursor = MainActivity.me.getContentResolver().query( MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
						MediaStore.Audio.Media._ID + "=?", new String[] { subid }, null );

				if ( cursor.moveToFirst() )
				{
					int albumIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.ALBUM );
					String album = cursor.getString( albumIndex );
					data = getArtURLForAlbum( album );
				}

				cursor.close();

				return Uri.fromFile( new File( data ) ).toString();
			}
			else if ( id.startsWith( VIDEO_PREFIX ) )
			{
				return "thumbnail:" + id;
			}
			else if ( id.startsWith( IMAGE_PREFIX ) )
			{
				String subid = id.replace( IMAGE_PREFIX, "" );

				String[] proj = { MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA, MediaStore.Images.Media.SIZE, MediaStore.Images.Media.MIME_TYPE };
				Cursor cursor = MainActivity.me.getContentResolver().query( MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj,
						MediaStore.Images.Media._ID + "=?", new String[] { subid }, null );

				if ( cursor.moveToFirst() )
				{
					int dataIndex = cursor.getColumnIndexOrThrow( MediaStore.Images.Media.DATA );
					data = cursor.getString( dataIndex );
				}

				cursor.close();

				return Uri.fromFile( new File( data ) ).toString();
			}
			else if ( id.startsWith( FOLDER_PREFIX ) )
			{
				String subid = id.replace( FOLDER_PREFIX, "" );
				data = externalDir + "/" + subid;

				return Uri.fromFile( new File( data ) ).toString();
			}

		}

		return null;
	}

	private static String getExternalDir()
	{
		String state = Environment.getExternalStorageState();
		if ( !Environment.MEDIA_MOUNTED.equals( state ) )
			return null;

		return Environment.getExternalStorageDirectory().getAbsolutePath();
	}

	private static final String ROOT_ID = "0";

	// IMPORTANT! Be careful not to change these
	// Some clients (MAP for example) use these tokens
	// in their code and must match between server and client
	public static final String AUDIO_ID = "1";
	public static final String VIDEO_ID = "2";
	public static final String IMAGE_ID = "3";
	public static final String FOLDERS_ID = "7";

	private static final String ALLAUDIO_ID = "8";
	private static final String ARTISTS_ID = "4";
	private static final String ALBUMS_ID = "5";
	private static final String GENRES_ID = "6";

	private static final String ARTIST_PREFIX = "artist-";
	private static final String ALBUM_PREFIX = "album-";
	private static final String GENRE_PREFIX = "genre-";

	private static final String AUDIO_PREFIX = "audio-";
	private static final String VIDEO_PREFIX = "video-";
	private static final String IMAGE_PREFIX = "image-";
	private static final String FOLDER_PREFIX = "folder-";

	private static final int REQUEST_COUNT = 50;

	private synchronized void searchDir( Container parent, String search, BrowseResultsListener listener, int max )
	{
		String[] titleSplit = search.split( "dc:title" );
		if ( titleSplit.length < 2 )
		{
			return; // it's not a search we support
		}

		titleSplit = titleSplit[1].split( "\"" );
		if ( titleSplit.length < 2 )
		{
			return;
		}

		String searchText = titleSplit[1];

		boolean upnpClassAlbum = false;
		boolean upnpClassArtist = false;
		String[] classSplit = search.split( "upnp.class" );
		if ( classSplit.length > 0 )
		{
			classSplit = classSplit[classSplit.length - 1].split( "\"" );
			if ( classSplit.length > 1 )
			{
				String classString = classSplit[1];
				upnpClassAlbum = classString.indexOf( "album" ) > 0;
				upnpClassArtist = classString.indexOf( "person" ) > 0;
			}
		}

		String artistKey = null;
		if ( parent.getObjectID().startsWith( ARTIST_PREFIX ) )
		{
			artistKey = parent.getObjectID().replace( ARTIST_PREFIX, "" );
		}

		String albumKey = null;
		if ( parent.getObjectID().startsWith( ALBUM_PREFIX ) )
		{
			albumKey = parent.getObjectID().replace( ALBUM_PREFIX, "" );
		}

		String genreKey = null;
		if ( parent.getObjectID().startsWith( GENRE_PREFIX ) )
		{
			genreKey = parent.getObjectID().replace( GENRE_PREFIX, "" );
		}

		boolean globalScope = parent.getObjectID().equals( ROOT_ID );
		boolean musicScope = parent.getObjectID().equals( AUDIO_ID );
		boolean albumsScope = parent.getObjectID().equals( ALBUMS_ID );
		boolean artistsScope = parent.getObjectID().equals( ARTISTS_ID );
		boolean videoScope = parent.getObjectID().equals( VIDEO_ID );
		boolean imageScope = parent.getObjectID().equals( IMAGE_ID );
		boolean searchAlbums = (globalScope || musicScope || albumsScope || artistsScope || artistKey != null) && albumKey == null && upnpClassAlbum;
		boolean searchArtists = (globalScope || musicScope || artistsScope) && artistKey == null && albumKey == null && upnpClassArtist;
		boolean searchMusicItemTitles = !videoScope && !imageScope && !upnpClassAlbum && !upnpClassArtist;
		boolean searchVideoItemTitles = (globalScope || videoScope) && !upnpClassAlbum && !upnpClassArtist;
		boolean searchImageItemTitles = (globalScope || imageScope) && !upnpClassAlbum && !upnpClassArtist;

		Cursor cursor = null;
		ArrayList<DirEntry> subList = new ArrayList<DirEntry>();

		String externalDir = getExternalDir();
		if ( externalDir == null )
		{
			handleNoSDCard( listener );
			return;
		}

		if ( searchAlbums )
		{
			String[] proj = { MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Albums.ALBUM_ART };
			if ( artistKey != null )
			{
				cursor = MainActivity.me.getContentResolver().query( MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, proj,
						MediaStore.Audio.Media.ALBUM + " like ? and " + MediaStore.Audio.Artists.ARTIST + " == ?",
						new String[] { "%" + searchText + "%", artistKey }, MediaStore.Audio.Albums.ALBUM );
			}
			else
			{
				cursor = MainActivity.me.getContentResolver().query( MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, proj,
						MediaStore.Audio.Media.ALBUM + " like ?", new String[] { "%" + searchText + "%" }, MediaStore.Audio.Albums.ALBUM );
			}

			int nameIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Albums.ALBUM );
			int artIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Albums.ALBUM_ART );

			while ( cursor.moveToNext() )
			{
				String title = cursor.getString( nameIndex );
				String artURL = cursor.getString( artIndex );
				Container c = new Container();
				c.setSelectionState( parent.getSelectionState() == SelectionState.Selected ? SelectionState.Selected : SelectionState.Unselected );
				c.setParent( parent );
				c.setObjectID( ALBUM_PREFIX + title );
				c.setTitle( title );
				if ( artURL != null )
					c.setArtURL( Uri.fromFile( new File( artURL ) ).toString() );
				parent.getChildren().add( c );
				subList.add( c );
			}
		}

		if ( searchArtists )
		{
			String[] proj = { MediaStore.Audio.Artists._ID, MediaStore.Audio.Artists.ARTIST, MediaStore.Audio.Artists.ARTIST_KEY };
			cursor = MainActivity.me.getContentResolver().query( MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI, proj,
					MediaStore.Audio.Media.ARTIST + " like ?", new String[] { "%" + searchText + "%" }, MediaStore.Audio.Albums.ARTIST );

			int nameIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Artists.ARTIST );

			while ( cursor.moveToNext() )
			{
				String title = cursor.getString( nameIndex );
				Container c = new Container();
				c.setSelectionState( parent.getSelectionState() == SelectionState.Selected ? SelectionState.Selected : SelectionState.Unselected );
				c.setParent( parent );
				c.setObjectID( ARTIST_PREFIX + title );
				c.setTitle( title );
				parent.getChildren().add( c );
				subList.add( c );
			}
		}

		if ( searchMusicItemTitles )
		{
			String[] proj = { MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
					MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.MIME_TYPE,
					MediaStore.Audio.Media.TRACK };
			if ( albumKey != null )
			{
				cursor = MainActivity.me.getContentResolver().query( MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
						MediaStore.Audio.Media.TITLE + " like ? and " + MediaStore.Audio.Albums.ALBUM + " == ?",
						new String[] { "%" + searchText + "%", albumKey }, MediaStore.Audio.Media.TITLE );
			}
			else if ( artistKey != null )
			{
				cursor = MainActivity.me.getContentResolver().query( MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
						MediaStore.Audio.Media.TITLE + " like ? and " + MediaStore.Audio.Artists.ARTIST + " == ?",
						new String[] { "%" + searchText + "%", artistKey }, MediaStore.Audio.Media.TITLE );
			}
			else if ( genreKey != null )
			{
				Uri uri = MediaStore.Audio.Genres.Members.getContentUri( "external", Long.parseLong( genreKey ) );
				cursor = MainActivity.me.getContentResolver().query( uri, proj, MediaStore.Audio.Media.TITLE + " like ?",
						new String[] { "%" + searchText + "%" }, MediaStore.Audio.Media.TITLE );
			}
			else
			{
				cursor = MainActivity.me.getContentResolver().query( MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
						MediaStore.Audio.Media.TITLE + " like ?", new String[] { "%" + searchText + "%" }, MediaStore.Audio.Media.TITLE );
			}

			int nameIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.TITLE );
			int artistIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.ARTIST );
			int albumIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.ALBUM );
			int idIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media._ID );
			int pathIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.DATA );
			int sizeIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.SIZE );
			int typeIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.MIME_TYPE );
			int secondsIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.DURATION );
			int trackIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.TRACK );

			while ( cursor.moveToNext() )
			{
				String title = cursor.getString( nameIndex );
				String artist = cursor.getString( artistIndex );
				String album = cursor.getString( albumIndex );
				String id = cursor.getString( idIndex );
				String path = cursor.getString( pathIndex );
				String size = cursor.getString( sizeIndex );
				String type = cursor.getString( typeIndex );
				long seconds = cursor.getLong( secondsIndex );
				int track = cursor.getInt( trackIndex );

				Item item = new Item();
				item.setSelectionState( parent.getSelectionState() == SelectionState.Selected ? SelectionState.Selected : SelectionState.Unselected );
				item.setParent( parent );
				item.setInternal( true );
				item.setObjectID( AUDIO_PREFIX + id );
				item.setUpnpClass( "object.item.audioItem.musicTrack" );
				item.setContentType( ContentType.Audio );
				item.setTitle( title );
				item.setSize( size );
				item.setMimeType( type );
				item.setTotalSeconds( seconds / 1000 );
				item.setResourceURL( Uri.fromFile( new File( path ) ).toString() );
				item.setAlbum( album );
				item.setArtist( artist );
				item.setTrackNumber( track );
				item.setGenre( genreLookup( id ) );
				String artURL = getArtURLForAlbum( album );

				if ( artURL != null )
				{
					item.setArtURL( Uri.fromFile( new File( artURL ) ).toString() );

					if ( parent.getArtURL() == DirEntry.NOART || parent.getArtURL() == null )
						parent.setArtURL( Uri.fromFile( new File( artURL ) ).toString() );
				}
				parent.getChildren().add( item );
				subList.add( item );
			}
		}

		if ( searchVideoItemTitles )
		{
			String[] proj = { MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DATA, MediaStore.Video.Media.SIZE,
					MediaStore.Video.Media.MIME_TYPE, MediaStore.Video.Media.DURATION };
			cursor = MainActivity.me.getContentResolver().query( MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj,
					MediaStore.Video.Media.DISPLAY_NAME + " like ?", new String[] { "%" + searchText + "%" }, MediaStore.Video.Media.DISPLAY_NAME );

			int nameIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media.DISPLAY_NAME );
			int idIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media._ID );
			int pathIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media.DATA );
			int sizeIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media.SIZE );
			int typeIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media.MIME_TYPE );
			int secondsIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media.DURATION );

			while ( cursor.moveToNext() )
			{
				Item item = videoItemFromCursor( cursor, parent, nameIndex, idIndex, pathIndex, sizeIndex, typeIndex, secondsIndex );
				parent.getChildren().add( item );
				subList.add( item );
			}
		}

		if ( searchImageItemTitles )
		{
			String[] proj = { MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATA, MediaStore.Images.Media.SIZE,
					MediaStore.Images.Media.MIME_TYPE };
			cursor = MainActivity.me.getContentResolver().query( MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj,
					MediaStore.Images.Media.DISPLAY_NAME + " like ?", new String[] { "%" + searchText + "%" }, MediaStore.Images.Media.DISPLAY_NAME );

			int nameIndex = cursor.getColumnIndexOrThrow( MediaStore.Images.Media.DISPLAY_NAME );
			int idIndex = cursor.getColumnIndexOrThrow( MediaStore.Images.Media._ID );
			int pathIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media.DATA );
			int sizeIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media.SIZE );
			int typeIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media.MIME_TYPE );

			while ( cursor.moveToNext() )
			{
				Item item = imagItemFromCursor( cursor, parent, idIndex, nameIndex, pathIndex, sizeIndex, typeIndex );
				parent.getChildren().add( item );
				subList.add( item );
			}
		}

		// notify listener
		parent.setTotalCount( parent.getChildren().size() );
		if ( listener != null && !listener.loadMore() )
		{
			if ( cursor != null )
				cursor.close();

			return;
		}

		if ( listener != null )
			listener.onMoreChildren( subList, parent.getTotalCount() );

		if ( cursor != null )
			cursor.close();

		if ( listener != null )
			listener.onDone();

	}

	private synchronized void browseDir( Container parent, String search, BrowseResultsListener listener, int max )
	{
		if ( search != null )
		{
			searchDir( parent, search, listener, max );
			return;
		}
		int entryCount = parent.getChildCount();
		int requestCount = entryCount != 0 ? REQUEST_COUNT : 10; // First

		int entriesLeft;

		if ( max > -1 )
			requestCount = max;

		if ( parent.getTotalCount() == -1 )
			entriesLeft = requestCount;
		else
			entriesLeft = parent.getTotalCount() - entryCount;

		Cursor cursor = null;

		while ( entriesLeft > 0 && (max < 0 || parent.getChildCount() != max) )
		{
			ArrayList<DirEntry> subList = new ArrayList<DirEntry>();

			int numReturned = 0;

			if ( parent.getObjectID().equals( ROOT_ID ) )
			{
				parent.setTotalCount( 4 );

				Container c = new Container();
				c.setSelectionState( parent.getSelectionState() == SelectionState.Selected ? SelectionState.Selected : SelectionState.Unselected );
				c.setParent( parent );
				c.setObjectID( FOLDERS_ID );
				c.setTitle( ((Context)MainActivity.me).getString( R.string.container_title_folders ) );
				parent.getChildren().add( c );
				subList.add( c );

				c = new Container();
				c.setSelectionState( parent.getSelectionState() == SelectionState.Selected ? SelectionState.Selected : SelectionState.Unselected );
				c.setParent( parent );
				c.setObjectID( AUDIO_ID );
				c.setTitle( ((Context)MainActivity.me).getString( R.string.container_title_audio ) );
				parent.getChildren().add( c );
				subList.add( c );

				c = new Container();
				c.setSelectionState( parent.getSelectionState() == SelectionState.Selected ? SelectionState.Selected : SelectionState.Unselected );
				c.setParent( parent );
				c.setObjectID( VIDEO_ID );
				c.setTitle( ((Context)MainActivity.me).getString( R.string.container_title_video ) );
				parent.getChildren().add( c );
				subList.add( c );

				c = new Container();
				c.setSelectionState( parent.getSelectionState() == SelectionState.Selected ? SelectionState.Selected : SelectionState.Unselected );
				c.setParent( parent );
				c.setObjectID( IMAGE_ID );
				c.setTitle( ((Context)MainActivity.me).getString( R.string.container_title_image ) );
				parent.getChildren().add( c );
				subList.add( c );

				numReturned = 4;
			}
			else if ( parent.getObjectID().startsWith( FOLDERS_ID ) )
			{
				if ( parent.getObjectID().contains( "../" ) )
				{
					if ( listener != null )
						listener.onDone();

					return;
				}

				String externalDir = getExternalDir();
				if ( externalDir == null )
				{
					handleNoSDCard( listener );
					return;
				}

				File rootdir = new File( externalDir );

				if ( parent.getObjectID().startsWith( FOLDERS_ID + "-" ) )
					rootdir = new File( externalDir + "/" + parent.getObjectID().replace( FOLDERS_ID + "-", "" ) );

				File[] children = rootdir.listFiles();

				if ( children != null )
				{
					for ( int i = parent.getChildCount(); i < children.length; ++i )
					{
						File child = children[i];

						if ( child.isDirectory() )
						{
							Container c = new Container();
							c.setSelectionState( parent.getSelectionState() == SelectionState.Selected ? SelectionState.Selected : SelectionState.Unselected );
							c.setParent( parent );
							c.setObjectID( FOLDERS_ID + "-" + child.getAbsolutePath().replace( externalDir + "/", "" ) );
							c.setTitle( child.getName() );
							parent.getChildren().add( c );
							subList.add( c );
						}
						else if ( child.isFile() )
						{
							Item item = itemFromPath( child, parent, externalDir );
							parent.getChildren().add( item );
							subList.add( item );
							numReturned++;
						}

						parent.setTotalCount( children.length );
						numReturned++;
					}
				}
				else
				{
					Log.e( MainActivity.appName, "Not a directory: '" + rootdir.getAbsolutePath() + "'" );
					Log.e( MainActivity.appName, "From container: '" + parent.getObjectID() + "'" );
				}
			}
			else if ( parent.getObjectID().equals( AUDIO_ID ) )
			{
				parent.setTotalCount( 4 );

				Container c = new Container();
				c.setSelectionState( parent.getSelectionState() == SelectionState.Selected ? SelectionState.Selected : SelectionState.Unselected );
				c.setParent( parent );
				c.setObjectID( ALLAUDIO_ID );
				c.setTitle( ((Context)MainActivity.me).getString( R.string.container_title_all_audio ) );
				parent.getChildren().add( c );
				subList.add( c );

				c = new Container();
				c.setSelectionState( parent.getSelectionState() == SelectionState.Selected ? SelectionState.Selected : SelectionState.Unselected );
				c.setParent( parent );
				c.setObjectID( ARTISTS_ID );
				c.setTitle( ((Context)MainActivity.me).getString( R.string.container_title_artists ) );
				parent.getChildren().add( c );
				subList.add( c );

				c = new Container();
				c.setSelectionState( parent.getSelectionState() == SelectionState.Selected ? SelectionState.Selected : SelectionState.Unselected );
				c.setParent( parent );
				c.setObjectID( ALBUMS_ID );
				c.setTitle( ((Context)MainActivity.me).getString( R.string.container_title_albums ) );
				parent.getChildren().add( c );
				subList.add( c );

				c = new Container();
				c.setSelectionState( parent.getSelectionState() == SelectionState.Selected ? SelectionState.Selected : SelectionState.Unselected );
				c.setParent( parent );
				c.setObjectID( GENRES_ID );
				c.setTitle( ((Context)MainActivity.me).getString( R.string.container_title_genres ) );
				parent.getChildren().add( c );
				subList.add( c );

				numReturned = 4;
			}
			else if ( parent.getObjectID().equals( ALLAUDIO_ID ) )
			{
				String externalDir = getExternalDir();
				if ( externalDir == null )
				{
					handleNoSDCard( listener );
					return;
				}

				if ( cursor == null )
				{
					String[] proj = { MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
							MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.MIME_TYPE,
							MediaStore.Audio.Media.TRACK };
					cursor = MainActivity.me.getContentResolver().query( MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, null, null,
							MediaStore.Audio.Media.TITLE );
					int totalCount = cursor.getCount();
					parent.setTotalCount( totalCount );

					cursor.moveToPosition( parent.getChildCount() );
					cursor.moveToPrevious();
				}

				int nameIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.TITLE );
				int artistIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.ARTIST );
				int albumIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.ALBUM );
				int idIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media._ID );
				int pathIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.DATA );
				int sizeIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.SIZE );
				int typeIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.MIME_TYPE );
				int secondsIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.DURATION );
				int trackIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.TRACK );

				while ( numReturned < requestCount && cursor.moveToNext() )
				{
					Item item = audioItemFromCursor( cursor, parent, nameIndex, artistIndex, albumIndex, idIndex, pathIndex, sizeIndex, typeIndex,
							secondsIndex, trackIndex );
					parent.getChildren().add( item );
					subList.add( item );
					numReturned++;
				}
			}
			else if ( parent.getObjectID().equals( ARTISTS_ID ) )
			{
				String externalDir = getExternalDir();
				if ( externalDir == null )
				{
					handleNoSDCard( listener );
					return;
				}

				if ( cursor == null )
				{
					String[] proj = { MediaStore.Audio.Artists._ID, MediaStore.Audio.Artists.ARTIST, MediaStore.Audio.Artists.ARTIST_KEY };
					cursor = MainActivity.me.getContentResolver().query( MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI, proj, null, null, null );
					int totalCount = cursor.getCount();
					parent.setTotalCount( totalCount );

					cursor.moveToPosition( parent.getChildCount() );
					cursor.moveToPrevious();
				}

				int nameIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Artists.ARTIST );

				while ( numReturned < requestCount && cursor.moveToNext() )
				{
					String title = cursor.getString( nameIndex );
					Container c = new Container();
					c.setSelectionState( parent.getSelectionState() == SelectionState.Selected ? SelectionState.Selected : SelectionState.Unselected );
					c.setParent( parent );
					c.setObjectID( ARTIST_PREFIX + title );
					c.setTitle( title );
					parent.getChildren().add( c );
					subList.add( c );
					numReturned++;
				}
			}
			else if ( parent.getObjectID().equals( ALBUMS_ID ) )
			{
				String externalDir = getExternalDir();
				if ( externalDir == null )
				{
					handleNoSDCard( listener );
					return;
				}

				if ( cursor == null )
				{
					String[] proj = { MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Albums.ALBUM_ART };
					cursor = MainActivity.me.getContentResolver().query( MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, proj, null, null, null );
					int totalCount = cursor.getCount();
					parent.setTotalCount( totalCount );

					cursor.moveToPosition( parent.getChildCount() );
					cursor.moveToPrevious();
				}

				int nameIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Albums.ALBUM );
				int artIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Albums.ALBUM_ART );

				while ( numReturned < requestCount && cursor.moveToNext() )
				{
					String title = cursor.getString( nameIndex );
					String artURL = cursor.getString( artIndex );
					Container c = new Container();
					c.setSelectionState( parent.getSelectionState() == SelectionState.Selected ? SelectionState.Selected : SelectionState.Unselected );
					c.setParent( parent );
					c.setObjectID( ALBUM_PREFIX + title );
					c.setTitle( title );
					if ( artURL != null )
						c.setArtURL( Uri.fromFile( new File( artURL ) ).toString() );
					parent.getChildren().add( c );
					subList.add( c );
					numReturned++;
				}
			}
			else if ( parent.getObjectID().equals( GENRES_ID ) )
			{
				String externalDir = getExternalDir();
				if ( externalDir == null )
				{
					handleNoSDCard( listener );
					return;
				}

				if ( cursor == null )
				{
					String[] proj = { MediaStore.Audio.Albums._ID, MediaStore.Audio.Genres.NAME };
					cursor = MainActivity.me.getContentResolver().query( MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI, proj, null, null, null );
					int totalCount = cursor.getCount();
					parent.setTotalCount( totalCount );

					cursor.moveToPosition( parent.getChildCount() );
					cursor.moveToPrevious();
				}

				int nameIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Genres.NAME );
				int keyIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Albums._ID );

				while ( numReturned < requestCount && cursor.moveToNext() )
				{
					String title = cursor.getString( nameIndex );
					String key = cursor.getString( keyIndex );
					Container c = new Container();
					c.setSelectionState( parent.getSelectionState() == SelectionState.Selected ? SelectionState.Selected : SelectionState.Unselected );
					c.setParent( parent );
					c.setObjectID( GENRE_PREFIX + key );
					c.setTitle( title );
					parent.getChildren().add( c );
					subList.add( c );
					numReturned++;
				}
			}
			else if ( parent.getObjectID().equals( VIDEO_ID ) )
			{
				String externalDir = getExternalDir();
				if ( externalDir == null )
				{
					handleNoSDCard( listener );
					return;
				}

				if ( cursor == null )
				{
					String[] proj = { MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DATA,
							MediaStore.Video.Media.SIZE, MediaStore.Video.Media.MIME_TYPE, MediaStore.Video.Media.DURATION };
					cursor = MainActivity.me.getContentResolver().query( MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj, null, null, null );
					int totalCount = cursor.getCount();
					parent.setTotalCount( totalCount );

					cursor.moveToPosition( parent.getChildCount() );
					cursor.moveToPrevious();
				}

				int nameIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media.DISPLAY_NAME );
				int idIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media._ID );
				int pathIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media.DATA );
				int sizeIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media.SIZE );
				int typeIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media.MIME_TYPE );
				int secondsIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media.DURATION );

				while ( numReturned < requestCount && cursor.moveToNext() )
				{
					Item item = videoItemFromCursor( cursor, parent, nameIndex, idIndex, pathIndex, sizeIndex, typeIndex, secondsIndex );
					parent.getChildren().add( item );
					subList.add( item );
					numReturned++;
				}
			}
			else if ( parent.getObjectID().equals( IMAGE_ID ) )
			{
				String externalDir = getExternalDir();
				if ( externalDir == null )
				{
					handleNoSDCard( listener );
					return;
				}

				if ( cursor == null )
				{
					String[] proj = { MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATA,
							MediaStore.Images.Media.SIZE, MediaStore.Images.Media.MIME_TYPE };
					cursor = MainActivity.me.getContentResolver().query( MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, null, null, null );
					int totalCount = cursor.getCount();
					parent.setTotalCount( totalCount );

					cursor.moveToPosition( parent.getChildCount() );
					cursor.moveToPrevious();
				}

				int nameIndex = cursor.getColumnIndexOrThrow( MediaStore.Images.Media.DISPLAY_NAME );
				int idIndex = cursor.getColumnIndexOrThrow( MediaStore.Images.Media._ID );
				int pathIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media.DATA );
				int sizeIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media.SIZE );
				int typeIndex = cursor.getColumnIndexOrThrow( MediaStore.Video.Media.MIME_TYPE );

				while ( numReturned < requestCount && cursor.moveToNext() )
				{
					Item item = imagItemFromCursor( cursor, parent, idIndex, nameIndex, pathIndex, sizeIndex, typeIndex );
					parent.getChildren().add( item );
					subList.add( item );
					numReturned++;
				}
			}
			else if ( parent.getObjectID().startsWith( ARTIST_PREFIX ) )
			{
				String externalDir = getExternalDir();
				if ( externalDir == null )
				{
					handleNoSDCard( listener );
					return;
				}

				String artistkey = parent.getObjectID().replace( ARTIST_PREFIX, "" );

				if ( cursor == null )
				{
					String[] proj = { MediaStore.Audio.Media._ID, MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Albums.ALBUM_ART };
					cursor = MainActivity.me.getContentResolver().query( MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, proj,
							MediaStore.Audio.Albums.ARTIST + "=?", new String[] { artistkey }, null );
					int totalCount = cursor.getCount();
					parent.setTotalCount( totalCount );

					cursor.moveToPosition( parent.getChildCount() );
					cursor.moveToPrevious();
				}

				int nameIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Albums.ALBUM );
				int artIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Albums.ALBUM_ART );

				while ( numReturned < requestCount && cursor.moveToNext() )
				{
					String title = cursor.getString( nameIndex );
					String artURL = cursor.getString( artIndex );
					Container c = new Container();
					c.setSelectionState( parent.getSelectionState() == SelectionState.Selected ? SelectionState.Selected : SelectionState.Unselected );
					c.setParent( parent );
					c.setObjectID( ALBUM_PREFIX + title );
					c.setTitle( title );
					if ( artURL != null )
						c.setArtURL( Uri.fromFile( new File( artURL ) ).toString() );
					parent.getChildren().add( c );
					subList.add( c );
					numReturned++;
				}
			}
			else if ( parent.getObjectID().startsWith( ALBUM_PREFIX ) )
			{
				String externalDir = getExternalDir();
				if ( externalDir == null )
				{
					handleNoSDCard( listener );
					return;
				}

				String albumkey = parent.getObjectID().replace( ALBUM_PREFIX, "" );

				if ( cursor == null )
				{
					String[] proj = { MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DATA,
							MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.TRACK };
					cursor = MainActivity.me.getContentResolver().query( MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
							MediaStore.Audio.Media.ALBUM + "=?", new String[] { albumkey }, MediaStore.Audio.Media.TRACK );
					int totalCount = cursor.getCount();
					parent.setTotalCount( totalCount );

					cursor.moveToPosition( parent.getChildCount() );
					cursor.moveToPrevious();
				}

				int nameIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.TITLE );
				int artistIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.ARTIST );
				int idIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media._ID );
				int pathIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.DATA );
				int sizeIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.SIZE );
				int typeIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.MIME_TYPE );
				int secondsIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.DURATION );
				int trackIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.TRACK );

				String artURL = getArtURLForAlbum( albumkey );

				while ( numReturned < requestCount && cursor.moveToNext() )
				{
					String title = cursor.getString( nameIndex );
					String artist = cursor.getString( artistIndex );
					String id = cursor.getString( idIndex );
					String path = cursor.getString( pathIndex );
					String size = cursor.getString( sizeIndex );
					String type = cursor.getString( typeIndex );
					long seconds = cursor.getLong( secondsIndex );
					int track = cursor.getInt( trackIndex );

					Item item = new Item();
					item.setSelectionState( parent.getSelectionState() == SelectionState.Selected ? SelectionState.Selected : SelectionState.Unselected );
					item.setParent( parent );
					item.setInternal( true );
					item.setObjectID( AUDIO_PREFIX + id );
					item.setUpnpClass( "object.item.audioItem.musicTrack" );
					item.setContentType( ContentType.Audio );
					item.setTitle( title );
					item.setSize( size );
					item.setMimeType( type );
					item.setTotalSeconds( seconds / 1000 );
					item.setResourceURL( Uri.fromFile( new File( path ) ).toString() );
					item.setAlbum( albumkey );
					item.setArtist( artist );
					item.setTrackNumber( track );
					item.setGenre( genreLookup( id ) );
					if ( artURL != null )
					{
						item.setArtURL( Uri.fromFile( new File( artURL ) ).toString() );

						if ( parent.getArtURL() == DirEntry.NOART || parent.getArtURL() == null )
							parent.setArtURL( Uri.fromFile( new File( artURL ) ).toString() );
					}
					parent.getChildren().add( item );
					subList.add( item );
					numReturned++;
				}
			}
			else if ( parent.getObjectID().startsWith( GENRE_PREFIX ) )
			{
				String externalDir = getExternalDir();
				if ( externalDir == null )
				{
					handleNoSDCard( listener );
					return;
				}

				String genrekey = parent.getObjectID().replace( GENRE_PREFIX, "" );

				Uri uri = MediaStore.Audio.Genres.Members.getContentUri( "external", Long.parseLong( genrekey ) );

				if ( cursor == null )
				{
					String[] proj = { MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ARTIST,
							MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.DURATION,
							MediaStore.Audio.Media.TRACK };
					cursor = MainActivity.me.getContentResolver().query( uri, proj, null, null, MediaStore.Audio.Media.TITLE );
					int totalCount = cursor.getCount();
					parent.setTotalCount( totalCount );

					cursor.moveToPosition( parent.getChildCount() );
					cursor.moveToPrevious();
				}

				int nameIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.TITLE );
				int albumIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.ALBUM );
				int artistIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.ARTIST );
				int idIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media._ID );
				int pathIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.DATA );
				int sizeIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.SIZE );
				int typeIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.MIME_TYPE );
				int secondsIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.DURATION );
				int trackIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Media.TRACK );

				while ( numReturned < requestCount && cursor.moveToNext() )
				{
					String title = cursor.getString( nameIndex );
					String album = cursor.getString( albumIndex );
					String artist = cursor.getString( artistIndex );
					String id = cursor.getString( idIndex );
					String path = cursor.getString( pathIndex );
					String size = cursor.getString( sizeIndex );
					String type = cursor.getString( typeIndex );
					long seconds = cursor.getLong( secondsIndex );
					int track = cursor.getInt( trackIndex );

					String artURL = getArtURLForAlbum( album );

					Item item = new Item();
					item.setSelectionState( parent.getSelectionState() == SelectionState.Selected ? SelectionState.Selected : SelectionState.Unselected );
					item.setParent( parent );
					item.setInternal( true );
					item.setObjectID( AUDIO_PREFIX + id );
					item.setUpnpClass( "object.item.audioItem.musicTrack" );
					item.setContentType( ContentType.Audio );
					item.setTitle( title );
					item.setSize( size );
					item.setMimeType( type );
					item.setTotalSeconds( seconds / 1000 );
					item.setResourceURL( Uri.fromFile( new File( path ) ).toString() );
					item.setAlbum( album );
					item.setArtist( artist );
					item.setTrackNumber( track );
					item.setGenre( genreLookup( id ) );
					if ( artURL != null )
					{
						item.setArtURL( Uri.fromFile( new File( artURL ) ).toString() );

						if ( parent.getArtURL() == DirEntry.NOART || parent.getArtURL() == null )
							parent.setArtURL( Uri.fromFile( new File( artURL ) ).toString() );
					}
					parent.getChildren().add( item );
					subList.add( item );
					numReturned++;
				}
			}

			entryCount += numReturned;

			entriesLeft = parent.getTotalCount() - entryCount;

			if ( requestCount == 10 )
				requestCount = REQUEST_COUNT;

			if ( listener != null && !listener.loadMore() )
			{
				if ( cursor != null )
					cursor.close();

				return;
			}

			if ( listener != null )
				listener.onMoreChildren( subList, parent.getTotalCount() );
		}

		if ( cursor != null )
			cursor.close();

		if ( listener != null )
			listener.onDone();
	}

	private Item imagItemFromCursor( Cursor cursor, Container parent, int idIndex, int nameIndex, int pathIndex, int sizeIndex, int typeIndex )
	{
		if ( cursor == null )
			return null;

		String id = cursor.getString( idIndex );
		String title = cursor.getString( nameIndex );
		String path = cursor.getString( pathIndex );
		String size = cursor.getString( sizeIndex );
		String type = cursor.getString( typeIndex );

		Item item = new Item();
		item.setSelectionState( parent.getSelectionState() == SelectionState.Selected ? SelectionState.Selected : SelectionState.Unselected );
		item.setParent( parent );
		item.setInternal( true );
		item.setObjectID( IMAGE_PREFIX + id );
		item.setUpnpClass( "object.item.imageItem.photo" );
		item.setContentType( ContentType.Image );
		item.setTitle( title );
		item.setSize( size );
		item.setMimeType( type );
		item.setResourceURL( Uri.fromFile( new File( path ) ).toString() );
		item.setArtURL( Uri.fromFile( new File( path ) ).toString() );
		if ( parent.getArtURL() == DirEntry.NOART || parent.getArtURL() == null )
			parent.setArtURL( Uri.fromFile( new File( path ) ).toString() );

		return item;
	}

	private Item videoItemFromCursor( Cursor cursor, Container parent, int nameIndex, int idIndex, int pathIndex, int sizeIndex, int typeIndex, int secondsIndex )
	{
		if ( cursor == null )
			return null;

		String title = cursor.getString( nameIndex );
		String id = cursor.getString( idIndex );
		String path = cursor.getString( pathIndex );
		String size = cursor.getString( sizeIndex );
		String type = cursor.getString( typeIndex );
		long seconds = cursor.getLong( secondsIndex );

		Item item = new Item();
		item.setSelectionState( parent.getSelectionState() == SelectionState.Selected ? SelectionState.Selected : SelectionState.Unselected );
		item.setParent( parent );
		item.setInternal( true );
		item.setObjectID( VIDEO_PREFIX + id );
		item.setUpnpClass( "object.item.videoItem" );
		item.setContentType( ContentType.Video );
		item.setTitle( title );
		item.setSize( size );

		// Override media scanner's mimetype if it says its mp4 but ends in .mov
		if ( type != null && type.toLowerCase().equals( "video/mp4" ) && path != null && path.toLowerCase().endsWith( ".mov" ) )
			type = "video/quicktime";

		item.setMimeType( type );
		item.setTotalSeconds( seconds / 1000 );
		item.setResourceURL( Uri.fromFile( new File( path ) ).toString() );
		item.setArtURL( "thumbnail:" + item.getObjectID() );
		if ( parent.getArtURL() == DirEntry.NOART || parent.getArtURL() == null )
			parent.setArtURL( "thumbnail:" + item.getObjectID() );

		// XXX Some videos in 4.0 don't have titles, only paths?
		if ( title == null || title.trim().equals( "" ) )
			item.setTitle( new File( path ).getName() );

		return item;
	}

	private Item audioItemFromCursor( Cursor cursor, Container parent, int nameIndex, int artistIndex, int albumIndex, int idIndex, int pathIndex,
			int sizeIndex, int typeIndex, int secondsIndex, int trackIndex )
	{
		if ( cursor == null )
			return null;

		String title = cursor.getString( nameIndex );
		String artist = cursor.getString( artistIndex );
		String album = cursor.getString( albumIndex );
		String id = cursor.getString( idIndex );
		String path = cursor.getString( pathIndex );
		String size = cursor.getString( sizeIndex );
		String type = cursor.getString( typeIndex );
		long seconds = cursor.getLong( secondsIndex );
		int track = cursor.getInt( trackIndex );

		String artURL = getArtURLForAlbum( album );

		Item item = new Item();
		item.setSelectionState( parent.getSelectionState() == SelectionState.Selected ? SelectionState.Selected : SelectionState.Unselected );
		item.setParent( parent );
		item.setInternal( true );
		item.setObjectID( AUDIO_PREFIX + id );
		item.setUpnpClass( "object.item.audioItem.musicTrack" );
		item.setContentType( ContentType.Audio );
		item.setTitle( title );
		item.setSize( size );
		item.setMimeType( type );
		item.setTotalSeconds( seconds / 1000 );
		item.setResourceURL( Uri.fromFile( new File( path ) ).toString() );
		item.setAlbum( album );
		item.setArtist( artist );
		item.setTrackNumber( track );
		item.setGenre( genreLookup( id ) );
		if ( artURL != null )
		{
			item.setArtURL( Uri.fromFile( new File( artURL ) ).toString() );

			if ( parent.getArtURL() == DirEntry.NOART || parent.getArtURL() == null )
				parent.setArtURL( Uri.fromFile( new File( artURL ) ).toString() );
		}

		return item;
	}

	private Item itemFromPath( File child, Container parent, String externalDir )
	{
		if ( externalDir == null )
			return null;

		Item item = new Item();
		item.setSelectionState( parent.getSelectionState() == SelectionState.Selected ? SelectionState.Selected : SelectionState.Unselected );
		item.setParent( parent );
		item.setInternal( true );
		item.setObjectID( FOLDER_PREFIX + child.getAbsolutePath().replace( externalDir + "/", "" ) );

		String fileext = child.getName().contains( "." ) ? child.getName().substring( child.getName().lastIndexOf( "." ) ).toLowerCase() : "";
		String type = typeFromExt( fileext, "audio/mpeg" );

		if ( type.startsWith( "video" ) )
		{
			item.setUpnpClass( "object.item.videoItem" );
			item.setContentType( ContentType.Video );
		}
		else if ( type.startsWith( "image" ) )
		{
			item.setUpnpClass( "object.item.imageItem.photo" );
			item.setContentType( ContentType.Image );
			item.setArtURL( Uri.fromFile( child ).toString() );
			if ( parent.getArtURL() == DirEntry.NOART || parent.getArtURL() == null )
				parent.setArtURL( Uri.fromFile( child ).toString() );
		}
		else if ( type.startsWith( "audio" ) )
		{
			item.setUpnpClass( "object.item.audioItem.musicTrack" );
			item.setContentType( ContentType.Audio );
		}
		else
		{
			item.setUpnpClass( "object.item" );
			item.setContentType( ContentType.Application );
		}

		item.setMimeType( type );
		item.setTitle( child.getName() );
		item.setSize( "" + child.length() );
		item.setResourceURL( Uri.fromFile( child ).toString() );

		return item;
	}

	Map<String, String> genreMap = null;

	private String genreLookup( String id )
	{
		String rval = null;

		try
		{
			if ( genreMap == null )
			{
				genreMap = new HashMap<String, String>();

				Cursor cursor = MainActivity.me.getContentResolver().query( MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
						new String[] { MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME }, null, null, null );

				for ( cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext() )
				{
					String genreId = cursor.getString( 0 );
					String genreName = cursor.getString( 1 );

					Uri uri = MediaStore.Audio.Genres.Members.getContentUri( "external", Long.parseLong( genreId ) );
					Cursor innerCursor = MainActivity.me.getContentResolver().query( uri, new String[] { MediaStore.Audio.Media._ID }, null, null, null );

					for ( innerCursor.moveToFirst(); !innerCursor.isAfterLast(); innerCursor.moveToNext() )
					{
						String audioId = innerCursor.getString( 0 );
						genreMap.put( audioId, genreName );
					}

					if ( innerCursor != null )
						innerCursor.close();
				}

				if ( cursor != null )
					cursor.close();
			}

			rval = genreMap.get( id );
		}
		catch ( Exception e )
		{
		}

		return rval;
	}

	private void handleNoSDCard( BrowseResultsListener listener )
	{
		if ( listener != null )
			listener.onDone();

		MainActivity.me.getHandler().post( new Runnable()
		{
			public void run()
			{
				Toast.makeText( (Context)MainActivity.me, "No SD Card Found", Toast.LENGTH_LONG ).show();
			}
		} );
	}

	private String typeFromFileName( String filename )
	{
		String fileext = filename.contains( "." ) ? filename.substring( filename.lastIndexOf( "." ) ).toLowerCase() : "";
		return typeFromExt( fileext, "audio/mpeg" );
	}

	private String typeFromExt( String fileext, String defaultType )
	{
		String type = typeMap.get( fileext );

		return type == null ? defaultType : type;
	}

	private static String getArtURLForAlbum( String albumkey )
	{
		if ( albumkey == null )
			return null;

		String externalDir = getExternalDir();
		if ( externalDir == null )
			return null;

		String[] proj = { MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Albums.ALBUM_ART };
		Cursor cursor = MainActivity.me.getContentResolver().query( MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, proj, MediaStore.Audio.Media.ALBUM + "=?",
				new String[] { albumkey }, null );

		String rval = null;

		if ( cursor != null && cursor.moveToFirst() )
		{
			int artIndex = cursor.getColumnIndexOrThrow( MediaStore.Audio.Albums.ALBUM_ART );
			rval = cursor.getString( artIndex );
		}

		if ( cursor != null )
			cursor.close();

		return rval;
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
		return true;
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
				for ( int i = 0; i < searches.length; ++i )
				{
					if ( listener.loadMore() )
					{
						String searchString = String.format( searches[i], query );
						parent.setTotalCount( -1 );
						listener.onMoreChildren( new ArrayList<DirEntry>(), -1 );
						browseDir( parent, searchString, listener, -1 );
					}

					listener.onDone();
				}
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
		imageView.setImageResource( R.drawable.icon );
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

	public static String getThumbnailForVideo( String id )
	{
		String subid = id.replace( VIDEO_PREFIX, "" );

		ContentResolver crThumb = MainActivity.me.getContentResolver();
		Bitmap b = MediaStore.Video.Thumbnails.getThumbnail( crThumb, Long.parseLong( subid ), MediaStore.Video.Thumbnails.MICRO_KIND, null );

		System.out.println( "thumb created: " + b.getWidth() + "x" + b.getHeight() );

		String data = MainActivity.me.getCacheDir() + "/" + id;
		new File( data ).deleteOnExit();

		try
		{
			FileOutputStream out = new FileOutputStream( data );
			b.compress( CompressFormat.JPEG, 100, out );
			out.flush();
			out.close();
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}

		return data;
	}
}
