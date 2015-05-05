package com.plugplayer.recivaremote.activities;

import com.plugplayer.plugplayer.upnp.PlugPlayerControlPoint;
import com.plugplayer.plugplayer.upnp.Renderer;
import com.plugplayer.recivaremote.R;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;

public class PlaylistActivity extends com.plugplayer.plugplayer.activities.PlaylistActivity
{
	@Override
	public void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );
	}
	
	@Override
	public boolean onPrepareOptionsMenu( Menu menu )
	{
		return true;
	}
	
	public void clearPlaylist(View view)
	{
		final Renderer r = PlugPlayerControlPoint.getInstance().getCurrentRenderer();
		if ( r != null )
			r.removePlaylistEntries( 0, r.getPlaylistEntryCount() );
	}
	
	public void addToPlaylist(View view)
	{
		Intent intent = new Intent( this, ServersActivity.class );
		startActivity( intent );
	}
	
	@Override
	public void playlistChanged()
	{
		handler.post( new Runnable()
		{
			public void run()
			{
				Renderer r = PlugPlayerControlPoint.getInstance().getCurrentRenderer();

				if ( r == null )
					return;
				
				if ( r.getPlaylistEntryCount() == 0 )
				{
					getListView().setVisibility( View.GONE );
					findViewById( R.id.title ).setVisibility( View.VISIBLE );
					findViewById( R.id.cancel ).setVisibility( View.GONE );
				}
				else
				{
					getListView().setVisibility( View.VISIBLE );
					findViewById( R.id.title ).setVisibility( View.GONE );
					findViewById( R.id.cancel ).setVisibility( View.VISIBLE );
				}

				setListAdapter( new DirEntryAdapter( PlaylistActivity.this, r ) );
			}
		} );
	}
}
