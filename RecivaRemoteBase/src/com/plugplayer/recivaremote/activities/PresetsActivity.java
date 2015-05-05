package com.plugplayer.recivaremote.activities;

import java.util.List;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import com.plugplayer.plugplayer.upnp.ControlPointListener;
import com.plugplayer.plugplayer.upnp.PlugPlayerControlPoint;
import com.plugplayer.plugplayer.upnp.RecivaRenderer;
import com.plugplayer.plugplayer.upnp.RecivaRenderer.Preset;
import com.plugplayer.plugplayer.upnp.Renderer;
import com.plugplayer.plugplayer.upnp.Server;
import com.plugplayer.recivaremote.R;
//import com.plugplayer.plugplayer.activities.BrowseActivityGroup;
//import com.plugplayer.plugplayer.upnp.DirEntry;

public class PresetsActivity extends ListActivity  implements ControlPointListener
{
	private final Handler handler = new Handler();

	PresetsListAdapter listAdapter;
	
	@Override
	public void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );
		setContentView( R.layout.presets );
		
		listAdapter = new PresetsListAdapter( this );
		setListAdapter( listAdapter );

		final ListView lv = getListView();
		lv.setTextFilterEnabled( false );

		lv.setOnItemClickListener( new OnItemClickListener()
		{
			public void onItemClick( AdapterView<?> parent, View view, int position, long id )
			{
				PlugPlayerControlPoint controlPoint = PlugPlayerControlPoint.getInstance();
				RecivaRenderer r = (RecivaRenderer)controlPoint.getCurrentRenderer();
				if (r!= null) {
					r.playPreset( position+1 );
					MainActivity.me.setTab( 0 );
				}
			}
		} );
		
		PlugPlayerControlPoint controlPoint = PlugPlayerControlPoint.getInstance();
		controlPoint.addControlPointListener( this );
		
		//				listAdapter.notifyDataSetChanged();
	}
	
	@Override
	protected void onDestroy()
	{
		super.onDestroy();

		PlugPlayerControlPoint controlPoint = PlugPlayerControlPoint.getInstance();
		if ( controlPoint != null )
			controlPoint.removeControlPointListener( this );
	}
	
	private void commitPreset(int num)
	{	
		PlugPlayerControlPoint controlPoint = PlugPlayerControlPoint.getInstance();
		RecivaRenderer r = (RecivaRenderer)controlPoint.getCurrentRenderer();
		if(r == null){
			Log.w( "PresetActivity", "PresetActivity.commitPreset(): getCurrentRenderer returned null"); 
			return;
		}
		r.saveCurrentStationAsPreset(num+1);
		Preset updatedPreset = r.getPreset(num+1);
		listAdapter.remove( listAdapter.getItem(num));
		listAdapter.insert(updatedPreset, num);
	}
	
	public void onClick( View v )
	{
		final ImageButton button = (ImageButton)v;

        new AlertDialog.Builder(this)
        .setIcon(android.R.drawable.ic_dialog_alert)
        .setTitle("Are you sure?")
        .setMessage("Are you Sure you want to change this preset?")
        .setPositiveButton("Yes", new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
            	commitPreset( (Integer)button.getTag() );
            }
        })
        .setNegativeButton("No", null)
        .show();
	}
	
	private class PresetsListAdapter extends ArrayAdapter<Preset>
	{
		public PresetsListAdapter(Context context)
		{
			super(context,0);
			updateList();
		}
		
		public void updateList()
		{
			clear();
			
			PlugPlayerControlPoint controlPoint = PlugPlayerControlPoint.getInstance();
			RecivaRenderer r = (RecivaRenderer)controlPoint.getCurrentRenderer();
			
			if ( r != null )
			{
				List<Preset> presets = r.getPresets();
				for( Preset preset : presets )
					add( preset );
			}
		}
		
		@Override
		public View getView(final int position, View convertView, ViewGroup parent)
		{
			if ( convertView == null )
			{
				LayoutInflater vi = (LayoutInflater)getContext().getSystemService( Context.LAYOUT_INFLATER_SERVICE );
				convertView = vi.inflate( R.layout.preset_list_item, null );
			}
		
			Preset p = getItem( position );

			TextView t = (TextView)convertView.findViewById( R.id.name );

			if ( p != null)
				t.setText( "P" + (position + 1) + ": " + p.name );
			else
				t.setText( "P" + (position + 1) + ": " );
			
			ImageButton i = (ImageButton)convertView.findViewById( R.id.select );
			i.setTag( position );
			i.setVisibility( ImageButton.VISIBLE );

			convertView.setOnClickListener( new OnClickListener()
			{
				public void onClick( View v )
				{
					getListView().getOnItemClickListener().onItemClick( null, v, position, 0 );
				}
			} );

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
				listAdapter.updateList();
			}
		});
	}

	@Override
	public void mediaRendererListChanged()
	{
		Log.i(android.util.PlugPlayerUtil.DBG_TAG, "inside mediaRendererListChanged, no implementation:PresetsActivity");
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
