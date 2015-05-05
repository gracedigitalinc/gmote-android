package com.plugplayer.recivaremote.activities;

import java.util.ArrayList;
import java.util.List;

import android.view.View;

import com.plugplayer.plugplayer.activities.BrowseActivityGroup;
import com.plugplayer.plugplayer.upnp.Item;
import com.plugplayer.plugplayer.upnp.PlugPlayerControlPoint;
import com.plugplayer.plugplayer.upnp.Renderer;
import com.plugplayer.plugplayer.upnp.DirEntry.SelectionState;

public class BrowseActivity extends com.plugplayer.plugplayer.activities.BrowseActivity
{
	public void cancel( View v )
	{
		BrowseActivityGroup.group.rootEntry.pushSelectionStateDown( SelectionState.Unselected );
		BrowseActivityGroup.group.finish();
		ServersActivity.me.finish();
	}

	public void addToPlaylist( View v )
	{
		List<Item> items = getSelectedItems( PlugPlayerControlPoint.getInstance().getCurrentServer(), BrowseActivityGroup.group.rootEntry,
				new ArrayList<Item>() );

		Renderer r = PlugPlayerControlPoint.getInstance().getCurrentRenderer();
		if ( r != null )
		{
			r.inserting = true;

			for ( Item selectedtem : items )
			{
				int index = r.getPlaylistEntryCount();
				r.insertPlaylistEntry( selectedtem, index );
			}

			r.inserting = false;
		}
		
		MainActivity.me.setTab(2);
		cancel( v );
	}
}
