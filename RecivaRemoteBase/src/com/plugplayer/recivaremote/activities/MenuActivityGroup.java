package com.plugplayer.recivaremote.activities;

import java.util.ArrayList;

import android.app.ActivityGroup;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ListView;

import com.plugplayer.plugplayer.upnp.ControlPointListener;
import com.plugplayer.plugplayer.upnp.PlugPlayerControlPoint;
import com.plugplayer.plugplayer.upnp.RecivaRenderer;
import com.plugplayer.plugplayer.upnp.RecivaRenderer.MenuEntry;
import com.plugplayer.plugplayer.upnp.Renderer;
import com.plugplayer.plugplayer.upnp.Server;
import com.plugplayer.recivaremote.R;
//import android.app.Activity;
//import android.content.SharedPreferences;

public class MenuActivityGroup extends ActivityGroup implements ControlPointListener
{
	private final Handler handler = new Handler();

	public static MenuActivityGroup group = null;
	
	static Renderer lastRenderer = null;
	
	private static ArrayList<MenuEntry> dirHistory = new ArrayList<MenuEntry>();

	private ArrayList<View> history;
	MenuEntry topParentEntry;
	MenuEntry rootEntry;
	String searchText;
	boolean errorInLoadingMenu = false;
	
	static ArrayList<MenuActivity> activities = new ArrayList<MenuActivity>();

	@Override
	protected void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );
		
		this.history = new ArrayList<View>();
		
		group = this;
		
		
		executeCommonCreate();
	}
	
	private void executeCommonCreate()
	{
		PlugPlayerControlPoint.getInstance().addControlPointListener( this );
		
		Renderer currentRenderer = PlugPlayerControlPoint.getInstance().getCurrentRenderer();
		
		if ( dirHistory.isEmpty() || currentRenderer != lastRenderer )
			mediaRendererChanged( currentRenderer, lastRenderer );
		else
		{
			ArrayList<MenuEntry> dirHistoryCopy = new ArrayList<MenuEntry>();
			dirHistoryCopy.addAll( dirHistory );
			dirHistory.clear();
			replaceView( new View( this ) );
			MenuActivity lastActivity = null;
			for ( MenuEntry c : dirHistoryCopy )
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

	public boolean back()
	{
		return back(false);
	}	
	
	public boolean back(boolean error)
	{
		System.out.println( "In Back Error: " + error );		
		if ( history.size() > 2 )
		{
			if(error == false) 
			{
				((RecivaRenderer)PlugPlayerControlPoint.getInstance().getCurrentRenderer()).goBackMenu();
			}
			dirHistory.remove( dirHistory.size() - 1 );
			history.remove( history.size() - 1 );
			setContentView( history.get( history.size() - 1 ) );

			ListView list = (ListView)history.get( history.size() - 1 ).findViewById( android.R.id.list );
			if(list != null)
				list.invalidateViews();

			activities.remove( activities.size() - 1 );

			MenuActivity currentActivity = activities.get( activities.size() - 1 );
			
			System.out.println( "Back to " + currentActivity.title.getText() );
			currentActivity.onBack();

			return true;
		}
		else if(error == true) 
		{
			errorInLoadingMenu = true;
			dirHistory.clear();
			history.clear();
			activities.remove( activities.size() - 1 );
			return true;
		} else
		{
			return false;
		}
	}

	@Override
	public void onBackPressed()
	{
		boolean loadingError = ((RecivaRenderer)PlugPlayerControlPoint.getInstance().getCurrentRenderer()).isLoadingError();

		back();
	}

	public static MenuActivity pushContainer( MenuActivity parent, MenuEntry entry, String searchText )
	{
		group.searchText = searchText;

		Intent intent = new Intent( parent == null ? MenuActivityGroup.group : parent, MenuActivity.class );
		group.topParentEntry = entry;
		dirHistory.add( entry );
		View newView = group.getLocalActivityManager().startActivity( "MenuActivity", intent.addFlags( Intent.FLAG_ACTIVITY_CLEAR_TOP ) ).getDecorView();
		group.replaceView( newView );

		MenuActivity newActivity = (MenuActivity)group.getLocalActivityManager().getCurrentActivity();
		activities.add( newActivity );

		return newActivity;
	}

	public static MenuActivity pushContainer(MenuActivity parent, MenuEntry entry)
	{
		return pushContainer(parent, entry, null);
	}
	
	@Override
	public void mediaRendererChanged(final Renderer newRenderer, Renderer oldRenderer)
	{
		handler.post( new Runnable()
		{
			public void run()
			{
				dirHistory.clear();
				history.clear();
				replaceView( new View( MenuActivityGroup.this ) );

				lastRenderer = newRenderer;

				if ( newRenderer == null )
				{
					setContentView( R.layout.no_radio );
					return;
				}

				rootEntry = topParentEntry = new MenuEntry();
				topParentEntry.number = -1;
				topParentEntry.title = "Menu";
				dirHistory.add( topParentEntry );

				View view = getLocalActivityManager().startActivity( "BrowseActivity",
						new Intent( MenuActivityGroup.this, MenuActivity.class ).addFlags( Intent.FLAG_ACTIVITY_CLEAR_TOP ) ).getDecorView();

				replaceView( view );

				MenuActivity newActivity = (MenuActivity)group.getLocalActivityManager().getCurrentActivity();
				activities.add( newActivity );
			}
		} );
	}

	@Override
	public void mediaServerChanged(Server newServer, Server oldServer)
	{
	}

	@Override
	public void mediaServerListChanged()
	{
	}

	@Override
	public void mediaRendererListChanged()
	{
		Log.i(android.util.PlugPlayerUtil.DBG_TAG, "inside mediaRendererListChanged, no implementation:MenuActivityActivity");
	}

	@Override
	public void onErrorFromDevice(String error)
	{
	}
	
	@Override
	public void onResume()
	{
		if(errorInLoadingMenu == true) {
			System.out.println("In Resume in MenuActivity Group");
			errorInLoadingMenu = false;
			executeCommonCreate();
		}
		super.onResume();
	}
	
	
}
