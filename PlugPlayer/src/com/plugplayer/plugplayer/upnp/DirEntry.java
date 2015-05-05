package com.plugplayer.plugplayer.upnp;

public class DirEntry implements Comparable<DirEntry>
{
	public final static String NOART = "NOART";

	protected Container parent = null;

	public static enum SelectionState
	{
		Unselected, Selected, PartiallySelected
	}

	SelectionState selectionState = SelectionState.Unselected;

	public SelectionState getSelectionState()
	{
		return selectionState;
	}

	public void setSelectionState( SelectionState selectionState )
	{
		this.selectionState = selectionState;
	}

	public void pushSelectionStateUp( SelectionState newState )
	{
		setSelectionState( newState );

		if ( parent != null )
		{
			for ( DirEntry child : parent.getChildren() )
			{
				if ( child.getSelectionState() != newState )
				{
					parent.pushSelectionStateUp( SelectionState.PartiallySelected );
					return;
				}
			}

			parent.pushSelectionStateUp( newState );
		}
	}

	String upnpClass;

	public void setUpnpClass( String upnpClass )
	{
		this.upnpClass = upnpClass;
	}

	public String getUpnpClass()
	{
		return upnpClass;
	}

	private String objectID;

	public void setObjectID( String objectID )
	{
		this.objectID = objectID;
	}

	public String getObjectID()
	{
		return objectID;
	}

	String title = null;

	public void setTitle( String title )
	{
		this.title = title;
	}

	public String getTitle()
	{
		return title;
	}

	@Override
	public String toString()
	{
		String title = getTitle();
		if ( title == null )
			return "?";
		else
			return getTitle();
	}

	private String artURL = null;

	public String getArtURL()
	{
		return artURL;
	}

	public void setArtURL( String artURL )
	{
		if ( artURL == null )
			this.artURL = null;
		else if ( !artURL.equals( "" ) )
			this.artURL = artURL;
	}

	public Container getParent()
	{
		return parent;
	}

	public void setParent( Container parent )
	{
		this.parent = parent;
	}

	public String sortString()
	{
		String name = toString().toUpperCase();
		return name.startsWith( "THE " ) && name.length() > 4 ? name.substring( 4 ) : name;
	}

	public int compareTo( DirEntry other )
	{
		char sortLetter = this.sortString().charAt( 0 );
		char otherSortLetter = other.sortString().charAt( 0 );

		if ( ((sortLetter < 'A' || sortLetter > 'Z') && (otherSortLetter < 'A' || otherSortLetter > 'Z'))
				|| ((sortLetter >= 'A' && sortLetter <= 'Z') && (otherSortLetter >= 'A' && otherSortLetter <= 'Z')) )
			return this.sortString().compareTo( other.sortString() );

		if ( sortLetter < 'A' || sortLetter > 'Z' )
			return -1;
		else
			return 1;
	}
}
