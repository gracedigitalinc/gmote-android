package com.plugplayer.recivaremote.activities;


import android.app.ListActivity;
import android.content.Context;
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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.plugplayer.plugplayer.upnp.ControlPointListener;
import com.plugplayer.plugplayer.upnp.PlugPlayerControlPoint;
import com.plugplayer.plugplayer.upnp.RecivaRenderer;
import com.plugplayer.plugplayer.upnp.Renderer;
import com.plugplayer.plugplayer.upnp.Server;
import com.plugplayer.recivaremote.R;

public class RadiosActivity extends ListActivity implements ControlPointListener
{
	private final Handler handler = new Handler();

	RadiosListAdapter listAdapter;
	Button muteAll;
	public static RadiosActivity radioActivityReference = null;
	protected RadiosListAdapter getRadiosListAdapter()
	{
		return new RadiosListAdapter( this );
	}
	
	public int getRadioListSize(){
		return listAdapter.getCount();
	}
	@Override
	public void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );
		radioActivityReference = this;
		setContentView( R.layout.radios );
		muteAll = (Button)findViewById(R.id.muteall);
		
		listAdapter = getRadiosListAdapter();
		setListAdapter( listAdapter );

		final ListView lv = getListView();
		lv.setTextFilterEnabled( false );
		lv.setOnItemClickListener( new OnItemClickListener()
		{
			public void onItemClick( AdapterView<?> parent, View view, int position, long id )
			{
				Renderer device = (Renderer)lv.getItemAtPosition( position );

				// Not sure how this could have happened; perhaps a race condition?
				if ( device == null )
					return;

				device.updateAlive();

				if ( !device.isAlive() )
				{
					((BaseAdapter)getListAdapter()).notifyDataSetChanged();
					return;
				}

				PlugPlayerControlPoint.getInstance().setCurrentRenderer( device );
			}
		} );
		
		PlugPlayerControlPoint controlPoint = PlugPlayerControlPoint.getInstance();
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
	
	public void muteAll(View view)
	{
		Button b = (Button)view;
		if ( b.getText().equals("Mute All") )
		{
			b.setText( "Unmute All" );
			for( Renderer renderer : PlugPlayerControlPoint.getInstance().getRendererList() )
			{
				RecivaRenderer r = (RecivaRenderer)renderer;
				if ( r.isAlive() && !r.isMute() )
					r.toggleMute();
			}
		}
		else
		{
			b.setText( "Mute All" );
			for( Renderer renderer : PlugPlayerControlPoint.getInstance().getRendererList() )
			{
				RecivaRenderer r = (RecivaRenderer)renderer;
				if ( r.isAlive() && r.isMute() )
					r.toggleMute();
			}
		}
	}
	
	public class RadiosListAdapter extends ArrayAdapter<Object>
	{
		public RadiosListAdapter(Context context)
		{
			super(context,0);
			updateList();
		}
		
		public void updateList()
		{
		 Log.i(android.util.PlugPlayerUtil.DBG_TAG, "updatingListAdapter: old list = "+getCount());
			clear();
			
			PlugPlayerControlPoint controlPoint = PlugPlayerControlPoint.getInstance();
			for( Renderer r : controlPoint.getRendererList() )
				if ( r.isAlive() )
					add( r );
			Log.i(android.util.PlugPlayerUtil.DBG_TAG, "updatingListAdapter: new list = "+getCount());
			if ( getCount() == 0 )
				muteAll.setVisibility( View.GONE );
			else
				muteAll.setVisibility( View.VISIBLE );
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent)
		{
			PlugPlayerControlPoint controlPoint = PlugPlayerControlPoint.getInstance();

			if ( convertView == null )
			{
				LayoutInflater vi = (LayoutInflater)getContext().getSystemService( Context.LAYOUT_INFLATER_SERVICE );
				convertView = vi.inflate( R.layout.radio_list_item, null );
			}
			
			TextView t = (TextView)convertView.findViewById( R.id.name );
			ImageView check = (ImageView)convertView.findViewById( R.id.check );

			Renderer r = (Renderer)getItem( position );

			check.setVisibility( View.INVISIBLE );
			if ( r == controlPoint.getCurrentRenderer() )
				check.setVisibility( View.VISIBLE );

			t.setText( r.getName() );

			if ( r.isAlive() )
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
	public void mediaRendererChanged(Renderer newRenderer, Renderer oldRenderer)
	{
		handler.post( new Runnable()
		{
			public void run()
			{
				listAdapter.notifyDataSetChanged();
			}
		});
	}

	@Override
	public void mediaRendererListChanged()
	{
		 Log.i(android.util.PlugPlayerUtil.DBG_TAG, "inside mediaRendererListChanged, handler.post listAdapter.update");
		handler.post( new Runnable()
		{
			public void run()
			{
				listAdapter.updateList();
			}
		});
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
	public void onErrorFromDevice(String error)
	{
	}
}
