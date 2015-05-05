package com.plugplayer.plugplayer.upnp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.cybergarage.upnp.UPnP;
import org.cybergarage.xml.Node;
import org.cybergarage.xml.Parser;
import org.cybergarage.xml.ParserException;

import android.net.Uri;
import android.os.Environment;

import com.android.vending.licensing.util.Base64;
import com.plugplayer.plugplayer.activities.MainActivity;
import com.plugplayer.plugplayer.utils.StateMap;

public class Item extends DirEntry
{
	private static final String STATENAME = "Item";

	private transient Node metaDoc = null;
	private transient long linnId;
	private transient AudioFileTypeID typeHint = AudioFileTypeID.Unknown;

	private String metadata = null;

	private boolean internal = false;

	public Item()
	{
		super();
		metaDoc = null;
		typeHint = AudioFileTypeID.Unknown;
		linnId = 0;
	}

	public void setInternal( boolean internal )
	{
		this.internal = internal;
	}

	public boolean isInternal()
	{
		return internal;
	}

	public Item( Node node )
	{
		this();
		metaDoc = node;
	}

	public String generateExternalURL( String baseURL )
	{
		if ( !internal )
			return getMaxResResourceURL();
		else
		{
			if ( getObjectID().startsWith( "folder-" ) )
				return baseURL + "/media/" + Uri.encode( getObjectID(), "/" );
			else
				return baseURL + "/media/" + getObjectID() + getResourceURL().substring( getResourceURL().lastIndexOf( '.' ) );
		}
	}

	public String generateExternalMetadata( String baseURL )
	{
		if ( !internal )
			return metadata;
		else
			return generateExternalMetadata( baseURL, true ).toString();
	}

	public Node generateExternalMetadata( String baseURL, boolean includeDIDL )
	{
		Node p = new Node( "item" );
		p.setAttribute( "id", getObjectID() );

		if ( parent != null )
			p.setAttribute( "parentID", parent.getObjectID() );

		Node t = new Node( "dc:title" );
		t.setNamespace( " xmlns:dc=\"http://purl.org/dc/elements/1.1/\"" );
		t.setValue( getTitle() );
		p.addNode( t );

		Node c = new Node( "upnp:class" );
		c.setNamespace( " xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"" );
		c.setValue( getUpnpClass() );
		p.addNode( c );

		if ( getAlbum() != null && !getAlbum().equals( "<Unknown Album>" ) )
		{
			Node albumNode = new Node( "upnp:album" );
			albumNode.setValue( getAlbum() );
			albumNode.setNamespace( " xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"" );
			p.addNode( albumNode );
		}

		if ( getArtist() != null && !getArtist().equals( "<Unknown Artist>" ) )
		{
			Node artistNode = new Node( "upnp:artist" );
			artistNode.setValue( getArtist() );
			artistNode.setNamespace( " xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"" );
			p.addNode( artistNode );
		}

		if ( getArtURL() != null )
		{
			Node artNode = new Node( "upnp:albumArtURI" );

			if ( getContentType() == ContentType.Image )
				artNode.setValue( baseURL + "/media/" + Uri.encode( getObjectID(), "/" ) + getResourceURL().substring( getResourceURL().lastIndexOf( '.' ) ) );
			else
				artNode.setValue( baseURL + "/thumbnail/" + Uri.encode( getObjectID(), "/" ) );

			artNode.setNamespace( " xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"" );
			p.addNode( artNode );
		}

		if ( getTrackNumber() > 0 )
		{
			Node trackNode = new Node( "upnp:originalTrackNumber" );
			trackNode.setValue( getTrackNumber() );
			trackNode.setNamespace( " xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"" );
			p.addNode( trackNode );
		}

		if ( getGenre() != null && getGenre().length() != 0 )
		{
			Node genreNode = new Node( "upnp:genre" );
			genreNode.setValue( getGenre() );
			genreNode.setNamespace( " xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"" );
			p.addNode( genreNode );
		}

		Node res = new Node( "res" );
		res.setAttribute( "size", getSize() );

		if ( getTotalSeconds() > 0 )
		{
			long hour = getTotalSeconds() / 3600;
			long min = (getTotalSeconds() - hour * 3600) / 60;
			long sec = getTotalSeconds() - hour * 3600 - min * 60;

			String secString = String.format( "%02d:%02d:%02d", hour, min, sec );

			res.setAttribute( "duration", secString );
		}

		res.setAttribute( "protocolInfo", "http-get:*:" + getMimeType() + ":*" );
		if ( getObjectID().startsWith( "folder-" ) )
			res.setValue( baseURL + "/media/" + Uri.encode( getObjectID(), "/" ) );
		else
			res.setValue( baseURL + "/media/" + getObjectID() + getResourceURL().substring( getResourceURL().lastIndexOf( '.' ) ) );
		p.addNode( res );

		if ( includeDIDL )
		{
			Node Result = new Node( "DIDL-Lite" );
			Result.setNamespace( " xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\"" );
			Result.addNode( p );
			return Result;
		}
		else
			return p;
	}

	private void generateMetadata()
	{
		Node Result = new Node( "DIDL-Lite" );
		Result.setNamespace( " xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\"" );

		Node p = new Node( "item" );
		p.setAttribute( "id", getObjectID() );

		Node t = new Node( "dc:title" );
		t.setNamespace( " xmlns:dc=\"http://purl.org/dc/elements/1.1/\"" );
		t.setValue( getTitle() );
		p.addNode( t );

		Node c = new Node( "upnp:class" );
		c.setNamespace( " xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"" );
		c.setValue( getUpnpClass() );
		p.addNode( c );

		if ( getAlbum() != null && !getAlbum().equals( "<Unknown Album>" ) )
		{
			Node albumNode = new Node( "upnp:album" );
			albumNode.setValue( getAlbum() );
			albumNode.setNamespace( " xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"" );
			p.addNode( albumNode );
		}

		if ( getArtist() != null && !getArtist().equals( "<Unknown Artist>" ) )
		{
			Node artistNode = new Node( "upnp:artist" );
			artistNode.setValue( getArtist() );
			artistNode.setNamespace( " xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"" );
			p.addNode( artistNode );
		}

		if ( getArtURL() != null )
		{
			Node artNode = new Node( "upnp:albumArtURI" );
			artNode.setValue( getArtURL() );
			artNode.setNamespace( " xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"" );
			p.addNode( artNode );
		}

		if ( getTrackNumber() > 0 )
		{
			Node trackNode = new Node( "upnp:originalTrackNumber" );
			trackNode.setValue( getTrackNumber() );
			trackNode.setNamespace( " xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"" );
			p.addNode( trackNode );
		}

		if ( getGenre() != null && getGenre().length() != 0 )
		{
			Node genreNode = new Node( "upnp:genre" );
			genreNode.setValue( getGenre() );
			genreNode.setNamespace( " xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"" );
			p.addNode( genreNode );
		}

		Node res = new Node( "res" );
		res.setAttribute( "size", getSize() );

		if ( getTotalSeconds() != 0 )
		{
			long hour = getTotalSeconds() / 3600;
			long min = (getTotalSeconds() - hour * 3600) / 60;
			long sec = getTotalSeconds() - hour * 3600 - min * 60;

			String secString = String.format( "%02d:%02d:%02d", hour, min, sec );

			res.setAttribute( "duration", secString );
		}

		res.setAttribute( "protocolInfo", "http-get:*:" + getMimeType() + ":*" );
		res.setValue( getResourceURL() );
		p.addNode( res );

		Result.addNode( p );

		metadata = Result.toString();
	}

	public void toState( StateMap state )
	{
		state.setName( STATENAME );

		if ( metadata == null )
			generateMetadata();

		state.setValue( "metadata", metadata );
		state.setValue( "internal", internal );
		state.setValue( "linnId", (int)linnId );
	}

	public void fromState( StateMap state )
	{
		metadata = state.getValue( "metadata", "" );
		internal = state.getValue( "internal", false );
		linnId = state.getValue( "linnId", 0 );

		if ( getArtURL() == null && getContentType() == ContentType.Image )
		{
			setArtURL( getResourceURL() );
		}
	}

	public static Item createFromState( StateMap state )
	{
		Item rval = new Item();
		rval.fromState( state );
		return rval;
	}

	@Override
	public String getObjectID()
	{
		String objectID = super.getObjectID();

		if ( objectID == null )
		{
			try
			{
				objectID = getMetadataDoc().getAttributeValue( "id" );
				super.setObjectID( objectID );
			}
			catch ( Exception e )
			{
			}
		}

		return objectID;
	}

	@Override
	public String getTitle()
	{
		String title = super.getTitle();

		if ( title == null )
		{
			try
			{
				title = getMetadataDoc().getNodeValue( "dc:title" );
				super.setTitle( title );
			}
			catch ( Exception e )
			{
			}
		}

		return title;
	}

	@Override
	public String getUpnpClass()
	{
		String _class = super.getUpnpClass();

		if ( _class == null )
		{
			try
			{
				_class = getMetadataDoc().getNodeValue( "upnp:class" );
				super.setUpnpClass( _class );
			}
			catch ( Exception e )
			{
			}
		}

		return _class;
	}

	public String getMetadata()
	{
		return metadata;
	}

	public void setMetadata( String metadata )
	{
		this.metadata = metadata;
	}

	private Node getMetadataDoc() throws ParserException
	{
		if ( metaDoc == null )
		{
			Parser parser = UPnP.getXMLParser();
			metaDoc = parser.parse( metadata ).getNode( 0 );
		}

		return metaDoc;
	}

	public static enum AudioFileTypeID
	{
		Unknown, kAudioFileMPEG4Type, kAudioFileM4AType, kAudioFileMP3Type, kAudioFileAAC_ADTSType, kAudioFileAIFFType, kAudioFileAIFCType, kAudioFileAMRType, kAudioFileWAVEType, kAudioFileCAFType, kAudioFileVorbisType, kAudioFileFLACType
	}

	public long getLinnId()
	{
		return linnId;
	}

	public void setLinnId( long linnId )
	{
		this.linnId = linnId;
	}

	public AudioFileTypeID getTypeHint()
	{
		// getting the URL will also calculate the AudioFileTypeID
		getResourceURL();

		return typeHint;
	}

	// XXXX Adjust these for what Android can play
	public AudioFileTypeID getTypeHintFromNode( Node res )
	{
		AudioFileTypeID rval = AudioFileTypeID.Unknown;

		String protocolInfo = res.getAttributeValue( "protocolInfo" );

		if ( protocolInfo != null )
		{
			String infos[] = protocolInfo.split( ":" );
			if ( infos.length > 2 )
			{
				String mimeType = infos[2].toLowerCase();

				if ( mimeType.equals( "audio/mp4" ) )
					rval = AudioFileTypeID.kAudioFileMPEG4Type;
				else if ( mimeType.equals( "audio/x-m4a" ) || mimeType.equals( "audio/x-m4b" ) || mimeType.equals( "audio/x-m4p" ) )
					rval = AudioFileTypeID.kAudioFileM4AType;
				else if ( mimeType.equals( "audio/mpeg" ) || mimeType.equals( "audio/mpeg3" ) || mimeType.equals( "audio/mpg" )
						|| mimeType.equals( "audio/x-mp3" ) || mimeType.equals( "audio/x-mpeg3" ) || mimeType.equals( "audio/x-mpeg" )
						|| mimeType.equals( "audio/x-mpg" ) )
					rval = AudioFileTypeID.kAudioFileMP3Type;
				else if ( mimeType.equals( "audio/aac" ) || mimeType.equals( "audio/x-aac" ) )
					rval = AudioFileTypeID.kAudioFileAAC_ADTSType;
				else if ( mimeType.equals( "audio/aiff" ) || mimeType.equals( "audio/x-aiff" ) )
					rval = AudioFileTypeID.kAudioFileAIFFType;
				else if ( mimeType.equals( "audio/aifc" ) || mimeType.equals( "audio/x-aifc" ) )
					rval = AudioFileTypeID.kAudioFileAIFCType;
				else if ( mimeType.equals( "audio/amr" ) )
					rval = AudioFileTypeID.kAudioFileAMRType;
				else if ( mimeType.equals( "audio/wav" ) || mimeType.equals( "audio/x-wav" ) )
					rval = AudioFileTypeID.kAudioFileWAVEType;
				else if ( mimeType.equals( "audio/x-caf" ) )
					rval = AudioFileTypeID.kAudioFileCAFType;
				else if ( mimeType.equals( "application/ogg" ) || mimeType.equals( "audio/ogg" ) || mimeType.equals( "audio/vorbis" ) )
					rval = AudioFileTypeID.kAudioFileVorbisType;
				// else if ( [mimeType isEqual:@"audio/x-ms-wma"] )
				// rval = kAudioFileWMAType;
				else if ( mimeType.equals( "audio/flac" ) || mimeType.equals( "audio/x-flac" ) )
					rval = AudioFileTypeID.kAudioFileFLACType;
			}
		}

		return rval;
	}

	String resourceURL = null;

	public void setResourceURL( String resourceURL )
	{
		this.resourceURL = resourceURL;
	}

	public String getResourceURL()
	{
		return getResourceURL( MainActivity.defaultSize );
	}

	public String getResourceURL( int targetSize )
	{
		if ( resourceURL != null )
			return resourceURL;

		String url;
		ContentType content = getContentType();

		if ( content == ContentType.Video )
			url = resourceURLForVideo();
		else if ( content == ContentType.Image )
			url = resourceURLForImage( targetSize, true );
		else
			url = resourceURLForAudio();

		// This is an internal resource sent to us from an external control point
		if ( url != null && url.contains( AndroidServer.getBaseURL() ) )
		{
			url = AndroidServer.generateInternalURL( url );
			setResourceURL( url );
		}

		return url;
	}

	public String getMaxResResourceURL()
	{
		return getResourceURL( Integer.MAX_VALUE );
	}

	private String resourceURLForAudio()
	{
		String rval = null;

		try
		{
			if ( metaDoc == null )
			{
				Parser parser = UPnP.getXMLParser();
				metaDoc = parser.parse( metadata ).getNode( 0 );
			}

			for ( int i = 0; i < metaDoc.getNNodes(); ++i )
			{
				Node tmpNode = metaDoc.getNode( i );
				if ( tmpNode.getName().equals( "res" ) )
				{
					AudioFileTypeID type = getTypeHintFromNode( tmpNode );
					if ( type != AudioFileTypeID.Unknown )
					{
						typeHint = type;
						rval = tmpNode.getValue();
						break;
					}
				}
			}

			if ( rval == null )
				rval = metaDoc.getNodeValue( "res" );

			// if ( res )
			// rval = [NSURL URLWithString:[NSString stringWithUTF8String:res]];
			//
			// if ( !rval && res )
			// rval = [NSURL URLWithString:[[NSString stringWithUTF8String:res] stringByAddingPercentEscapesUsingEncoding:NSUTF8StringEncoding]];
		}
		catch ( Exception e )
		{

		}

		return rval;
	}

	private String resourceURLForImage( int targetSize, boolean preferOversized )
	{
		String bestUnderURL = null;
		String bestOverURL = null;

		int bestUnderDistance = Integer.MIN_VALUE;
		int bestOverDistance = Integer.MAX_VALUE;

		try
		{
			if ( metaDoc == null )
			{
				Parser parser = UPnP.getXMLParser();
				metaDoc = parser.parse( metadata ).getNode( 0 );
			}

			for ( int i = 0; i < metaDoc.getNNodes(); ++i )
			{
				Node tmpNode = metaDoc.getNode( i );
				if ( tmpNode.getName().equals( "res" ) )
				{
					String resolution = tmpNode.getAttributeValue( "resolution" );
					int x = 0, y = 0;
					int max = 0;
					if ( resolution != null && resolution.contains( "x" ) )
					{
						String xy[] = resolution.split( "x" );
						x = Integer.parseInt( xy[0] );
						y = Integer.parseInt( xy[1] );
						max = Math.max( x, y );
					}

					int distance = max - targetSize;

					if ( distance <= 0 && distance > bestUnderDistance )
					{
						bestUnderDistance = distance;
						bestUnderURL = tmpNode.getValue();
						if ( bestUnderURL == null )
						{
							bestUnderURL = metaDoc.getNodeValue( "res" );
						}
					}
					else if ( distance >= 0 && distance < bestOverDistance )
					{
						bestOverDistance = distance;
						bestOverURL = tmpNode.getValue();
						if ( bestOverURL == null )
						{
							bestOverURL = metaDoc.getNodeValue( "res" );
						}
					}
				}
			}

		}
		catch ( Exception e )
		{

		}

		if ( preferOversized && Math.abs( bestUnderDistance / (float)targetSize ) > 0.1 )
		{
			// System.out.println( "returning oversized, over = " + bestOverDistance + ", under = " + bestUnderDistance + ", size = " + targetSize );
			return bestOverURL != null ? bestOverURL : bestUnderURL;
		}
		else
		{
			// System.out.println( "returning undersized, over = " + bestOverDistance + ", under = " + bestUnderDistance + ", size = " + targetSize );
			return bestUnderURL != null ? bestUnderURL : bestOverURL;
		}
	}

	private String resourceURLForVideo()
	{
		String rval = null;

		try
		{
			if ( metaDoc == null )
			{
				Parser parser = UPnP.getXMLParser();
				metaDoc = parser.parse( metadata ).getNode( 0 );
			}

			rval = metaDoc.getNodeValue( "res" );

			// if ( res )
			// rval = [NSURL URLWithString:[NSString stringWithUTF8String:res]];
			//
			// if ( !rval && res )
			// rval = [NSURL URLWithString:[[NSString stringWithUTF8String:res] stringByAddingPercentEscapesUsingEncoding:NSUTF8StringEncoding]];
		}
		catch ( Exception e )
		{

		}

		return rval;
	}

	public static enum ContentType
	{
		Audio, Video, Image, Application
	}

	ContentType contentType = null;

	public void setContentType( ContentType contentType )
	{
		this.contentType = contentType;
	}

	public ContentType getContentType()
	{
		if ( contentType == null )
		{
			contentType = ContentType.Audio;

			String mimeType = getMimeType();
			if ( mimeType != null )
			{
				if ( mimeType.startsWith( "video" ) || mimeType.equals( "application/x-mpegurl" ) )
					contentType = ContentType.Video;
				else if ( mimeType.startsWith( "image" ) )
					contentType = ContentType.Image;
				else if ( mimeType.startsWith( "audio" ) )
					contentType = ContentType.Audio;
				else
					// if ( mimeType.startsWith( "application" ) )
					contentType = ContentType.Application;
			}
		}

		return contentType;
	}

	String album = null;

	public void setAlbum( String album )
	{
		this.album = album;
	}

	public String getAlbum()
	{
		if ( album == null )
		{
			try
			{
				if ( metaDoc == null )
				{
					Parser parser = UPnP.getXMLParser();
					metaDoc = parser.parse( metadata ).getNode( 0 );
				}

				album = metaDoc.getNodeValue( "upnp:album" );

				if ( album == null || album.length() == 0 )
					album = "<Unknown Album>";
			}
			catch ( Exception e )
			{
				album = "<Unknown Album>";
			}
		}

		return album;
	}

	String artist = null;

	public void setArtist( String artist )
	{
		this.artist = artist;
	}

	public String getArtist()
	{
		if ( artist == null )
		{
			try
			{
				if ( metaDoc == null )
				{
					Parser parser = UPnP.getXMLParser();
					metaDoc = parser.parse( metadata ).getNode( 0 );
				}

				artist = metaDoc.getNodeValue( "upnp:artist" );

				if ( artist == null || artist.length() == 0 )
					artist = "<Unknown Artist>";
			}
			catch ( Exception e )
			{
				artist = "<Unknown Artist>";
			}
		}

		return artist;
	}

	@Override
	public String getArtURL()
	{
		String rval = super.getArtURL();

		if ( rval != null )
			return rval;

		try
		{
			if ( metaDoc == null )
			{
				Parser parser = UPnP.getXMLParser();
				metaDoc = parser.parse( metadata ).getNode( 0 );
			}

			rval = metaDoc.getNodeValue( "upnp:albumArtURI" );

			if ( rval != null && rval.length() > 0 )
			{
				if ( rval.contains( AndroidServer.getBaseURL() ) )
					rval = AndroidServer.generateInternalURL( rval );

				setArtURL( rval );
				return rval;
			}
		}
		catch ( Exception e )
		{
		}

		setArtURL( NOART );
		return null;
	}

	String mimeType = null;

	public void setMimeType( String mimeType )
	{
		this.mimeType = mimeType;
	}

	public String getMimeType()
	{
		if ( mimeType != null )
			return mimeType;

		try
		{
			if ( metaDoc == null )
			{
				Parser parser = UPnP.getXMLParser();
				metaDoc = parser.parse( metadata ).getNode( 0 );
			}

			Node res = metaDoc.getNode( "res" );
			String protocolInfo = res.getAttributeValue( "protocolInfo" );

			if ( protocolInfo != null )
			{
				String infos[] = protocolInfo.split( ":" );
				if ( infos.length > 2 )
				{
					mimeType = infos[2].toLowerCase();
				}
			}
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}

		return mimeType;
	}

	public File copyToExternalFile()
	{
		try
		{
			File dir = Environment.getExternalStorageDirectory();
			dir = new File( dir.getAbsolutePath() + "/Android/data/" + MainActivity.appBundle + "/cache/" );
			dir.mkdirs();
			// File tmpfile = File.createTempFile( "controller", "item", dir );
			File tmpfile = new File( dir, getTitle() );
			tmpfile.deleteOnExit();
			return copyToFile( tmpfile );
		}
		catch ( Exception e )
		{
			e.printStackTrace();
			return null;
		}
	}

	public File copyToTmpFile()
	{
		try
		{
			File tmpfile = File.createTempFile( "controller", "item", MainActivity.me.getCacheDir() );
			tmpfile.deleteOnExit();
			return copyToFile( tmpfile );
		}
		catch ( Exception e )
		{
			e.printStackTrace();
			return null;
		}
	}

	public File copyToFile( File tmpfile )
	{
		try
		{
			ContentType content = getContentType();

			String urlString;
			if ( content == ContentType.Image )
				urlString = resourceURLForImage( MainActivity.defaultSize, true );
			else
				urlString = getResourceURL();

			URL url = new URL( urlString );
			String userInfo = url.getUserInfo();

			HttpURLConnection httpConn = (HttpURLConnection)url.openConnection();

			if ( userInfo != null )
			{
				String authStringEnc = Base64.encode( userInfo.getBytes() );
				httpConn.setRequestProperty( "Authorization", "Basic " + authStringEnc );
			}

			InputStream is = httpConn.getInputStream();
			FileOutputStream fos = new FileOutputStream( tmpfile );

			byte buf[] = new byte[16 * 1024];
			do
			{
				int numread = is.read( buf );
				if ( numread <= 0 )
					break;
				fos.write( buf, 0, numread );
			} while ( true );
			fos.flush();
			fos.close();
			return tmpfile;
		}
		catch ( Exception e )
		{
			e.printStackTrace();
			return null;
		}
	}

	String size = null;

	public void setSize( String size )
	{
		this.size = size;
	}

	public String getSize()
	{
		if ( size == null )
		{
			try
			{
				size = MediaDevice.getNodeAttribute( getMetadataDoc(), "res", "size" );
			}
			catch ( Exception e )
			{
			}
		}

		return size;
	}

	long totalSeconds = -1;

	public void setTotalSeconds( long totalSeconds )
	{
		this.totalSeconds = totalSeconds;
	}

	public long getTotalSeconds()
	{
		if ( totalSeconds == -1 )
		{
			try
			{
				String duration = MediaDevice.getNodeAttribute( getMetadataDoc(), "res", "duration" );
				totalSeconds = MediaDevice.secsFromString( duration );
			}
			catch ( Exception e )
			{
			}
		}

		return totalSeconds;
	}

	int trackNumber = -1;

	public void setTrackNumber( int track )
	{
		trackNumber = track;
	}

	public int getTrackNumber()
	{
		if ( trackNumber == -1 )
		{
			try
			{
				String trackString = getMetadataDoc().getNodeValue( "upnp:originalTrackNumber" );
				trackNumber = Integer.parseInt( trackString );
			}
			catch ( Exception e )
			{
			}
		}

		return trackNumber;
	}

	String genre = null;

	public void setGenre( String genre )
	{
		this.genre = genre;
	}

	public String getGenre()
	{
		if ( genre == null )
		{
			try
			{
				genre = getMetadataDoc().getNodeValue( "upnp:genre" );
			}
			catch ( Exception e )
			{
			}
		}

		return genre;
	}
}
