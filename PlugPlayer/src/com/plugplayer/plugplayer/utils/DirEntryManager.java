package com.plugplayer.plugplayer.utils;

import java.util.Iterator;
import java.util.LinkedList;

import com.plugplayer.plugplayer.upnp.Container;
import com.plugplayer.plugplayer.upnp.DirEntry;

public class DirEntryManager
{
	private static final int max = 3000;

	private static int itemCount = 0;

	private static LinkedList<Container> cache = new LinkedList<Container>();

	public static synchronized void addEntry( DirEntry newEntry )
	{
		if ( true )
			return;

		if ( newEntry instanceof Container )
			cache.addLast( (Container)newEntry );
		else
			itemCount++;

		System.out.println( "Total Size: " + (itemCount + cache.size()) );

		if ( itemCount + cache.size() > max )
		{
			Container candidate = null;

			int needed = (cache.size() + itemCount) - max;

			candidate = getCandidateWith( 10 + needed );
			if ( candidate == null && needed > 1 )
				candidate = getCandidateWith( 11 );

			if ( candidate == null )
				candidate = getCandidateWith( 1 );

			if ( candidate == null )
				candidate = getCandidateWith( 0 );

			if ( candidate != null )
			{
				if ( candidate.getChildCount() > (10 + needed) )
					removeItems( candidate, needed );
				else if ( candidate.getChildCount() > 11 )
					removeItems( candidate, candidate.getChildCount() - 10 );
				else if ( candidate.getChildCount() > 0 )
					removeItems( candidate, candidate.getChildCount() );
				else
				{
					System.out.println( "Removing container" );
					return;
				}

				cache.addLast( candidate );
			}
		}
	}

	private static void removeItems( Container candidate, int numToRemove )
	{
		System.out.println( "Removing: " + numToRemove );

		for ( int x = 0; x < numToRemove; ++x )
			candidate.removeEntry( candidate.getChildCount() - 1 );

		itemCount -= numToRemove;
	}

	private static Container getCandidateWith( int numChildren )
	{
		// Look for a candidate that is not an Item and has lots of Item children
		// Things at the top of the list are the oldest and potential candidates for removal
		for ( Iterator<Container> i = cache.iterator(); i.hasNext(); )
		{
			Container container = i.next();

			// if ( container.isActive() )
			// continue;

			if ( container.getChildCount() >= numChildren )
			{
				boolean allItems = true;

				for ( int x = container.getChildCount() - (1 + numChildren); x < container.getChildCount(); ++x )
				{
					DirEntry child = container.getChild( x );
					if ( child instanceof Container )
					{
						allItems = false;
						break;
					}
				}

				if ( allItems )
				{
					cache.remove( i );
					return container;
				}
			}
		}

		return null;
	}
}
