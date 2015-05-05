package com.plugplayer.plugplayer.upnp;

import java.util.ArrayList;

import org.cybergarage.upnp.Action;
import org.cybergarage.upnp.Device;
import org.cybergarage.upnp.Service;
import org.cybergarage.xml.Node;

import android.app.Activity;
import android.content.SharedPreferences;
import android.util.Log;

import com.plugplayer.plugplayer.activities.MainActivity;
import com.plugplayer.plugplayer.activities.SettingsEditor;
import com.plugplayer.plugplayer.utils.ChunkedXmlPullParser;
import com.plugplayer.plugplayer.utils.StateMap;

public class UPNPServer extends Server
{
	private static final int REQUEST_COUNT = 50;
	// private static final int SEARCHES = 3;
	private static final String searches[] = { "(upnp:class = \"object.container.album.musicAlbum\" and dc:title contains \"%s\")",
			"(upnp:class = \"object.container.person.musicArtist\" and dc:title contains \"%s\")", "(dc:title contains \"%s\")" };

	public static final String STATENAME = "UPNPServer";

	private transient int canSearch = -1;
	protected transient Service directoryService;

	public static UPNPServer createDevice( Device dev )
	{
		Service directoryService = dev.getService( "urn:schemas-upnp-org:service:ContentDirectory:1" );
		if ( directoryService == null )
			directoryService = dev.getService( "urn:schemas-upnp-org:service:ContentDirectory:2" );

		if ( directoryService != null )
		{
			UPNPServer server = new UPNPServer();
			server.updateServices( dev );
			return server;
		}

		return null;
	}

	@Override
	public void updateServices( Device dev )
	{
		setUDN( dev.getUDN() );
		setLocation( dev.getLocation() );
		setOriginalName( dev.getFriendlyName() );

		directoryService = dev.getService( "urn:schemas-upnp-org:service:ContentDirectory:1" );
		if ( directoryService == null )
			directoryService = dev.getService( "urn:schemas-upnp-org:service:ContentDirectory:2" );

		if ( !dev.getIconList().isEmpty() )
			setIconURL( dev.getAbsoluteURL( dev.getIcon( 0 ).getURL() ) );

		PlugPlayerControlPoint.getInstance().controlPoint.addDevice( dev.getRootNode() );
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

	public static UPNPServer createFromState( StateMap state )
	{
		UPNPServer rval = new UPNPServer();
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
			// controlPoint.subscribe( this, directoryService );
		}
		else
		{
			// controlPoint.unsubscribe( this, directoryService );
		}

		super.setActive( active, hardStop );
	}

	ChunkedXmlPullParser.Filter filter = new ChunkedXmlPullParser.Filter()
	{
		public boolean ignoreNode( String nodePrefix, String nodeName )
		{
			return "pv:".equals( nodePrefix ) || "desc".equals( nodeName ) || "searchClass".equals( nodeName ) || "writeStatus".equals( nodeName )
					|| "author".equals( nodeName ) || "actor".equals( nodeName ) || "genre".equals( nodeName ) || "creator".equals( nodeName )
					|| "originalTrackNumber".equals( nodeName );
		}
	};

	private void browseDir( Container parent, String search, int searchNum, BrowseResultsListener listener, int max )
	{
		if ( directoryService == null )
		{
			if ( listener != null )
				listener.onDone();

			Log.e( MainActivity.appName, "No Directory Service!" );
			return;
		}

		// XXX We should be able to synchronize on parent, but at least on MediaTomb we code the wrong data back
		synchronized ( this )
		{
			if ( listener != null && search == null )
			{
				// We copy because parent.getChildren() is used by the background thread
				ArrayList<DirEntry> copy = new ArrayList<DirEntry>();
				copy.addAll( parent.getChildren() );
				listener.onInitialChildren( copy, parent.getTotalCount() );
			}

			int entryCount = parent.getChildCount();
			int requestCount = entryCount != 0 ? REQUEST_COUNT : 10; // First
																		// request
																		// we use 10
																		// to make
																		// sure if
																		// it's a
																		// big
																		// directory
																		// that we
																		// show a
																		// full page
																		// right
																		// away.
			int entriesLeft;

			if ( max > -1 )
				requestCount = max;

			if ( parent.getTotalCount() == -1 )
				entriesLeft = requestCount;
			else
				entriesLeft = parent.getTotalCount() - entryCount;

			final SharedPreferences preferences = MainActivity.me.getSharedPreferences( SettingsEditor.ADVANCED_SETTINGS, Activity.MODE_PRIVATE );

			String nodeFilter = "*";

			if ( preferences.getBoolean( SettingsEditor.ART_BROWSE, true ) )
				nodeFilter = "id,childCount,dc:title,upnp:class,res,res@resolution,res@protocolInfo,upnp:artist,upnp:album,res@duration,upnp:albumArtURI";

			while ( entriesLeft > 0 && (max < 0 || parent.getChildCount() != max) )
			{
				ArrayList<DirEntry> subList = new ArrayList<DirEntry>();

				Action action;

				if ( search != null )
				{
					action = directoryService.getAction( "Search" );

					if ( action != null )
					{
						action.setArgumentValue( "ContainerID", parent.getObjectID() );
						action.setArgumentValue( "SearchCriteria", String.format( searches[searchNum], search ) );
						action.setArgumentValue( "Filter", nodeFilter );
						action.setArgumentValue( "StartingIndex", "0" );
						action.setArgumentValue( "RequestedCount", requestCount );
						action.setArgumentValue( "SortCriteria", "" );
					}
					else
					{
						handleError( "Server '" + getName() + "' has no Search action" );
						return;
					}
				}
				else
				{
					action = directoryService.getAction( "Browse" );

					if ( action != null )
					{
						action.setArgumentValue( "ObjectID", parent.getObjectID() );
						action.setArgumentValue( "BrowseFlag", "BrowseDirectChildren" );
						action.setArgumentValue( "Filter", nodeFilter );
						action.setArgumentValue( "StartingIndex", entryCount );
						action.setArgumentValue( "RequestedCount", requestCount );
						action.setArgumentValue( "SortCriteria", "" );
					}
					else
					{
						handleError( "Server '" + getName() + "' has no Browse action" );
						return;
					}
				}

				if ( action.postControlAction() )
				{
					// Always (re)set total count incase it changes as well load like on PS3MediaServer
					// if ( parent.getTotalCount() < 0 )
					parent.setTotalCount( action.getArgumentIntegerValue( "TotalMatches" ) );

					int numReturned = action.getArgumentIntegerValue( "NumberReturned" );
					entryCount += numReturned;

					entriesLeft = parent.getTotalCount() - entryCount;

					if ( requestCount == 10 )
						requestCount = REQUEST_COUNT;

					if ( numReturned > 0 )
					{
						String result = action.getArgumentValue( "Result" );

						// From this point forward, action it no longer needed.
						// We clear it's reference so its memory can be reclaim if needed
						action = null;

						int didllen = 0;
						int didlstart = 0;
						if ( result.charAt( 1 ) == '?' )
						{
							didlstart = result.indexOf( ">" ) + 1;
							didllen = result.indexOf( ">", didlstart ) - didlstart + 1;
						}
						else
							didllen = result.indexOf( ">" ) + 1;

						String didl = result.substring( didlstart, didlstart + didllen );

						try
						{
							// Because these result sets can be large, we parse them in chunks
							for ( Node child : ChunkedXmlPullParser.parse( result, filter ) )
							{
								// Parser parser = UPnP.getXMLParser();
								// Node innerResultDoc = parser.parse( result );
								//
								// for ( int i = 0; i < innerResultDoc.getNNodes(); ++i )
								// {
								// Node child = innerResultDoc.getNode( i );

								// System.out.println( child );

								if ( child.getName().equals( "container" ) )
								{
									subList.add( parent.addContainer( child ) );
								}
								else if ( child.getName().equals( "item" ) )
								{
									if ( getBaseOverride() != null && getBaseOverride().length() > 0 )
									{
										for ( int n = 0; n < child.getNNodes(); ++n )
										{
											Node node = child.getNode( n );
											if ( node.getName().equals( "res" ) || node.getName().equals( "upnp:albumArtURI" ) )
											{
												node.setValue( overrideBase( node.getValue() ) );
											}
										}
									}

									subList.add( parent.addItem( child, didl ) );
								}
							}
						}
						catch ( Exception e )
						{
							e.printStackTrace();
						}
					}

					if ( listener != null && !listener.loadMore() )
						return;
				}
				else
				{
					handleError( action );
					break;
				}

				if ( listener != null )
					listener.onMoreChildren( subList, parent.getTotalCount() );
			}

			if ( listener != null )
				listener.onDone();
		}
	}

	@Override
	public void browseDir( Container parent )
	{
		browseDir( parent, null, 0, null, -1 );
	}

	@Override
	public void browseDir( Container parent, int max )
	{
		browseDir( parent, null, 0, null, max );
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
				browseDir( parent, null, 0, listener, -1 );
			}
		} ).start();
	}

	@Override
	public void searchDir( Container parent, String search )
	{
		browseDir( parent, search, 0, null, -1 );
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
						parent.setTotalCount( -1 );
						listener.onMoreChildren( new ArrayList<DirEntry>(), -1 );
						browseDir( parent, query, i, listener, -1 );
					}

					listener.onDone();
				}
			}
		} ).start();
	}

	@Override
	public boolean canSearch()
	{
		if ( canSearch < 0 )
		{
			canSearch = 0;

			Action getSearchCapabilities = directoryService.getAction( "GetSearchCapabilities" );

			if ( getSearchCapabilities != null && getSearchCapabilities.postControlAction() )
			{
				String search = getSearchCapabilities.getArgumentValue( "SearchCaps" );
				if ( search != null && search.length() > 0 )
					canSearch = 1;
			}
			else
			{
				handleError( getSearchCapabilities );
			}
		}

		return canSearch == 1;
	}
}
