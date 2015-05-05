package com.plugplayer.recivaremote.activities;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.plugplayer.plugplayer.upnp.ControlPointListener;
import com.plugplayer.plugplayer.upnp.PlugPlayerControlPoint;
import com.plugplayer.plugplayer.upnp.Renderer;
import com.plugplayer.plugplayer.upnp.Server;
import com.plugplayer.recivaremote.R;


public class ServersActivity extends ListActivity implements ControlPointListener
{
	Handler handler = new Handler();
	
	ServersListAdapter listAdapter;
	
	static ServersActivity me;
	
	@Override
	public void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );
		setContentView( R.layout.servers );
		
		me = this;
		
		listAdapter = new ServersListAdapter( this );
		setListAdapter( listAdapter );

		final ListView lv = getListView();
		lv.setTextFilterEnabled( false );
		lv.setOnItemClickListener( new OnItemClickListener()
		{
			public void onItemClick( AdapterView<?> parent, View view, int position, long id )
			{
				Server device = (Server)lv.getItemAtPosition( position );

				// Not sure how this could have happened; perhaps a race condition?
				if ( device == null )
					return;

				device.updateAlive();

				if ( !device.isAlive() )
				{
					((BaseAdapter)getListAdapter()).notifyDataSetChanged();
					return;
				}

				PlugPlayerControlPoint.getInstance().setCurrentServer( null );
				PlugPlayerControlPoint.getInstance().setCurrentServer( device );
				
				BrowseActivityGroup.reset();
				Intent intent = new Intent( ServersActivity.this, BrowseActivityGroup.class );
				startActivity( intent );
			}
		} );
		
		PlugPlayerControlPoint controlPoint = PlugPlayerControlPoint.getInstance();
		if ( controlPoint == null )
		{
			Log.i( android.util.PlugPlayerUtil.DBG_TAG, "onCreate controlPoint is null, so instantiate it:plugplayer.ServersActivity" );
			controlPoint = new PlugPlayerControlPoint();
			controlPoint.start();
		}
		controlPoint.addControlPointListener( this );	
	}
	
	@Override
	protected void onDestroy()
	{
		super.onDestroy();

		PlugPlayerControlPoint controlPoint = PlugPlayerControlPoint.getInstance();
		if ( controlPoint != null )
			controlPoint.removeControlPointListener( this );
	}
	
	private class ServersListAdapter extends ArrayAdapter<Server>
	{
		public ServersListAdapter(Context context)
		{
			super(context,0);
			updateList();
		}
		
		public void updateList()
		{
			clear();
			
			PlugPlayerControlPoint controlPoint = PlugPlayerControlPoint.getInstance();
			for( Server s : controlPoint.getServerList() )
				add( s );
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent)
		{
			if ( convertView == null )
			{
				LayoutInflater vi = (LayoutInflater)getContext().getSystemService( Context.LAYOUT_INFLATER_SERVICE );
				convertView = vi.inflate( R.layout.server_list_item, null );
			}
			
			TextView t = (TextView)convertView.findViewById( R.id.title );
			ImageView i = (ImageView)convertView.findViewById( R.id.icon );

			Server s = getItem( position );
			s.loadIcon(i);
			t.setText( s.getName() );

			if ( s.isAlive() )
			{
				t.setTextColor( 0xFFFFFFFF );
			}
			else
			{
				t.setTextColor( 0x44FFFFFF );
			}
			
			return convertView;
		}
	}

	@Override
	public void mediaServerListChanged()
	{
		handler.post( new Runnable()
		{
			public void run()
			{
				listAdapter.updateList();
			}
		});
	}
	
	@Override
	public void mediaRendererChanged(Renderer newRenderer, Renderer oldRenderer)
	{
	}

	@Override
	public void mediaRendererListChanged()
	{
		Log.i(android.util.PlugPlayerUtil.DBG_TAG, "inside mediaRendererListChanged, no implementation:ServersActivity");
	}

	@Override
	public void mediaServerChanged(Server newServer, Server oldServer)
	{
	}
	
	@Override
	public void onErrorFromDevice(String error)
	{
	}
}
