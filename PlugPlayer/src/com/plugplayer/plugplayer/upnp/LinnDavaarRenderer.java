package com.plugplayer.plugplayer.upnp;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cybergarage.upnp.Action;
import org.cybergarage.upnp.Device;
import org.cybergarage.upnp.Service;
import org.cybergarage.upnp.UPnP;
import org.cybergarage.xml.Node;
import org.cybergarage.xml.Parser;
import org.cybergarage.xml.ParserException;

import com.plugplayer.plugplayer.upnp.Item.ContentType;
import com.plugplayer.plugplayer.utils.StateMap;

public class LinnDavaarRenderer extends Renderer implements LinnRenderer
{
	public static final String STATENAME = "LinnDavaarRenderer";

	public static enum SourceType
	{
		Unknown, Playlist, Radio, Songcast, Other
	}

	//
	private int currentTrack = -1;
	private float volume = 0;
	private int totalSeconds = -1;

	//
	private Service playlistService = null;
	private Service timeService = null;
	private Service productService = null;
	private Service radioService = null;
	private Service renderingControl = null;
	private Service avtransportService = null;
	private Service senderService = null;
	private Service receiverService = null;
	private Service infoService = null;
	private char idArrayString[] = null;
	private List<Long> idArray = null;
	private final Map<Long, Item> trackmap;
	private boolean repeat = false;
	private boolean shuffle = false;

	private SourceType currentSource;
	private char radioIdArrayString[] = null;
	private final Map<Long, Item> radioTrackmap;
	private PlayState radioPlaystate;
	private int radioChannel = -1;

	@Override
	public int getTrackNumber()
	{
		if ( getCurrentSourceType() == SourceType.Radio )
			return radioChannel;
		else if ( getCurrentSourceType() == SourceType.Songcast )
			return 0;
		else
			return currentTrack;
	}

	PlayState getSongcastPlayState()
	{
		PlayState rval = PlayState.NoMedia;

		Action transportState = receiverService.getAction( "TransportState" );

		if ( transportState.postControlAction() )
		{
			String transportStateString = transportState.getArgumentValue( "Value" );
			if ( transportStateString != null )
			{
				if ( transportStateString.equals( "Stopped" ) )
					rval = PlayState.Stopped;
				else if ( transportStateString.equals( "Playing" ) || transportStateString.equals( "Buffering" ) )
					rval = PlayState.Playing;
				else if ( transportStateString.equals( "Paused" ) )
					rval = PlayState.Paused;
			}
		}
		else
		{
			handleError( transportState );
		}

		return rval;
	}

	PlayState getRadioPlayState()
	{
		PlayState rval = PlayState.NoMedia;

		Action transportState = radioService.getAction( "TransportState" );

		if ( transportState.postControlAction() )
		{
			String transportStateString = transportState.getArgumentValue( "Value" );
			if ( transportStateString != null )
			{
				if ( transportStateString.equals( "Stopped" ) )
					rval = PlayState.Stopped;
				else if ( transportStateString.equals( "Playing" ) || transportStateString.equals( "Buffering" ) )
					rval = PlayState.Playing;
				else if ( transportStateString.equals( "Paused" ) )
					rval = PlayState.Paused;
			}
		}
		else
		{
			handleError( transportState );
		}

		return rval;
	}

	PlayState getCurrentPlayState()
	{
		PlayState rval = PlayState.NoMedia;

		Action transportState = playlistService.getAction( "TransportState" );

		if ( transportState.postControlAction() )
		{
			String transportStateString = transportState.getArgumentValue( "Value" );
			if ( transportStateString != null )
			{
				if ( transportStateString.equals( "Stopped" ) )
					rval = PlayState.Stopped;
				else if ( transportStateString.equals( "Playing" ) || transportStateString.equals( "Buffering" ) )
					rval = PlayState.Playing;
				else if ( transportStateString.equals( "Paused" ) )
					rval = PlayState.Paused;
			}
		}
		else
		{
			handleError( transportState );
		}

		return rval;
	}

	@Override
	public boolean supportsSeek()
	{
		return true;
	}

	@Override
	public boolean seekToSecond( int second )
	{
		if ( currentSource != null && currentSource != SourceType.Playlist )
			return false;

		Action seekSecondAbsolute = playlistService.getAction( "SeekSecondAbsolute" );
		seekSecondAbsolute.setArgumentValue( "Value", second );

		if ( seekSecondAbsolute.postControlAction() )
		{
			return true;
		}
		else
		{
			handleError( seekSecondAbsolute );
			return false;
		}
	}

	float getCurrentVolume()
	{
		if ( renderingControl == null )
			return 0;

		float rval = 0;

		Action getVolume = renderingControl.getAction( "GetVolume" );
		getVolume.setArgumentValue( "InstanceID", 0 );
		getVolume.setArgumentValue( "Channel", "Master" );

		if ( getVolume.postControlAction() )
		{
			String volString = getVolume.getArgumentValue( "CurrentVolume" );
			if ( volString != null )
				rval = Integer.parseInt( volString ) / 100.0f;
		}
		else
		{
			handleError( getVolume );
		}

		return rval;
	}

	@Override
	public void setVolume( float newVolume )
	{
		Action setVolume = renderingControl.getAction( "SetVolume" );
		setVolume.setArgumentValue( "InstanceID", 0 );
		setVolume.setArgumentValue( "Channel", "Master" );
		setVolume.setArgumentValue( "DesiredVolume", (int)(100 * newVolume) );

		if ( setVolume.postControlAction() )
		{
			volume = newVolume;
			mute = false;
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
		float tmp = getVolume() + 0.1f;
		setVolume( Math.min( 1.0f, tmp ) );
	}

	@Override
	public void volumeDec()
	{
		float tmp = getVolume() - 0.1f;
		setVolume( Math.max( 0.0f, tmp ) );
	}

	int base64Value( char base64 )
	{
		char encoding[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

		for ( int i = 0; i < 64; ++i )
			if ( encoding[i] == base64 )
				return i;

		return 255;
	}

	long radioIdAtIndex( int index )
	{
		if ( index >= getRadioEntryCount() )
			return 0;

		long rval = 0;

		int bitOffset = index * 4 * 8;
		int charOffset = bitOffset / 6;
		int bitsfromchunk = 6 - (bitOffset - (charOffset * 6));
		rval = (base64Value( radioIdArrayString[charOffset] ) << (2 + 6 - bitsfromchunk)) << 24;

		charOffset++;
		int bits = bitsfromchunk;
		while ( bits < 32 )
		{
			long bits6 = base64Value( radioIdArrayString[charOffset] ) << 2;
			if ( bits > 24 )
				rval |= bits6 >> (bits - 24);
			else
				rval |= bits6 << (24 - bits);
			bits += 6;
			charOffset++;
		}

		return rval;
	}

	long idAtIndex( int index )
	{
		if ( index >= getPlaylistCount() || index < 0 )
			return 0;

		long rval = 0;

		if ( idArray != null )
		{
			rval = idArray.get( index );
		}
		else
		{
			int bitOffset = index * 4 * 8;
			int charOffset = bitOffset / 6;
			int bitsfromchunk = 6 - (bitOffset - (charOffset * 6));
			rval = (base64Value( idArrayString[charOffset] ) << (2 + 6 - bitsfromchunk)) << 24;

			charOffset++;
			int bits = bitsfromchunk;
			while ( bits < 32 )
			{
				long bits6 = base64Value( idArrayString[charOffset] ) << 2;
				if ( bits > 24 )
					rval |= bits6 >> (bits - 24);
				else
					rval |= bits6 << (24 - bits);
				bits += 6;
				charOffset++;
			}
		}

		return rval;
	}

	int radioIndexFromTrackId( long trackId )
	{
		int count = getRadioEntryCount();
		for ( int i = 0; i < count; ++i )
		{
			if ( trackId == radioIdAtIndex( i ) )
				return i;
		}

		return -1;
	}

	int indexFromTrackId( long trackId )
	{
		int count = getPlaylistEntryCount();
		for ( int i = 0; i < count; ++i )
		{
			if ( trackId == idAtIndex( i ) )
				return i;
		}

		return -1;
	}

	int getCurrentTrack()
	{
		int rval = -1;

		Action state = playlistService.getAction( "Id" );

		if ( state.postControlAction() )
		{
			String trackString = state.getArgumentValue( "Value" );
			if ( trackString != null )
			{
				rval = indexFromTrackId( Long.parseLong( trackString ) );
			}
		}
		else
		{
			handleError( state );
		}

		return rval;
	}

	int getRadioChannel()
	{
		int rval = -1;

		Action state = radioService.getAction( "Id" );

		if ( state.postControlAction() )
		{
			String trackString = state.getArgumentValue( "Value" );
			if ( trackString != null )
			{
				rval = radioIndexFromTrackId( Long.parseLong( trackString ) );
			}
		}
		else
		{
			handleError( state );
		}

		return rval;
	}

	void updateIdArray()
	{
		Action IdArray = playlistService.getAction( "IdArray" );

		if ( IdArray.postControlAction() )
		{
			idArray = null;
			idArrayString = null;
			String tmp = IdArray.getArgumentValue( "Array" );
			if ( tmp != null )
				idArrayString = tmp.toCharArray();
		}
		else
		{
			handleError( IdArray );
		}
	}

	void updateRadioIdArray()
	{
		Action idArray = radioService.getAction( "IdArray" );

		if ( idArray.postControlAction() )
		{
			radioIdArrayString = null;
			String tmp = idArray.getArgumentValue( "Array" );
			if ( tmp != null )
				radioIdArrayString = tmp.toCharArray();
		}
		else
		{
			handleError( idArray );
		}
	}

	synchronized void updateIdArrayWith( long newId, int index )
	{
		if ( idArray == null )
		{
			int count = getPlaylistEntryCount();

			ArrayList<Long> tmpIdArray = new ArrayList<Long>();

			for ( int i = 0; i < count; ++i )
				tmpIdArray.add( idAtIndex( i ) );

			idArray = tmpIdArray;
		}

		idArray.add( index, newId );
	}

	synchronized void removeFromIdArray( int index )
	{
		if ( idArray == null )
		{
			int count = getPlaylistEntryCount();

			ArrayList<Long> tmpIdArray = new ArrayList<Long>();

			for ( int i = 0; i < count; ++i )
				tmpIdArray.add( idAtIndex( i ) );

			idArray = tmpIdArray;
		}

		idArray.remove( index );
	}

	void insertPlaylistEntry( Item newEntry, int index, boolean listener )
	{
		long afterId = 0;

		if ( index != 0 )
		{
			Item afterItem = getPlaylistEntry( index - 1 );
			afterId = afterItem.getLinnId();
		}

		// XXX TODO: Add back once UPnP.parser supports printing namespaces in toString
		// IXML_Document *metadoc = ixmlParseBuffer( newEntry.metadata );
		// IXML_Node *parent = ixmlNode_getFirstChild( ixmlNode_getFirstChild( (IXML_Node*)metadoc ) );
		// IXML_NodeList *items = ixmlNode_getChildNodes( parent );
		//
		// IXML_Node *attr = parent->firstAttr;
		// while ( attr )
		// {
		// if ( attr->nodeName && strstr( attr->nodeName, "dlna:" ) )
		// attr = removeAttribute( attr, parent );
		// else
		// attr = attr->nextSibling;
		// }
		//
		// for( int i = 0; i < ixmlNodeList_length( items ); ++i )
		// {
		// IXML_Node *child = ixmlNodeList_item( items, i );
		//
		// if ( child && child->nodeName && strstr( child->nodeName, "pv:" ) )
		// ixmlNode_removeChild( parent, child, NULL );
		// else if ( child )
		// {
		// IXML_Node *attr = child->firstAttr;
		// while ( attr )
		// {
		// if ( attr->nodeName && strstr( attr->nodeName, "dlna:" ) )
		// attr = removeAttribute( attr, child );
		// else
		// attr = attr->nextSibling;
		// }
		// }
		// }

		String metadata = newEntry.generateExternalMetadata( AndroidServer.getBaseURL() );

		Action insert = playlistService.getAction( "Insert" );
		insert.setArgumentValue( "AfterId", "" + afterId );
		insert.setArgumentValue( "Uri", newEntry.getMaxResResourceURL() );
		insert.setArgumentValue( "Metadata", metadata );

		if ( insert.postControlAction() )
		{
			long newId = 0;
			String newIdStr = insert.getArgumentValue( "NewId" );
			if ( newIdStr != null )
				newId = Long.parseLong( newIdStr );

			newEntry.setLinnId( newId );
			trackmap.put( newId, newEntry );

			updateIdArrayWith( newId, index );

			if ( listener )
				emitPlaylistChanged();
		}
		else
		{
			handleError( insert );
		}
	}

	@Override
	public void insertPlaylistEntry( Item newEntry, int index )
	{
		insertPlaylistEntry( newEntry, index, true );
	}

	void removeAllPlaylistEntries()
	{
		Action deleteAll = playlistService.getAction( "DeleteAll" );

		if ( deleteAll.postControlAction() )
		{
			idArrayString = null;
			emitPlaylistChanged();
		}
		else
		{
			handleError( deleteAll );
		}
	}

	void removePlaylistEntryById( long trackId, int index, boolean listener )
	{
		Action delete = playlistService.getAction( "DeleteId" );
		delete.setArgumentValue( "Value", "" + trackId );

		if ( delete.postControlAction() )
		{
			trackmap.remove( trackId );
			removeFromIdArray( index );

			if ( index < currentTrack )
				currentTrack = currentTrack - 1;

			if ( listener )
				emitPlaylistChanged();
		}
		else
		{
			handleError( delete );
		}
	}

	@Override
	public void removePlaylistEntry( int index )
	{
		Item item = getPlaylistEntry( index );
		removePlaylistEntryById( item.getLinnId(), index, true );
	}

	@Override
	synchronized public void movePlaylistEntry( int fromIndex, int toIndex )
	{
		Item item = getPlaylistEntry( fromIndex );
		removePlaylistEntryById( item.getLinnId(), fromIndex, false );
		insertPlaylistEntry( item, toIndex, false );
	}

	@Override
	public void removePlaylistEntries( int index, int count )
	{
		if ( count == getPlaylistEntryCount() )
			removeAllPlaylistEntries();
		else
		{
			// XXX This clearly doesn't work, but I don't think it is every called
			long list[] = new long[1024]; // Cara playlists currently limited to 1000 tracks
			for ( int i = 0; i < count; ++i )
				list[i] = getPlaylistEntry( index + count ).getLinnId();
		}
	}

	Item getPlaylistEntryFromServer( long trackId )
	{
		Item rval = null;

		Action read = playlistService.getAction( "Read" );
		read.setArgumentValue( "Id", "" + trackId );

		if ( read.postControlAction() )
		{
			rval = new Item();

			String metadataStr = read.getArgumentValue( "Metadata" );
			rval.setMetadata( metadataStr );

			try
			{
				Node n = UPnP.getXMLParser().parse( metadataStr ).getNode( 0 );

				String classStr = n.getNodeValue( "upnp:class" );
				String titleStr = n.getNodeValue( "dc:title" );

				rval.setTitle( titleStr != null ? titleStr : "<No Name>" );
				rval.setUpnpClass( classStr != null ? classStr : "" );

				if ( rval.getArtURL() == null && rval.getContentType() == ContentType.Image )
				{
					rval.setArtURL( rval.getResourceURL() );
				}
			}
			catch ( Exception e )
			{
			}

			rval.setLinnId( trackId );
			trackmap.put( trackId, rval );
		}
		else
		{
			handleError( read );
		}

		return rval;
	}

	Item getRadioEntryFromServer( long trackId )
	{
		Item rval = null;

		Action read = radioService.getAction( "Read" );
		read.setArgumentValue( "Id", "" + trackId );

		if ( read.postControlAction() )
		{
			rval = new Item();

			String metadataStr = read.getArgumentValue( "Metadata" );

			if ( metadataStr != null )
			{
				try
				{
					Node metadoc = UPnP.getXMLParser().parse( metadataStr );

					Node item = metadoc.getNode( "item" );

					Node child = new Node( "upnp", "album" );
					child.setValue( "Radio" );
					item.addNode( child );

					String bitrate = "";
					String bitrateStr = item.getNode( "res" ).getAttributeValue( "bitrate" );
					if ( bitrateStr != null )
						bitrate = ((Integer.parseInt( bitrateStr ) * 8) / 1000) + " Kbps";

					child = new Node( "upnp", "artist" );
					child.setValue( bitrate );
					item.addNode( child );

					rval.setMetadata( metadoc.toString() );
				}
				catch ( Exception e )
				{
				}
			}
			else
			{
				handleError( read );
			}

			rval.setLinnId( trackId );
			radioTrackmap.put( trackId, rval );
		}

		return rval;
	}

	Item getRadioEntry( int index )
	{
		long trackId = radioIdAtIndex( index );

		Item rval = radioTrackmap.get( trackId );

		if ( rval == null )
			rval = getRadioEntryFromServer( trackId );

		return rval;
	}

	Item songCastEntryFromMetadata( String metadata )
	{
		Item rval = new Item();
		rval.setMetadata( metadata );
		return rval;
	}

	private Item songcastEntry = null;

	Item getSongcastEntry( int index )
	{
		if ( songcastEntry == null )
		{
			String metadata = getSongcastMetadata();
			songcastEntry = songCastEntryFromMetadata( metadata );
		}

		return songcastEntry;
	}

	@Override
	public Item getPlaylistEntry( int index )
	{
		if ( currentSource == SourceType.Radio )
			return getRadioEntry( index );
		if ( currentSource == SourceType.Songcast )
			return getSongcastEntry( index );

		long trackId;

		synchronized ( this )
		{
			if ( idArray != null )
				trackId = idArray.get( index );
			else
				trackId = idAtIndex( index );
		}

		Item rval = trackmap.get( trackId );

		if ( rval == null )
			rval = getPlaylistEntryFromServer( trackId );

		return rval;
	}

	int getRadioEntryCount()
	{
		return radioIdArrayString != null ? radioIdArrayString.length * 6 / 8 / 4 : 0;
	}

	int getPlaylistCount()
	{
		return idArray != null ? idArray.size() : (idArrayString != null ? idArrayString.length * 6 / 8 / 4 : 0);
	}

	@Override
	public int getPlaylistEntryCount()
	{
		if ( currentSource == SourceType.Radio )
			return getRadioEntryCount();
		else if ( currentSource == SourceType.Songcast )
			return 1;
		else
			return getPlaylistCount();
	}

	// This is only used for saving playlists
	List<Item> getPlaylist()
	{
		List<Item> rval = new ArrayList<Item>();

		int count = getPlaylistEntryCount();

		for ( int i = 0; i < count; ++i )
			rval.add( getPlaylistEntry( i ) );

		return rval;
	}

	@Override
	public void setTrackNumber( int newTrackNumber )
	{
		if ( getCurrentSourceType() == SourceType.Songcast )
		{
			play();
		}
		else if ( getCurrentSourceType() == SourceType.Radio )
		{
			Item item = getRadioEntry( newTrackNumber );

			if ( item == null )
				return;

			Action setId = radioService.getAction( "SetId" );
			setId.setArgumentValue( "Value", "" + item.getLinnId() );
			setId.setArgumentValue( "Uri", item.getMaxResResourceURL() );

			if ( setId.postControlAction() )
			{
				radioChannel = newTrackNumber;
				currentTrack = newTrackNumber;
				emitTrackNumberChanged( currentTrack );
			}
			else
			{
				handleError( setId );
			}
		}
		else
		{
			Action seekTrackAbsolute = playlistService.getAction( "SeekIndex" );
			seekTrackAbsolute.setArgumentValue( "Value", newTrackNumber );

			if ( seekTrackAbsolute.postControlAction() )
			{
				currentTrack = newTrackNumber;
				emitTrackNumberChanged( currentTrack );
			}
			else
			{
				handleError( seekTrackAbsolute );
			}
		}
	}

	PlayState playState;

	PlayState songcastPlaystate = PlayState.Stopped;

	@Override
	public PlayState getPlayState()
	{
		if ( getCurrentSourceType() == SourceType.Radio )
			return radioPlaystate;
		else if ( getCurrentSourceType() == SourceType.Songcast )
			return songcastPlaystate;
		else
			return playState;
	}

	void setSongcastPlayState( PlayState newPlayState )
	{
		songcastPlaystate = newPlayState;
		emitPlayStateChanged( songcastPlaystate );
	}

	void setRadioPlayState( PlayState newPlayState )
	{
		radioPlaystate = newPlayState;
		emitPlayStateChanged( radioPlaystate );
	}

	@Override
	public void play()
	{
		final Action play;
		if ( getCurrentSourceType() == SourceType.Songcast )
		{
			setSongcastPlayState( PlayState.Playing );
			play = receiverService.getAction( "Play" );
		}
		else if ( getCurrentSourceType() == SourceType.Radio )
		{
			setRadioPlayState( PlayState.Playing );
			play = radioService.getAction( "Play" );
		}
		else
		{
			setPlayState( PlayState.Playing );
			play = playlistService.getAction( "Play" );
		}

		if ( play.postControlAction() )
		{
		}
		else
		{
			handleError( play );
		}
	}

	@Override
	public void setPlayState( PlayState playState )
	{
		this.playState = playState;
		emitPlayStateChanged( playState );
	}

	@Override
	public void pause()
	{
		final Action pause;

		if ( getCurrentSourceType() == SourceType.Songcast )
		{
			stop();
			return;
		}
		else if ( getCurrentSourceType() == SourceType.Radio )
		{
			setRadioPlayState( PlayState.Paused );
			pause = radioService.getAction( "Pause" );
		}
		else
		{
			setPlayState( PlayState.Paused );
			pause = playlistService.getAction( "Pause" );
		}

		if ( pause.postControlAction() )
		{
		}
		else
		{
			handleError( pause );
		}
	}

	@Override
	public void stop()
	{
		final Action stop;

		if ( getCurrentSourceType() == SourceType.Songcast )
		{
			setSongcastPlayState( PlayState.Stopped );
			stop = receiverService.getAction( "Stop" );
		}
		else if ( getCurrentSourceType() == SourceType.Radio )
		{
			setRadioPlayState( PlayState.Stopped );
			stop = radioService.getAction( "Stop" );
		}
		else
		{
			setPlayState( PlayState.Stopped );
			stop = playlistService.getAction( "Stop" );
		}

		if ( stop.postControlAction() )
		{
		}
		else
		{
			handleError( stop );
		}
	}

	ArrayList<String> sourceTypes = new ArrayList<String>();

	public List<String> getSourceNames()
	{
		sourceTypes.clear();
		List<String> rval = new ArrayList<String>();

		if ( productService == null )
		{
			rval.add( "No Sources" );
			return rval;
		}

		Action sourceXml = productService.getAction( "SourceXml" );
		if ( sourceXml != null && sourceXml.postControlAction() )
		{
			String sourceXmlStr = sourceXml.getArgumentValue( "Value" );
			if ( sourceXmlStr != null )
			{
				try
				{
					Node sourceDoc = UPnP.getXMLParser().parse( sourceXmlStr );

					for ( int i = 0; i < sourceDoc.getNNodes(); ++i )
					{
						Node source = sourceDoc.getNode( i );
						if ( source.getName().equals( "Source" ) )
						{
							String sourceName = source.getNodeValue( "Name" );
							if ( sourceName == null )
								continue;

							String sourceVisible = source.getNodeValue( "Visible" );
							if ( sourceVisible == null )
								continue;

							if ( sourceVisible.equals( "true" ) )
								rval.add( sourceName );
							else
								rval.add( "_hidden_" );

							String sourceType = source.getNodeValue( "Type" );
							sourceTypes.add( sourceType );
						}
					}
				}
				catch ( ParserException e )
				{
					e.printStackTrace();
				}
			}
		}
		else
		{
			handleError( sourceXml );
		}

		return rval;
	}

	public int getSourceIndex()
	{
		int rval = 0;

		if ( productService == null )
			return rval;

		Action sourceIndex = productService.getAction( "SourceIndex" );

		if ( sourceIndex.postControlAction() )
		{
			rval = sourceIndex.getArgumentIntegerValue( "Value" );
		}
		else
		{
			handleError( sourceIndex );
		}

		return rval;
	}

	public void setSourceIndex( int sourceIndex )
	{
		if ( productService == null )
			return;

		Action setSourceIndex = productService.getAction( "SetSourceIndex" );
		setSourceIndex.setArgumentValue( "Value", sourceIndex );

		if ( setSourceIndex.postControlAction() )
		{
		}
		else
		{
			handleError( setSourceIndex );
		}
	}

	void updateSourceType( int currentSourceIndex )
	{
		String typeName = sourceTypes.get( currentSourceIndex );

		SourceType lastSource = currentSource;

		currentSource = SourceType.Other;

		if ( typeName.equals( "Playlist" ) )
			currentSource = SourceType.Playlist;
		else if ( typeName.equals( "Radio" ) )
			currentSource = SourceType.Radio;
		else if ( typeName.equals( "Receiver" ) )
			currentSource = SourceType.Songcast;

		if ( lastSource != currentSource )
		{
			if ( currentSource == SourceType.Playlist )
			{
				currentTrack = getCurrentTrack();
				emitPlaylistChanged();
				emitTrackNumberChanged( currentTrack );
			}
			else if ( currentSource == SourceType.Playlist )
			{
				currentTrack = getRadioChannel();
				emitPlaylistChanged();
				emitTrackNumberChanged( currentTrack );
			}
			else if ( currentSource == SourceType.Songcast )
			{
				currentTrack = 0;
				emitPlaylistChanged();
				emitTrackNumberChanged( currentTrack );
			}
		}
	}

	SourceType getCurrentSourceType()
	{
		if ( currentSource != SourceType.Unknown )
			return currentSource;

		getSourceNames();

		int currentSourceIndex = getSourceIndex();

		updateSourceType( currentSourceIndex );

		return currentSource;
	}

	public void setStandby( boolean standby )
	{
		if ( productService == null )
			return;

		Action setStandby = productService.getAction( "SetStandby" );
		setStandby.setArgumentValue( "Value", standby ? "1" : "0" );

		if ( setStandby.postControlAction() )
		{
		}
		else
		{
			handleError( setStandby );
		}
	}

	public boolean getStandby()
	{
		boolean rval = false;

		if ( productService == null )
			return rval;

		Action standby = productService.getAction( "Standby" );

		if ( standby.postControlAction() )
		{
			String standbyStr = standby.getArgumentValue( "Value" );
			rval = "1".equals( standbyStr );
		}
		else
		{
			handleError( standby );
		}

		return rval;
	}

	int getCurrentTime()
	{
		int rval = -1;

		if ( timeService == null )
		{
			handleError( "Seconds Action Missing" );
			return rval;
		}

		Action seconds = timeService.getAction( "Time" );
		if ( seconds.postControlAction() )
		{
			try
			{
				String secondsString = seconds.getArgumentValue( "Seconds" );
				if ( secondsString != null )
					rval = Integer.parseInt( secondsString );
			}
			catch ( Exception e )
			{

			}

			try
			{
				String durationString = seconds.getArgumentValue( "Duration" );
				if ( durationString != null )
					totalSeconds = Integer.parseInt( durationString );
			}
			catch ( Exception e )
			{

			}
		}
		else
		{
			handleError( seconds );
		}

		return rval;
	}

	@Override
	protected void updateCurrentTimeStamp()
	{
		int time = getCurrentTime();
		setCurrentSeconds( time );
	}

	int currentSeconds = -1;

	private void setCurrentSeconds( int time )
	{
		currentSeconds = time;
		emitTimeStampChanged( currentSeconds );
	}

	@Override
	public void setActive( boolean active, boolean hardStop )
	{
		if ( active == getActive() )
			return;

		super.setActive( active, hardStop );

		if ( !active )
		{
			if ( renderingControl != null )
				controlPoint.unsubscribe( this, renderingControl );

			if ( avtransportService != null )
				controlPoint.unsubscribe( this, avtransportService );

			controlPoint.unsubscribe( this, playlistService );

			if ( productService != null )
				controlPoint.unsubscribe( this, productService );

			if ( radioService != null )
				controlPoint.unsubscribe( this, radioService );

			if ( infoService != null )
				controlPoint.unsubscribe( this, infoService );

			if ( receiverService != null )
				controlPoint.unsubscribe( this, receiverService );
		}
		else
		{
			// Reset the playmode to disabled. If device supports it, it will get enabled upon subscription
			setPlayModeInternal( getCurrentPlayMode() );

			// Reset the playlist
			currentTrack = -1;

			// XXX What would this do since there isn't an internal playlist?!
			// while ( [super getPlaylistEntryCount] )
			// [super removePlaylistEntryNoListener:0];

			if ( productService != null )
			{
				currentSource = SourceType.Unknown;
				getCurrentSourceType();

				if ( radioService != null )
				{
					updateRadioIdArray();
					radioPlaystate = getRadioPlayState();
					radioChannel = getRadioChannel();
				}

				if ( receiverService != null )
				{
					songcastPlaystate = getSongcastPlayState();
				}
			}

			// Fetch the playlist id array
			updateIdArray();

			// Tell listeners that there is a new playlist
			emitPlaylistChanged();

			// Get the current play state
			setPlayState( getCurrentPlayState() );

			// Set the current track
			currentTrack = getCurrentTrack();

			// Set the current volume
			volume = getCurrentVolume();
			emitVolumeChanged( volume );

			// Subscribe to future events
			if ( renderingControl != null )
				controlPoint.subscribe( this, renderingControl );

			if ( avtransportService != null )
				controlPoint.subscribe( this, avtransportService );

			controlPoint.subscribe( this, playlistService );

			if ( productService != null )
				controlPoint.subscribe( this, productService );

			if ( radioService != null )
				controlPoint.subscribe( this, radioService );

			if ( infoService != null )
				controlPoint.subscribe( this, infoService );

			if ( receiverService != null )
				controlPoint.subscribe( this, receiverService );
		}
	}

	private PlayMode getCurrentPlayMode()
	{
		if ( avtransportService == null )
			return PlayMode.Unsupported;

		PlayMode rval = PlayMode.Unsupported;

		Action getTransportSettings = avtransportService.getAction( "GetTransportSettings" );

		if ( getTransportSettings == null )
			return PlayMode.Unsupported;

		getTransportSettings.setArgumentValue( "InstanceID", 0 );

		if ( getTransportSettings.postControlAction() )
		{
			String newPlayMode = getTransportSettings.getArgumentValue( "PlayMode" );
			if ( newPlayMode != null )
				rval = PlayMode.fromUpnpValue( newPlayMode );
		}
		else
		{
			handleError( getTransportSettings );
		}

		return rval;
	}

	void processRepeat( String repeatString, String shuffleString )
	{
		if ( repeatString == null && shuffleString == null )
			return;

		if ( shuffleString != null )
			shuffle = shuffleString.equals( "true" );

		if ( repeatString != null )
			repeat = repeatString.equals( "true" );

		if ( shuffle )
		{
			setPlayModeInternal( PlayMode.Shuffle );
		}
		else if ( repeat )
		{
			setPlayModeInternal( PlayMode.RepeatAll );
		}
		else
		{
			setPlayModeInternal( PlayMode.Normal );
		}
	}

	PlayMode playMode = PlayMode.Normal;

	private void setPlayModeInternal( PlayMode playMode )
	{
		PlayMode oldMode = this.playMode;

		this.playMode = playMode;

		if ( oldMode != PlayMode.Unsupported && playMode != PlayMode.Unsupported )
			emitPlayModeChanged( playMode );
	}

	@Override
	public void setPlayMode( PlayMode mode )
	{
		if ( avtransportService != null )
		{
			while ( mode != PlayMode.Normal && mode != PlayMode.Shuffle && mode != PlayMode.RepeatAll && mode != PlayMode.RepeatOne )
				mode = mode.next();

			setPlayModeInternal( mode );

			Action setPlayMode = avtransportService.getAction( "SetPlayMode" );
			setPlayMode.setArgumentValue( "InstanceID", 0 );
			setPlayMode.setArgumentValue( "NewPlayMode", mode.upnpValue() );

			if ( setPlayMode.postControlAction() )
			{
			}
			else
			{
				handleError( setPlayMode );
			}
		}
		else
		{
			while ( mode != PlayMode.Normal && mode != PlayMode.Shuffle && mode != PlayMode.RepeatAll )
				mode = mode.next();

			setPlayModeInternal( mode );

			Action setRepeat = playlistService.getAction( "SetRepeat" );
			setRepeat.setArgumentValue( "Value", mode == PlayMode.RepeatAll ? "true" : "false" );

			if ( setRepeat.postControlAction() )
			{
			}
			else
			{
				handleError( setRepeat );
			}

			Action setShuffle = playlistService.getAction( "SetShuffle" );
			setShuffle.setArgumentValue( "Value", mode == PlayMode.Shuffle ? "true" : "false" );

			if ( setShuffle.postControlAction() )
			{
			}
			else
			{
				handleError( setShuffle );
			}
		}
	}

	private synchronized void radioEventNotifyReceived( String uuid, long seq, String varName, String value )
	{
		if ( varName.equals( "Id" ) )
		{
			radioChannel = radioIndexFromTrackId( Long.parseLong( value ) );

			if ( getCurrentSourceType() == SourceType.Radio )
			{
				currentTrack = radioChannel;
				emitTrackNumberChanged( currentTrack );
			}
		}
		else if ( varName.equals( "IdArray" ) )
		{
			radioIdArrayString = value.toCharArray();

			if ( getCurrentSourceType() == SourceType.Radio )
			{
				emitPlaylistChanged();
			}
		}
		else if ( varName.equals( "TransportState" ) )
		{
			PlayState newPlayState = PlayState.NoMedia;

			if ( value.equals( "Stopped" ) )
				newPlayState = PlayState.Stopped;
			else if ( value.equals( "Playing" ) || value.equals( "Buffering" ) )
				newPlayState = PlayState.Playing;
			else if ( value.equals( "Paused" ) )
				newPlayState = PlayState.Paused;

			setRadioPlayState( newPlayState );
		}
	}

	private synchronized void receiverEventNotifyReceived( String uuid, long seq, String varName, String value )
	{
		if ( varName.equals( "TransportState" ) )
		{
			PlayState newPlayState = PlayState.NoMedia;

			if ( value.equals( "Stopped" ) )
				newPlayState = PlayState.Stopped;
			else if ( value.equals( "Playing" ) || value.equals( "Buffering" ) )
				newPlayState = PlayState.Playing;
			else if ( value.equals( "Paused" ) )
				newPlayState = PlayState.Paused;

			setSongcastPlayState( newPlayState );
		}
	}

	@Override
	public synchronized void eventNotifyReceived( String uuid, long seq, String varName, String value )
	{
		if ( radioService != null && uuid.equals( radioService.getSID() ) )
		{
			radioEventNotifyReceived( uuid, seq, varName, value );
			return;
		}

		if ( receiverService != null && uuid.equals( receiverService.getSID() ) )
		{
			receiverEventNotifyReceived( uuid, seq, varName, value );
			return;
		}

		if ( varName.equals( "LastChange" ) )
		{
			try
			{
				boolean needEvent = false;
				Parser parser = UPnP.getXMLParser();
				Node innerDoc = parser.parse( value ).getNode( 0 );

				if ( !isVolumeChanging() )
				{
					String volumeString = getNodeAttribute( innerDoc, "Volume", "val" );
					if ( volumeString != null )
					{
						float v = Integer.parseInt( volumeString ) / 100.0f;
						if ( v != getVolume() )
						{
							volume = v;
							needEvent = true;
						}
					}

					String muteString = getNodeAttribute( innerDoc, "Mute", "val" );
					if ( muteString != null )
					{
						boolean newMute = muteString.equals( "1" ) || muteString.equals( "true" );
						if ( newMute != mute )
						{
							mute = newMute;
							needEvent = true;
						}
					}

					if ( needEvent )
						emitVolumeChanged( volume );
				}

				String newPlayMode = getNodeAttribute( innerDoc, "CurrentPlayMode", "val" );
				if ( newPlayMode != null && newPlayMode.length() > 0 )
				{
					setPlayModeInternal( PlayMode.fromUpnpValue( newPlayMode ) );
				}
			}
			catch ( Exception e )
			{
				e.printStackTrace();
			}
		}
		else if ( varName.equals( "IdArray" ) )
		{
			if ( !inserting )
			{
				idArray = null;
				idArrayString = value.toCharArray();
				emitPlaylistChanged();

				currentTrack = getCurrentTrack();
				emitTrackNumberChanged( currentTrack );
			}
		}
		else if ( avtransportService == null && varName.equals( "Repeat" ) )
		{
			processRepeat( value, null );

		}
		else if ( avtransportService == null && varName.equals( "Shuffle" ) )
		{
			processRepeat( null, value );
		}
		else if ( varName.equals( "Duration" ) )
		{
			totalSeconds = Integer.parseInt( value );
		}
		else if ( varName.equals( "Seconds" ) )
		{
			setCurrentSeconds( Integer.parseInt( value ) );
		}
		else if ( varName.equals( "TransportState" ) )
		{
			PlayState newPlayState = PlayState.NoMedia;

			if ( value.equals( "Stopped" ) )
				newPlayState = PlayState.Stopped;
			else if ( value.equals( "Playing" ) || value.equals( "Buffering" ) )
				newPlayState = PlayState.Playing;
			else if ( value.equals( "Paused" ) )
				newPlayState = PlayState.Paused;

			if ( playState != newPlayState )
				setPlayState( newPlayState );
		}
		else if ( varName.equals( "Id" ) )
		{
			long trackId = Long.parseLong( value );
			currentTrack = indexFromTrackId( trackId );
			emitTrackNumberChanged( currentTrack );
		}
		else if ( varName.equals( "SourceIndex" ) )
		{
			updateSourceType( Integer.parseInt( value ) );
		}
		else if ( varName.equals( "Metatext" ) )
		{
			if ( getCurrentSourceType() == SourceType.Radio )
			{
				try
				{
					Parser parser = UPnP.getXMLParser();
					Node innerDoc = parser.parse( value ).getNode( 0 );

					String title = innerDoc.getNodeValue( "dc:title" );
					if ( title == null || title.equals( "" ) )
						title = "Radio";

					Item entry = getPlaylistEntry( radioChannel );
					entry.setAlbum( title );

					emitTrackNumberChanged( radioChannel );
				}
				catch ( Exception e )
				{
					e.printStackTrace();
				}
			}
		}
	}

	public static LinnDavaarRenderer createDevice( Device dev )
	{
		LinnDavaarRenderer renderer = new LinnDavaarRenderer();
		renderer.updateServices( dev );

		if ( renderer.playlistService != null && renderer.renderingControl != null && renderer.timeService != null )
		{
			return renderer;
		}

		return null;
	}

	public LinnDavaarRenderer()
	{
		super();
		trackmap = new HashMap<Long, Item>();
		radioTrackmap = new HashMap<Long, Item>();
	}

	@Override
	public void updateServices( Device dev )
	{
		setLocation( dev.getLocation() );
		setUDN( dev.getUDN() );
		setOriginalName( dev.getFriendlyName() );

		playlistService = dev.getService( "urn:av-openhome-org:service:Playlist:1" );
		renderingControl = dev.getService( "urn:schemas-upnp-org:service:RenderingControl:1" );
		avtransportService = dev.getService( "urn:schemas-upnp-org:service:AVTransport:1" );
		timeService = dev.getService( "urn:av-openhome-org:service:Time:1" );
		productService = dev.getService( "urn:av-openhome-org:service:Product:1" );
		radioService = dev.getService( "urn:av-openhome-org:service:Radio:1" );
		receiverService = dev.getService( "urn:av-openhome-org:service:Receiver:1" );
		senderService = dev.getService( "urn:av-openhome-org:service:Sender:1" );
		infoService = dev.getService( "urn:av-openhome-org:service:Info:1" );

		String baseURLString = "";
		try
		{
			URL locationURL = new URL( dev.getLocation() );
			baseURLString = locationURL.getProtocol() + "://" + locationURL.getHost() + ":" + locationURL.getPort();
		}
		catch ( MalformedURLException e )
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// PlugPlayerControlPoint controlPoint = PlugPlayerControlPoint.getInstance();

		if ( playlistService != null && renderingControl == null )
		{
			Device d = PlugPlayerControlPoint.deviceFromLocation( baseURLString + "/MediaRenderer", getSearchTimeout() );

			if ( d != null )
			{
				setOtherUDN( d.getUDN() );
				renderingControl = d.getService( "urn:schemas-upnp-org:service:RenderingControl:1" );
			}
		}
		else if ( playlistService == null && renderingControl != null )
		{
			Device d = PlugPlayerControlPoint.deviceFromLocation( baseURLString + "/Ds", getSearchTimeout() );

			if ( d != null )
			{
				setOtherUDN( dev.getUDN() );
				setLocation( d.getLocation() );
				setUDN( d.getUDN() );
				setOriginalName( d.getFriendlyName() );
				playlistService = d.getService( "urn:av-openhome-org:service:Playlist:1" );
				timeService = d.getService( "urn:av-openhome-org:service:Time:1" );
				productService = d.getService( "urn:av-openhome-org:service:Product:1" );
				radioService = d.getService( "urn:av-openhome-org:service:Radio:1" );
				receiverService = d.getService( "urn:av-openhome-org:service:Receiver:1" );
				senderService = d.getService( "urn:av-openhome-org:service:Sender:1" );
				infoService = d.getService( "urn:av-openhome-org:service:Info:1" );
			}
		}

		if ( playlistService != null && renderingControl != null && timeService != null )
		{
			if ( !dev.getIconList().isEmpty() )
				setIconURL( dev.getAbsoluteURL( dev.getIcon( 0 ).getURL() ) );

			PlugPlayerControlPoint.getInstance().controlPoint.addDevice( dev.getRootNode() );
		}
	}

	String otherUDN = null;

	public String getOtherUDN()
	{
		return otherUDN;
	}

	private void setOtherUDN( String udn )
	{
		this.otherUDN = udn;
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

	public static LinnDavaarRenderer createFromState( StateMap state )
	{
		LinnDavaarRenderer rval = new LinnDavaarRenderer();
		rval.fromState( state );
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

	// XXX SHould this jsut execute next on the renderer?!
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

	// XXX Should this jsut call prev on the renderer!?
	@Override
	public void prev()
	{
		if ( !hasPrev() )
			return;

		int index = prevTrackNumber();
		setTrackNumber( index );
	}

	@Override
	public int getCurrentSeconds()
	{
		return currentSeconds;
	}

	@Override
	public int getTotalSeconds()
	{
		return totalSeconds;
	}

	@Override
	public PlayMode getPlayMode()
	{
		return playMode;
	}

	@Override
	public float getVolume()
	{
		return volume;
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

		Action setMute = renderingControl.getAction( "SetMute" );
		setMute.setArgumentValue( "InstanceID", 0 );
		setMute.setArgumentValue( "Channel", "Master" );
		setMute.setArgumentValue( "DesiredMute", newMute ? "true" : "false" );

		if ( setMute.postControlAction() == true )
		{
			mute = newMute;

			// if ( mute )
			// emitVolumeChanged( 0 );
			// else
			emitVolumeChanged( volume );
		}
		else
		{
			handleError( setMute );
		}
	}

	public boolean hasProductService()
	{
		return productService != null;
	}

	public boolean hasReceiverService()
	{
		return receiverService != null;
	}

	public boolean hasSenderService()
	{
		return senderService != null;
	}

	int insertindex;
	int playindex;
	volatile List<Item> playentries = null;
	volatile boolean keepInserting = true;

	Runnable insertInBackground = new Runnable()
	{
		public void run()
		{
			try
			{
				int i = 0;

				if ( playindex != -1 )
				{
					for ( ; i < playindex && keepInserting; ++i )
						insertPlaylistEntry( playentries.get( i ), i + insertindex );
				}

				for ( ; i < playentries.size() && keepInserting; ++i )
					insertPlaylistEntry( playentries.get( i ), i + insertindex + (playindex != -1 ? 1 : 0) );
			}
			catch ( Exception e )
			{
				e.printStackTrace();
			}

			inserting = false;

			playentries = null;
		}
	};

	public void insertPlaylistEntries( List<Item> entries, int insertIndex, int playIndex, boolean clear ) throws InterruptedException
	{
		// NSLog(@"Waiting to inserting %d entries", entries.count );

		// If we're insterting something; make it stop and we'll start over
		// XXX TODO: We could check to see if playentries is (basically) the same list and so something smart
		keepInserting = false;
		while ( playentries != null )
			Thread.sleep( 100 );
		keepInserting = true;

		if ( clear || playIndex != -1 )
			stop();

		if ( clear )
			removePlaylistEntries( 0, getPlaylistEntryCount() );

		if ( entries == null || entries.isEmpty() )
			return;

		// NSLog(@"Inserting %d entries", entries.count );

		playentries = entries;

		inserting = true;

		this.insertindex = insertIndex;
		this.playindex = playIndex;

		if ( playIndex != -1 )
		{
			insertPlaylistEntry( playentries.get( playIndex ), 0 );
			setTrackNumber( 0 );
			play();

			playentries.remove( playIndex );
		}

		new Thread( insertInBackground ).start();
	}

	String getSenderMetadata()
	{
		if ( senderService == null )
			return null;

		String rval = null;

		Action getMetadata = senderService.getAction( "Metadata" );
		if ( getMetadata.postControlAction() == true )
		{
			rval = getMetadata.getArgumentValue( "Value" );
		}
		else
		{
			handleError( getMetadata );
		}

		return rval;
	}

	String getSongcastMetadata()
	{
		String rval = null;

		Action getSender = receiverService.getAction( "Sender" );
		if ( getSender.postControlAction() == true )
		{
			rval = getSender.getArgumentValue( "Metadata" );
		}
		else
		{
			handleError( getSender );
		}

		return rval;
	}

	public Renderer getSongcastSender()
	{
		Renderer rval = null;
		String metadata = getSongcastMetadata();

		try
		{
			Node n = UPnP.getXMLParser().parse( metadata ).getNode( 0 );
			String sname = n.getNodeValue( "dc:title" );

			for ( Renderer r : PlugPlayerControlPoint.getInstance().getRendererList() )
			{
				if ( r.getName().equals( sname ) )
				{
					rval = r;
					break;
				}
			}
		}
		catch ( Exception e )
		{
		}

		return rval;
	}

	public void setSongcastSender( Renderer sender )
	{
		if ( !(sender instanceof LinnDavaarRenderer) )
			return;

		LinnDavaarRenderer s = (LinnDavaarRenderer)sender;
		String metadata = s.getSenderMetadata();

		try
		{
			Node n = UPnP.getXMLParser().parse( metadata ).getNode( 0 );
			String res = n.getNodeValue( "res" );

			Action setSender = receiverService.getAction( "SetSender" );
			setSender.setArgumentValue( "Uri", res );
			setSender.setArgumentValue( "Metadata", metadata );

			if ( setSender.postControlAction() == true )
			{
				songcastEntry = songCastEntryFromMetadata( metadata );
				emitPlaylistChanged();
				emitTrackNumberChanged( 0 );
			}
			else
			{
				handleError( setSender );
			}
		}
		catch ( Exception e )
		{
		}
	}
}
