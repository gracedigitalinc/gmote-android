package com.plugplayer.plugplayer.activities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.app.Activity;
import android.app.ListActivity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SectionIndexer;
import android.widget.TextView;

import com.example.android.imagedownloader.ImageDownloader;
import com.plugplayer.plugplayer.R;
import com.plugplayer.plugplayer.upnp.Container;
import com.plugplayer.plugplayer.upnp.DirEntry;
import com.plugplayer.plugplayer.upnp.DirEntry.SelectionState;
import com.plugplayer.plugplayer.upnp.Item;
import com.plugplayer.plugplayer.upnp.LinnDavaarRenderer;
import com.plugplayer.plugplayer.upnp.MediaDevice;
import com.plugplayer.plugplayer.upnp.PlugPlayerControlPoint;
import com.plugplayer.plugplayer.upnp.Renderer;
import com.plugplayer.plugplayer.upnp.Server;
import com.plugplayer.plugplayer.upnp.Server.BrowseResultsListener;
import com.plugplayer.plugplayer.utils.ArtFinder;

public class BrowseActivity extends ListActivity implements View.OnClickListener
{
	private final MyHandler handler = new MyHandler();

	private static class MyHandler extends Handler
	{
		public Thread t = Thread.currentThread();
	};

	protected Container parentEntry;
	protected DirEntryAdapter adapter;
	ProgressBar progressBar;
	protected TextView title;

	public void onBack()
	{
		Intent intent = getIntent();
		if ( Intent.ACTION_SEARCH.equals( intent.getAction() ) )
		{
		}
		else
		{
			// final ListView lv = getListView();
			// lv.setFastScrollEnabled( false );

			adapter.loadMore = true;
			adapter.clear();
			adapter.internalList.clear();

			PlugPlayerControlPoint.getInstance().getCurrentServer().browseDir( parentEntry, adapter );
		}
	}

	@Override
	public void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );
		setContentView( R.layout.browse );

		progressBar = (ProgressBar)findViewById( R.id.progress );
		title = (TextView)findViewById( R.id.title );

		parentEntry = BrowseActivityGroup.group.topParentEntry;
		title.setText( parentEntry.getTitle() );

		adapter = new DirEntryAdapter( this, new IndexedList<DirEntry>() );

		Intent intent = getIntent();
		if ( Intent.ACTION_SEARCH.equals( intent.getAction() ) )
		{
			String query = intent.getStringExtra( SearchManager.QUERY );
			PlugPlayerControlPoint.getInstance().getCurrentServer().searchDir( parentEntry, query, adapter );
		}
		else
		{
			PlugPlayerControlPoint.getInstance().getCurrentServer().browseDir( parentEntry, adapter );
		}

		MainActivity.me.getWindow().setFeatureInt( Window.FEATURE_PROGRESS, 10000 );
		setListAdapter( adapter );

		final ListView lv = getListView();
		lv.setTextFilterEnabled( false );

		lv.setOnItemClickListener( new OnItemClickListener()
		{
			public void onItemClick( AdapterView<?> parent, View view, int position, long id )
			{
				DirEntry entry = (DirEntry)lv.getItemAtPosition( position );

				if ( entry instanceof Container )
				{
					BrowseActivityGroup.pushContainer( BrowseActivity.this, (Container)entry );
					adapter.stopLoading();
				}
				else
				{
					if ( BrowseActivityGroup.group.select == false )
					{
						Renderer r = PlugPlayerControlPoint.getInstance().getCurrentRenderer();
						if ( r == null )
							return;

						ArrayList<Item> entryList = new ArrayList<Item>();
						int track = -1;
						for ( int i = 0; i < parentEntry.getChildCount(); ++i )
						{
							DirEntry child = parentEntry.getChild( i );

							if ( child instanceof Item )
							{
								int index = entryList.size();

								entryList.add( (Item)child );

								if ( child == entry )
									track = index;
							}
						}

						insertEntries( r, entryList, 0, track, true );

						MainActivity.me.setTab( 0 );
					}
					else
					{
						toggleSelection( entry );
					}
				}
			}
		} );
	}

	public static void insertEntries( Renderer r, List<Item> entryList, int insertIndex, int playIndex, boolean clear )
	{
		if ( r instanceof LinnDavaarRenderer )
		{
			try
			{
				((LinnDavaarRenderer)r).insertPlaylistEntries( entryList, insertIndex, playIndex, clear );
			}
			catch ( InterruptedException e )
			{
				e.printStackTrace();
			}
		}
		else
		{
			r.inserting = true;

			if ( clear )
			{
				r.stop();
				r.removePlaylistEntries( 0, r.getPlaylistEntryCount() );
			}

			for ( Item child : entryList )
			{
				int index = r.getPlaylistEntryCount();
				r.insertPlaylistEntry( child, index );
			}

			r.inserting = false;

			r.emitPlaylistChanged();

			if ( playIndex != -1 )
			{
				r.setTrackNumber( playIndex );
				r.play();
			}
		}
	}

	public void toggleSelection( DirEntry child )
	{
		synchronized ( child )// not sure if this will fix the "ConcurrentModificationException" as it can happen event with single thread
		{

			if ( child.getSelectionState() == SelectionState.Selected )
				child.setSelectionState( SelectionState.Unselected );
			else
				child.setSelectionState( SelectionState.Selected );

			if ( child instanceof Container )
				((Container)child).pushSelectionStateDown( child.getSelectionState() );
			child.pushSelectionStateUp( child.getSelectionState() );

		}
		getListView().invalidateViews();
	}

	public void onClick( View v )
	{
		ImageButton button = (ImageButton)v;

		DirEntry child = (DirEntry)getListAdapter().getItem( (Integer)button.getTag() );

		toggleSelection( child );
	}

	@Override
	public boolean onPrepareOptionsMenu( Menu menu )
	{
		menu.clear();

		if ( BrowseActivityGroup.group.select == false )
		{
			menu.add( 0, 0, 0, R.string.select );
			menu.add( 0, 5, 4, R.string.search_no_colon );
		}
		else
		{
			menu.add( 0, 1, 0, R.string.add_to_playlist );
			menu.add( 0, 2, 1, R.string.cancel );
		}

		return true;
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem item )
	{
		final ListView lv = getListView();

		final SharedPreferences preferences = getSharedPreferences( SettingsEditor.ADVANCED_SETTINGS, Activity.MODE_PRIVATE );

		if ( item.getItemId() == 0 && BrowseActivityGroup.group.rootEntry != null ) // select
		{
			BrowseActivityGroup.group.rootEntry.pushSelectionStateDown( SelectionState.Unselected );

			BrowseActivityGroup.group.select = true;
			lv.invalidateViews();
			return true;
		}
		else if ( item.getItemId() == 5 ) // Search
		{
			final int DISPATCH_KEY_FROM_IME = 1011;

			KeyEvent evt = new KeyEvent( KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SEARCH );
			Message msg = new Message();
			msg.what = DISPATCH_KEY_FROM_IME;
			msg.obj = evt;
			getListView().getHandler().sendMessage( msg );

			evt = new KeyEvent( KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SEARCH );
			msg = new Message();
			msg.what = DISPATCH_KEY_FROM_IME;
			msg.obj = evt;
			getListView().getHandler().sendMessage( msg );

			return true;
		}
		else if ( item.getItemId() == 1 ) // Done
		{
			List<Item> items = getSelectedItems( PlugPlayerControlPoint.getInstance().getCurrentServer(), BrowseActivityGroup.group.rootEntry,
					new ArrayList<Item>() );

			Renderer r = PlugPlayerControlPoint.getInstance().getCurrentRenderer();
			if ( r == null )
				return true;

			insertEntries( r, items, r.getPlaylistEntryCount(), -1, false );

			if ( preferences.getBoolean( SettingsEditor.TAP_TO_PLAY, true ) )
				BrowseActivityGroup.group.select = false;
			else
				BrowseActivityGroup.group.rootEntry.pushSelectionStateDown( SelectionState.Unselected );

			lv.invalidateViews();

			return true;
		}
		else if ( item.getItemId() == 2 ) // Cancel
		{
			if ( preferences.getBoolean( SettingsEditor.TAP_TO_PLAY, true ) )
				BrowseActivityGroup.group.select = false;
			else
				BrowseActivityGroup.group.rootEntry.pushSelectionStateDown( SelectionState.Unselected );

			lv.invalidateViews();

			return true;
		}

		return super.onOptionsItemSelected( item );
	}

	protected List<Item> getSelectedItems( Server currentServer, Container rootEntry, List<Item> rval )
	{
		if ( rootEntry.getSelectionState() == SelectionState.PartiallySelected || rootEntry.getSelectionState() == SelectionState.Selected )
		{
			currentServer.browseDir( rootEntry );

			for ( DirEntry child : rootEntry.getChildren() )
			{
				if ( child instanceof Container )
					getSelectedItems( currentServer, (Container)child, rval );
				else if ( child.getSelectionState() == SelectionState.Selected )
					rval.add( (Item)child );
			}
		}

		return rval;
	}

	@Override
	public boolean onSearchRequested()
	{
		// return BrowseActivityGroup.group.onSearchRequested();
		if ( !Intent.ACTION_SEARCH.equals( getIntent().getAction() ) && PlugPlayerControlPoint.getInstance().getCurrentServer().canSearch() )
			return MainActivity.me.onSearchRequested();
		else
			return false;
	}

	@Override
	public void onBackPressed()
	{
		adapter.stopLoading();

		if ( !BrowseActivityGroup.group.back() )
			super.onBackPressed();
	}

	// mit fix crash 5:fix for at com.plugplayer.plugplayer.activities.BrowseActivity.onBackPressed(BrowseActivity.java:349
	// This is a known bug in the support package: //dont call for super(). Bug on API Level > 11.
	@Override
	protected void onSaveInstanceState( Bundle outState )
	{
		outState.putString( "WORKAROUND_FOR_BUG_19917_KEY", "WORKAROUND_FOR_BUG_19917_VALUE" );
		super.onSaveInstanceState( outState );
	}

	@SuppressWarnings( "serial" )
	private static class IndexedList<T extends DirEntry> extends ArrayList<T>
	{
		private boolean indexed = false;

		private final char[] sections = { '#', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V',
				'W', 'X', 'Y', 'Z' };
		private final String[] sectionNames = { "#", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U",
				"V", "W", "X", "Y", "Z" };
		private final int[] sectionPositions = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

		@Override
		public void clear()
		{
			super.clear();

			for ( int i = 0; i < sectionPositions.length; ++i )
				sectionPositions[i] = 0;

			indexed = false;
		}

		public void setIndexed( boolean indexed )
		{
			if ( !indexed )
			{
				Collections.sort( this );

				int section = 0;
				for ( int index = 0; index < size(); ++index )
				{
					DirEntry dirEntry = get( index );

					char sortLetter = dirEntry.sortString().charAt( 0 );

					if ( sortLetter > sections[section] && sortLetter < 'Z' )
					{
						section++;
						sectionPositions[section] = index;
					}
				}
			}

			this.indexed = indexed;
		}

		public boolean isIndexed()
		{
			return this.indexed;
		}

		@Override
		public boolean add( T object )
		{
			if ( !indexed )
				return super.add( object );

			int index = Collections.binarySearch( this, object );
			if ( index < 0 )
				index = -index - 1;

			if ( index > size() )
				super.add( object );
			else
				super.add( index, object );

			for ( int section = sectionForLetter( ((DirEntry)object).sortString().charAt( 0 ) ) + 1; section < sections.length; ++section )
			{
				sectionPositions[section]++;
			}

			return true;
		}

		private int sectionForLetter( char letter )
		{
			if ( letter < 'A' || letter > 'Z' )
				return 0;
			else
				return letter - 'A' + 1;
		}

		public int getPositionForSection( int section )
		{
			return sectionPositions[section];
		}

		public int getSectionForPosition( int position )
		{
			for ( int section = 1; section < sections.length; ++section )
			{
				int sectionPosition = sectionPositions[section];

				if ( sectionPosition > position )
					return section - 1;
			}

			return 0;
		}

		public Object[] getSections()
		{
			return sectionNames;
		}
	}

	protected View getView( final int position, View convertView, ViewGroup parent, DirEntry entry )
	{
		// For some reason this was breaking the image loading because the same imageview was being sent over and over.
		// if ( convertView == null )
		{
			LayoutInflater vi = (LayoutInflater)getSystemService( Context.LAYOUT_INFLATER_SERVICE );

			if ( entry instanceof Container )
				convertView = vi.inflate( R.layout.browsecontainer_list_item, null );
			else
				convertView = vi.inflate( R.layout.browseitem_list_item, null );
		}

		final SharedPreferences preferences = getSharedPreferences( SettingsEditor.ADVANCED_SETTINGS, Activity.MODE_PRIVATE );

		TextView t = (TextView)convertView.findViewById( R.id.title );
		t.setText( entry.getTitle() );
		t.setSelected( true );
		t.setEllipsize( (preferences.getBoolean( SettingsEditor.SCROLLING_LABELS, true )) ? TextUtils.TruncateAt.MARQUEE : TextUtils.TruncateAt.END );

		Server s = PlugPlayerControlPoint.getInstance().getCurrentServer();

		if ( entry instanceof Item )
		{
			TextView time = (TextView)convertView.findViewById( R.id.time );

			int seconds = (int)((Item)entry).getTotalSeconds();

			if ( seconds > 0 )
				time.setText( MediaDevice.stringFromSecs( seconds ) );
			else
				time.setText( "" );

			TextView album = (TextView)convertView.findViewById( R.id.album );
			album.setText( ((Item)entry).getAlbum() + " - " + ((Item)entry).getArtist() );
			album.setSelected( true );
			album.setEllipsize( (preferences.getBoolean( SettingsEditor.SCROLLING_LABELS, true )) ? TextUtils.TruncateAt.MARQUEE : TextUtils.TruncateAt.END );

			ImageView art = (ImageView)convertView.findViewById( R.id.art );

			if ( preferences.getBoolean( SettingsEditor.ART_BROWSE, true ) )
			{
				art.setVisibility( View.VISIBLE );
				ImageDownloader.thumbnail.download( s.overrideBase( ((Item)entry).getArtURL() ), art );
			}
			else
				art.setVisibility( View.GONE );
		}
		else
		{
			ImageView art = (ImageView)convertView.findViewById( R.id.art );

			if ( preferences.getBoolean( SettingsEditor.ART_BROWSE, true ) )
			{
				art.setVisibility( View.VISIBLE );
				ArtFinder.loadArt( (Container)entry, s, art );
			}
			else
				art.setVisibility( View.GONE );
		}

		ImageButton i = (ImageButton)convertView.findViewById( R.id.select );
		if ( BrowseActivityGroup.group.select == false )
			i.setVisibility( ImageButton.GONE );
		else
		{
			switch ( entry.getSelectionState() )
			{
				case Unselected:
					i.setImageResource( R.drawable.notselected );
					break;
				case Selected:
					i.setImageResource( R.drawable.selected );
					break;
				case PartiallySelected:
					i.setImageResource( R.drawable.partialselected );
					break;
			}

			i.setTag( position );
			i.setVisibility( ImageButton.VISIBLE );
		}

		convertView.setOnClickListener( new OnClickListener()
		{
			public void onClick( View v )
			{
				getListView().getOnItemClickListener().onItemClick( null, v, position, 0 );
			}
		} );
		return convertView;
	}

	public class DirEntryAdapter extends ArrayAdapter<DirEntry> implements BrowseResultsListener, SectionIndexer
	{
		private final IndexedList<DirEntry> internalList;

		public DirEntryAdapter( Context context, IndexedList<DirEntry> internalList )
		{
			super( context, R.layout.browsecontainer_list_item, internalList );
			this.internalList = internalList;
		}

		@Override
		public View getView( final int position, View convertView, ViewGroup parent )
		{
			DirEntry entry = getItem( position );

			return BrowseActivity.this.getView( position, convertView, parent, entry );
		}

		@Override
		public boolean areAllItemsEnabled()
		{
			return true;
		}

		@Override
		public boolean isEnabled( int position )
		{
			return true;
		}

		// @Override
		// public int getCount()
		// {
		// return parent.getChildCount();
		// }
		//
		// @Override
		// public Object getItem( int position )
		// {
		// return parent.getChild( position );
		// }
		//
		// @Override
		// public long getItemId( int position )
		// {
		// return position;
		// }

		@Override
		public int getItemViewType( int position )
		{
			DirEntry entry = getItem( position );

			if ( entry instanceof Container )
				return 0;
			else
				return 1;
		}

		@Override
		public int getViewTypeCount()
		{
			return 2;
		}

		// @Override
		// public boolean hasStableIds()
		// {
		// return false;
		// }
		//
		// @Override
		// public boolean isEmpty()
		// {
		// return false;
		// }

		// @Override
		// public void registerDataSetObserver( DataSetObserver observer )
		// {
		// }
		//
		// @Override
		// public void unregisterDataSetObserver( DataSetObserver observer )
		// {
		// }

		public void onInitialChildren( final ArrayList<DirEntry> copy, final int totalCount )
		{
			// System.out.println( "onInitialChildren " + totalCount );

			if ( copy.size() > getCount() )
			{
				final Runnable r = new Runnable()
				{
					public void run()
					{
						clear();
						onMoreChildren( copy, totalCount );
					}
				};

				if ( Thread.currentThread() == handler.t )
					r.run();
				else
					handler.post( r );
			}
		}

		public void onMoreChildren( final List<DirEntry> children, final int total )
		{
			// System.out.println( "onMoreChildren " + total );

			final Runnable r = new Runnable()
			{
				public void run()
				{
					final SharedPreferences preferences = getSharedPreferences( SettingsEditor.ADVANCED_SETTINGS, Activity.MODE_PRIVATE );

					if ( total > preferences.getInt( SettingsEditor.INDEX_THRESHOLD, 40 ) && !internalList.isIndexed() )
					{
						getListView().setFastScrollEnabled( true );
						DirEntryAdapter.this.notifyDataSetChanged();

						internalList.setIndexed( true );
					}

					for ( DirEntry child : children )
						add( child );

					if ( total == -1 || (total != 0 && getCount() == 0) )
					{
						progressBar.setIndeterminate( true );
						progressBar.setVisibility( View.VISIBLE );
						// title.setText( parentEntry.getTitle() );
					}
					else if ( total == getCount() )
					{
						progressBar.setVisibility( View.INVISIBLE );
						// title.setText( parentEntry.getTitle() );
					}
					else
					{
						progressBar.setVisibility( View.VISIBLE );
						progressBar.setIndeterminate( false );
						progressBar.setMax( total );
						progressBar.setProgress( getCount() );
						// title.setText( parentEntry.getTitle() + " (" + getCount() + " of " + total + ")" );
					}

					progressBar.invalidate();
				}
			};

			if ( Thread.currentThread() == handler.t )
				r.run();
			else
				handler.post( r );
		}

		public void onDone()
		{
			// System.out.println( "onDone" );

			final Runnable r = new Runnable()
			{
				public void run()
				{
					progressBar.setVisibility( View.INVISIBLE );
				}
			};

			if ( Thread.currentThread() == handler.t )
				r.run();
			else
				handler.post( r );
		}

		private boolean loadMore = true;

		public void stopLoading()
		{
			loadMore = false;
		}

		public boolean loadMore()
		{
			return loadMore;
		}

		@Override
		public void add( DirEntry object )
		{
			// if ( getListView().getLastVisiblePosition() != getCount() - 1 )
			// setNotifyOnChange( false );

			super.add( object );

			// setNotifyOnChange( true );
		}

		public int getPositionForSection( int section )
		{
			return internalList.getPositionForSection( section );
		}

		public int getSectionForPosition( int position )
		{
			return internalList.getSectionForPosition( position );
		}

		public Object[] getSections()
		{
			return internalList.getSections();
		}
	}
}
