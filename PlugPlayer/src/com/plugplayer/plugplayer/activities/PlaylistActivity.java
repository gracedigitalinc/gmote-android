package com.plugplayer.plugplayer.activities;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.commonsware.cwac.tlv.TouchListView;
import com.commonsware.cwac.tlv.TouchListView.DropListener;
import com.commonsware.cwac.tlv.TouchListView.RemoveListener;
import com.example.android.imagedownloader.ImageDownloader;
import com.plugplayer.plugplayer.R;
import com.plugplayer.plugplayer.upnp.ControlPointListener;
import com.plugplayer.plugplayer.upnp.DirEntry;
import com.plugplayer.plugplayer.upnp.Item;
import com.plugplayer.plugplayer.upnp.MediaDevice;
import com.plugplayer.plugplayer.upnp.PlugPlayerControlPoint;
import com.plugplayer.plugplayer.upnp.Renderer;
import com.plugplayer.plugplayer.upnp.Renderer.PlayMode;
import com.plugplayer.plugplayer.upnp.Renderer.PlayState;
import com.plugplayer.plugplayer.upnp.RendererListener;
import com.plugplayer.plugplayer.upnp.Server;

public class PlaylistActivity extends ListActivity implements ControlPointListener, RendererListener
{
	PlugPlayerControlPoint controlPoint;
	protected final Handler handler = new Handler();

	@Override
	public void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );
		setContentView( R.layout.playlist );

		controlPoint = PlugPlayerControlPoint.getInstance();
		controlPoint.addControlPointListener( this );

		final TouchListView lv = (TouchListView)getListView();
		lv.setTextFilterEnabled( false );

		lv.setDropListener( new DropListener()
		{
			public void drop( int from, int to )
			{
				final Renderer r = controlPoint.getCurrentRenderer();
				r.movePlaylistEntry( from, to );
			}
		} );

		lv.setRemoveListener( new RemoveListener()
		{
			public void remove( int which )
			{
				final Renderer r = controlPoint.getCurrentRenderer();
				r.removePlaylistEntry( which );
			}
		} );

		lv.setOnItemClickListener( new OnItemClickListener()
		{
			public void onItemClick( AdapterView<?> parent, View view, int position, long id )
			{
				final Renderer r = controlPoint.getCurrentRenderer();
				r.setTrackNumber( position );
				r.play();
				// MainActivity.me.setTab( 0 );
			}
		} );

		final Renderer r = controlPoint.getCurrentRenderer();
		mediaRendererChanged( r, null );
	}

	@Override
	public void onBackPressed()
	{
	}

	@Override
	public boolean onPrepareOptionsMenu( Menu menu )
	{
		menu.clear();

		final Renderer r = controlPoint.getCurrentRenderer();

		menu.add( 0, 1, 1, R.string.load_playlist );

		if ( r.getPlaylistEntryCount() != 0 )
		{
			menu.add( 0, 0, 0, R.string.save_playlist );
			menu.add( 0, 2, 2, R.string.clear_playlist );
		}

		return true;
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem item )
	{
		if ( item.getItemId() == 2 ) // Clear
		{
			final Renderer r = controlPoint.getCurrentRenderer();
			r.stop();
			r.removePlaylistEntries( 0, r.getPlaylistEntryCount() );
			r.setTrackNumber( -1 );
			return true;
		}
		else if ( item.getItemId() == 1 ) // Load
		{
			PlaylistsActivity.save = false;
			Intent intent = new Intent( this, PlaylistsActivity.class );
			startActivity( intent );
			return true;
		}
		else if ( item.getItemId() == 0 ) // Save
		{
			PlaylistsActivity.save = true;
			Intent intent = new Intent( this, PlaylistsActivity.class );
			startActivity( intent );
			return true;
		}

		return super.onOptionsItemSelected( item );
	}

	@Override
	public boolean onSearchRequested()
	{
		return false;
	}

	protected class DirEntryAdapter implements Adapter, ListAdapter
	{
		private final Renderer r;
		private final int playlistCount;

		public DirEntryAdapter( Context context, Renderer r )
		{
			this.r = r;
			this.playlistCount = r.getPlaylistEntryCount();
		}

		public View getView( int position, View convertView, ViewGroup parent )
		{
			if ( convertView == null )
			{
				LayoutInflater vi = (LayoutInflater)getSystemService( Context.LAYOUT_INFLATER_SERVICE );
				convertView = vi.inflate( R.layout.playlist_list_item, null );
			}

			ImageView playing = (ImageView)convertView.findViewById( R.id.playing );
			playing.setVisibility( View.INVISIBLE );
			Renderer r = controlPoint.getCurrentRenderer();
			if ( r != null && r.getTrackNumber() == position )
			{
				playing.setVisibility( View.VISIBLE );
			}

			final SharedPreferences preferences = getSharedPreferences( SettingsEditor.ADVANCED_SETTINGS, Activity.MODE_PRIVATE );

			TextView t = (TextView)convertView.findViewById( R.id.title );
			t.setSelected( true );
			t.setEllipsize( (preferences.getBoolean( SettingsEditor.SCROLLING_LABELS, true )) ? TextUtils.TruncateAt.MARQUEE : TextUtils.TruncateAt.END );

			TextView album = (TextView)convertView.findViewById( R.id.album );
			album.setSelected( true );
			album.setEllipsize( (preferences.getBoolean( SettingsEditor.SCROLLING_LABELS, true )) ? TextUtils.TruncateAt.MARQUEE : TextUtils.TruncateAt.END );

			ImageView art = (ImageView)convertView.findViewById( R.id.art );
			TextView time = (TextView)convertView.findViewById( R.id.time );

			DirEntry entry = (DirEntry)getItem( position );
			if ( entry == null )
			{
				t.setText( "" );
				album.setText( "" );
				time.setText( "" );
				art.setVisibility( View.GONE );
			}
			else
			{
				t.setText( entry.getTitle() );
				album.setText( ((Item)entry).getAlbum() + " - " + ((Item)entry).getArtist() );

				if ( preferences.getBoolean( SettingsEditor.ART_PLAYLIST, false ) )
				{
					art.setVisibility( View.VISIBLE );
					ImageDownloader.thumbnail.download( ((Item)entry).getArtURL(), art );
				}
				else
					art.setVisibility( View.GONE );

				if ( entry instanceof Item )
				{
					int seconds = (int)((Item)entry).getTotalSeconds();

					if ( seconds > 0 )
						time.setText( MediaDevice.stringFromSecs( seconds ) );
					else
						time.setText( "" );
				}
				else
					time.setText( "" );
			}

			return convertView;
		}

		public boolean areAllItemsEnabled()
		{
			return true;
		}

		public boolean isEnabled( int position )
		{
			return true;
		}

		public int getCount()
		{
			return playlistCount;
		}

		public Object getItem( int position )
		{
			return r.getPlaylistEntry( position );
		}

		public long getItemId( int position )
		{
			return position;
		}

		public int getItemViewType( int position )
		{
			return 0;
		}

		public int getViewTypeCount()
		{
			return 1;
		}

		public boolean hasStableIds()
		{
			return false;
		}

		public boolean isEmpty()
		{
			return false;
		}

		public void registerDataSetObserver( DataSetObserver observer )
		{
		}

		public void unregisterDataSetObserver( DataSetObserver observer )
		{
		}
	}

	public void mediaServerChanged( Server newServer, Server oldServer )
	{
	}

	public void mediaServerListChanged()
	{
	}

	public void mediaRendererChanged( Renderer newRenderer, Renderer oldRenderer )
	{
		if ( oldRenderer != null )
			oldRenderer.removeRendererListener( this );

		if ( newRenderer != null )
			newRenderer.addRendererListener( this );

		playlistChanged();
	}

	public void mediaRendererListChanged()
	{
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "inside mediaRendererListChanged, no implementation:PlaylistActivity" );
	}

	public void error( String message )
	{
		// TODO Auto-generated method stub

	}

	public void playStateChanged( PlayState state )
	{
		// TODO Auto-generated method stub

	}

	public void playModeChanged( PlayMode mode )
	{
		// TODO Auto-generated method stub

	}

	public void playlistChanged()
	{
		handler.post( new Runnable()
		{
			public void run()
			{
				Renderer r = controlPoint.getCurrentRenderer();

				if ( r != null )
				{
					if ( r.getPlaylistEntryCount() == 0 )
					{
						getListView().setVisibility( View.GONE );
						findViewById( R.id.title ).setVisibility( View.VISIBLE );
					}
					else
					{
						getListView().setVisibility( View.VISIBLE );
						findViewById( R.id.title ).setVisibility( View.GONE );
					}

					setListAdapter( new DirEntryAdapter( PlaylistActivity.this, r ) );
				}
				else
				{
					setListAdapter( new ArrayAdapter<String>( PlaylistActivity.this, 0, new String[] {} ) );
				}
			}
		} );
	}

	public void volumeChanged( float volume )
	{
	}

	public void trackNumberChanged( int trackNumber )
	{
		if ( controlPoint.getCurrentRenderer() == null )
			return;

		handler.post( new Runnable()
		{
			public void run()
			{
				setListAdapter( new DirEntryAdapter( PlaylistActivity.this, controlPoint.getCurrentRenderer() ) );
			}
		} );
	}

	public void timeStampChanged( int seconds )
	{
		// TODO Auto-generated method stub

	}

	public void onErrorFromDevice( String error )
	{
	}
}
