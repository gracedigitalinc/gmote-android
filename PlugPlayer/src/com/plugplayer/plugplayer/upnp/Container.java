package com.plugplayer.plugplayer.upnp;

import java.util.ArrayList;
import java.util.List;

import org.cybergarage.xml.Node;

import com.plugplayer.plugplayer.upnp.Item.ContentType;
import com.plugplayer.plugplayer.utils.DirEntryManager;

public class Container extends DirEntry
{
	public List<DirEntry> getChildren()
	{
		return allChildren;
	}

	public int getChildCount()
	{
		return allChildren.size();
	}

	public DirEntry getChild( int index )
	{
		return allChildren.get( index );
	}

	private int totalCount = -1;

	public int getTotalCount()
	{
		return totalCount;
	}

	public void setTotalCount( int totalCount )
	{
		this.totalCount = totalCount;
	}

	public Container addContainer( Node child )
	{
		String classStr = child.getNodeValue( "upnp:class" );
		String titleStr = child.getNodeValue( "dc:title" );
		String objectIdStr = child.getAttributeValue( "id" );
		String childCount = child.getAttributeValue( "childCount" );
		String artURL = child.getNodeValue( "upnp:albumArtURI" );

		Container entry = new Container();
		entry.setArtURL( artURL );
		entry.setSelectionState( getSelectionState() == SelectionState.Selected ? SelectionState.Selected : SelectionState.Unselected );
		entry.setTitle( titleStr != null && titleStr.length() > 0 ? titleStr : "<No Name>" );
		entry.setObjectID( objectIdStr != null ? objectIdStr : "" );
		entry.setUpnpClass( classStr != null ? classStr : "" );

		int count = childCount != null && !childCount.equals( "" ) ? Integer.parseInt( childCount ) : -1;

		// XXX Don't trust 0; PS3MediaServer sends 0 when it doens't know the answer.
		if ( count == 0 )
			count = -1;

		entry.setTotalCount( count );

		addEntry( entry, entry.getTitle() );

		return entry;
	}

	private final List<DirEntry> allChildren = new ArrayList<DirEntry>();

	@Override
	public void setArtURL( String artURL )
	{
		super.setArtURL( artURL );

		String art = getArtURL();
		if ( art != null && art != NOART && parent != null && (parent.getArtURL() == NOART || parent.getArtURL() == null) )
			parent.setArtURL( art );
	}

	private void addEntry( DirEntry entry, String title )
	{
		entry.parent = this;

		String art = entry.getArtURL();
		if ( art != null && (getArtURL() == NOART || getArtURL() == null) )
			setArtURL( art );

		allChildren.add( entry );

		DirEntryManager.addEntry( entry );
	}

	public void removeEntry( int x )
	{
		DirEntry entry = getChild( x );
		entry.parent = null;
		allChildren.remove( x );
	}

	public Item addItem( Node child, String didl )
	{
		String classStr = child.getNodeValue( "upnp:class" );
		String titleStr = child.getNodeValue( "dc:title" );
		String artURL = child.getNodeValue( "upnp:albumArtURI" );

		Item entry = new Item( child );
		entry.setSelectionState( getSelectionState() == SelectionState.Selected ? SelectionState.Selected : SelectionState.Unselected );

		String metadata = didl + child.toString() + "</DIDL-Lite>";
		entry.setMetadata( metadata );
		entry.setTitle( titleStr != null && titleStr.length() > 0 ? titleStr : "<No Name>" );
		entry.setUpnpClass( classStr != null ? classStr : "" );
		entry.setArtURL( artURL );

		if ( entry.getArtURL() == null && entry.getContentType() == ContentType.Image )
		{
			entry.setArtURL( entry.getResourceURL() );
		}

		addEntry( entry, entry.getTitle() );

		return entry;
	}

	// Recursively set all children to unselected
	public void pushSelectionStateDown( SelectionState newState )
	{
		setSelectionState( newState );

		for ( DirEntry child : allChildren )
		{
			if ( child instanceof Container )
				((Container)child).pushSelectionStateDown( newState );
			else
				child.setSelectionState( newState );
		}
	}
}
