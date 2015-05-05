package com.plugplayer.plugplayer.activities;

import java.util.ArrayList;

import android.app.Activity;
import android.app.ActivityGroup;
import android.app.SearchManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ListView;

import com.plugplayer.plugplayer.R;
import com.plugplayer.plugplayer.upnp.Container;
import com.plugplayer.plugplayer.upnp.ControlPointListener;
import com.plugplayer.plugplayer.upnp.PlugPlayerControlPoint;
import com.plugplayer.plugplayer.upnp.Renderer;
import com.plugplayer.plugplayer.upnp.Server;

public class BrowseActivityGroup extends ActivityGroup implements ControlPointListener
{
	private final Handler handler = new Handler();

	public static BrowseActivityGroup group = null;

	static Server lastServer = null;
	private static ArrayList<Container> dirHistory = new ArrayList<Container>();
	static ArrayList<BrowseActivity> activities = new ArrayList<BrowseActivity>();

	protected ArrayList<View> history;
	Container topParentEntry;
	public Container rootEntry;

	public boolean select;

	public static void reset()
	{
		group = null;
		lastServer = null;
		dirHistory.clear();
		activities.clear();
	}

	@Override
	protected void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );
		this.history = new ArrayList<View>();

		if ( group != null )
			rootEntry = group.rootEntry;

		group = this;

		PlugPlayerControlPoint.getInstance().addControlPointListener( this );

		Server currentServer = PlugPlayerControlPoint.getInstance().getCurrentServer();
		if ( dirHistory.isEmpty() || currentServer != lastServer )
			mediaServerChanged( currentServer, lastServer );
		else
		{
			ArrayList<Container> dirHistoryCopy = new ArrayList<Container>();
			dirHistoryCopy.addAll( dirHistory );
			dirHistory.clear();
			replaceView( new View( this ) );
			BrowseActivity lastActivity = null;
			for ( Container c : dirHistoryCopy )
				lastActivity = pushContainer( lastActivity, c );
		}
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();

		if ( PlugPlayerControlPoint.getInstance() != null )
			PlugPlayerControlPoint.getInstance().removeControlPointListener( this );
	}

	public void replaceView( View v )
	{
		history.add( v );
		setContentView( v );
	}

	@Override
	public boolean onSearchRequested()
	{
		return super.onSearchRequested();
	}

	public boolean back()
	{
		if ( history.size() > 2 )
		{
			dirHistory.remove( dirHistory.size() - 1 );
			history.remove( history.size() - 1 );
			topParentEntry = dirHistory.get( dirHistory.size() - 1 );
			setContentView( history.get( history.size() - 1 ) );

			ListView list = (ListView)history.get( history.size() - 1 ).findViewById( android.R.id.list );
			list.invalidateViews();

			activities.remove( activities.size() - 1 );
			BrowseActivity currentActivity = activities.get( activities.size() - 1 );

			// System.out.println( "Back to " + currentActivity.title.getText() );
			currentActivity.onBack();
			group.getLocalActivityManager().destroyActivity( "BrowseActivity" + dirHistory.size() + 1, true );

			return true;
		}
		else
		{
			// return false;
			return true;
		}
	}

	@Override
	public void onBackPressed()
	{
		if ( !BrowseActivityGroup.group.back() )
			super.onBackPressed();
	}

	protected Intent newBrowseIntent( Activity parent )
	{
		Intent intent = new Intent( parent, BrowseActivity.class );
		return intent;
	}

	public static BrowseActivity pushContainer( BrowseActivity parent, Container entry )
	{
		Intent intent = group.newBrowseIntent( parent == null ? BrowseActivityGroup.group : parent );

		group.topParentEntry = entry;
		dirHistory.add( entry );
		View newView = group.getLocalActivityManager().startActivity( "BrowseActivity" + dirHistory.size(), intent.addFlags( Intent.FLAG_ACTIVITY_CLEAR_TOP ) )
				.getDecorView();
		group.replaceView( newView );

		BrowseActivity newActivity = (BrowseActivity)group.getLocalActivityManager().getCurrentActivity();
		activities.add( newActivity );

		return newActivity;
	}

	public static BrowseActivity pushSearch( String search )
	{
		Intent intent = group.newBrowseIntent( group.getLocalActivityManager().getCurrentActivity() );
		intent.setAction( Intent.ACTION_SEARCH );
		intent.putExtra( SearchManager.QUERY, search );

		Container c = new Container();
		c.setObjectID( group.topParentEntry.getObjectID() );
		c.setTitle( group.getResources().getText( R.string.search ) + " " + search );
		c.setParent( group.topParentEntry );
		c.getParent().getChildren().add( c );
		c.getParent().setTotalCount( group.topParentEntry.getChildCount() );
		group.topParentEntry = c;
		dirHistory.add( c );
		View newView = group.getLocalActivityManager().startActivity( "BrowseActivity" + dirHistory.size(), intent.addFlags( Intent.FLAG_ACTIVITY_CLEAR_TOP ) )
				.getDecorView();
		group.replaceView( newView );

		BrowseActivity newActivity = (BrowseActivity)group.getLocalActivityManager().getCurrentActivity();
		activities.add( newActivity );

		return newActivity;
	}

	public void mediaServerChanged( final Server newServer, final Server oldServer )
	{
		handler.post( new Runnable()
		{
			public void run()
			{
				final SharedPreferences preferences = getSharedPreferences( SettingsEditor.ADVANCED_SETTINGS, Activity.MODE_PRIVATE );
				select = !preferences.getBoolean( SettingsEditor.TAP_TO_PLAY, true );

				dirHistory.clear();
				history.clear();
				replaceView( new View( BrowseActivityGroup.this ) );

				lastServer = newServer;

				if ( newServer == null )
				{
					setContentView( R.layout.no_server );
					return;
				}

				String folderId = getIntent().getStringExtra( "folder" );
				rootEntry = topParentEntry = new Container();
				topParentEntry.setObjectID( folderId == null ? "0" : folderId );
				topParentEntry.setTitle( newServer.getName() );
				dirHistory.add( topParentEntry );

				Intent intent = newBrowseIntent( BrowseActivityGroup.this );

				View view = getLocalActivityManager().startActivity( "BrowseActivity" + dirHistory.size(), intent.addFlags( Intent.FLAG_ACTIVITY_CLEAR_TOP ) )
						.getDecorView();

				replaceView( view );

				BrowseActivity newActivity = (BrowseActivity)group.getLocalActivityManager().getCurrentActivity();
				activities.add( newActivity );
			}
		} );
	}

	public void mediaServerListChanged()
	{
	}

	public void mediaRendererChanged( Renderer newRenderer, Renderer oldRenderer )
	{
	}

	public void mediaRendererListChanged()
	{
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "inside mediaRendererListChanged, no implementation:BrowseActivityGroup" );
	}

	public void onErrorFromDevice( String error )
	{
	}
}
