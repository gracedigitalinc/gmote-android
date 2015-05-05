package com.plugplayer.plugplayer.activities;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;
import android.view.View.OnKeyListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

import com.plugplayer.plugplayer.R;
import com.plugplayer.plugplayer.upnp.Item;
import com.plugplayer.plugplayer.upnp.Renderer;
import com.plugplayer.plugplayer.utils.StateList;
import com.plugplayer.plugplayer.utils.StateMap;

public class PlaylistsActivity extends ListActivity
{
	public static boolean save = false;

	@Override
	protected void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );

		File dir = new File( getFilesDir() + "/playlists" );
		if ( !dir.exists() )
			dir.mkdir();

		if ( save )
			setTitle( R.string.save_playlist );
		else
			setTitle( R.string.load_playlist );

		List<String> playlists = getPlaylistNames();
		setListAdapter( new ArrayAdapter<String>( this, R.layout.playlistname, playlists ) );

		final ListView lv = getListView();
		lv.setTextFilterEnabled( false );
		lv.setOnItemClickListener( new OnItemClickListener()
		{
			public void onItemClick( AdapterView<?> parent, View view, int position, long id )
			{
				final String playlistName = (String)lv.getItemAtPosition( position );

				Renderer r = MainActivity.me.getControlPoint().getCurrentRenderer();

				if ( r.getPlaylistEntryCount() > 0 )
				{
					OnClickListener buttonListener = new OnClickListener()
					{
						public void onClick( DialogInterface dialog, int which )
						{
							loadPlaylist( playlistName, which == AlertDialog.BUTTON_POSITIVE );
							PlaylistsActivity.this.finish();
						}
					};

					AlertDialog.Builder builder = new AlertDialog.Builder( PlaylistsActivity.this );
					builder.setMessage( R.string.playlist_clear );
					builder.setPositiveButton( R.string.playlist_clear_button, buttonListener );
					builder.setNegativeButton( R.string.playlist_add_button, buttonListener );
					builder.create().show();
				}
				else
				{
					loadPlaylist( playlistName, false );
					PlaylistsActivity.this.finish();
				}
			}
		} );

		lv.setOnCreateContextMenuListener( new OnCreateContextMenuListener()
		{
			public void onCreateContextMenu( ContextMenu menu, View v, ContextMenuInfo menuInfo )
			{
				int position = ((AdapterContextMenuInfo)menuInfo).position;
				String pl = (String)lv.getItemAtPosition( position );

				if ( pl != null )
					menu.add( 0, position, 0, R.string.playlist_delete );
			}
		} );

		if ( save )
		{
			LayoutInflater vi = (LayoutInflater)getSystemService( Context.LAYOUT_INFLATER_SERVICE );
			final EditText e = (EditText)vi.inflate( R.layout.playlistname_edit, null );

			final Builder builder = new AlertDialog.Builder( this );
			builder.setTitle( R.string.save_playlist );
			builder.setView( e );
			builder.setOnCancelListener( new OnCancelListener()
			{
				public void onCancel( DialogInterface dialog )
				{
					PlaylistsActivity.this.finish();
				}
			} );
			final AlertDialog dialog = builder.show();

			e.setOnKeyListener( new OnKeyListener()
			{
				public boolean onKey( final View v, int keyCode, KeyEvent event )
				{
					if ( (event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER) )
					{
						final String newPlaylistName = e.getText().toString();

						savePlaylist( newPlaylistName );

						dialog.dismiss();

						PlaylistsActivity.this.finish();

						return true;
					}

					return false;
				}
			} );
		}
	}

	@Override
	public boolean onContextItemSelected( MenuItem item )
	{
		String pl = (String)getListView().getItemAtPosition( item.getItemId() );

		if ( pl != null )
		{
			File f = new File( getFilesDir() + "/playlists/" + pl );
			f.delete();
			((ArrayAdapter<String>)getListView().getAdapter()).remove( pl );
			return true;
		}

		return super.onContextItemSelected( item );
	}

	public static void savePlaylist( String playlistName )
	{
		try
		{
			Renderer r = MainActivity.me.getControlPoint().getCurrentRenderer();

			StateMap state = new StateMap();
			StateList list = new StateList();
			state.setList( "playlist", list );

			for ( int i = 0; i < r.getPlaylistEntryCount(); ++i )
			{
				Item item = r.getPlaylistEntry( i );
				StateMap itemState = new StateMap();
				item.toState( itemState );
				list.add( itemState );
			}

			state.writeXML( MainActivity.me.getFilesDir() + "/playlists/" + playlistName );
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}
	}

	protected void loadPlaylist( String playlistName, boolean clear )
	{
		try
		{
			Renderer r = MainActivity.me.getControlPoint().getCurrentRenderer();

			StateMap state = StateMap.fromXML( getFilesDir() + "/playlists/" + playlistName );
			StateList list = state.getList( "playlist" );

			r.inserting = true;

			if ( clear )
			{
				r.stop();
				r.removePlaylistEntries( 0, r.getPlaylistEntryCount() );
			}

			for ( StateMap itemState : list )
			{
				Item newItem = Item.createFromState( itemState );
				r.insertPlaylistEntry( newItem, r.getPlaylistEntryCount() );
			}

			r.inserting = false;
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}
	}

	protected List<String> getPlaylistNames()
	{
		File dir = new File( getFilesDir() + "/playlists" );
		return new ArrayList<String>( Arrays.asList( dir.list() ) );
	}
}
