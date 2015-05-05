package com.plugplayer.plugplayer.activities;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Spinner;
import android.widget.TextView;

import com.plugplayer.plugplayer.R;
import com.plugplayer.plugplayer.upnp.LinnRenderer;
import com.plugplayer.plugplayer.upnp.PlugPlayerControlPoint;
import com.plugplayer.plugplayer.upnp.Renderer;

public class LinnActivity extends Activity
{
	CheckBox standby;
	Spinner sourceSpinner;
	Spinner songcastSpinner;
	TextView songcastLabel;

	@Override
	protected void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );
		setContentView( R.layout.linn );

		setTitle( R.string.linn_settings );

		standby = (CheckBox)this.findViewById( R.id.standby );
		standby.setOnCheckedChangeListener( new OnCheckedChangeListener()
		{
			public void onCheckedChanged( CompoundButton buttonView, final boolean isChecked )
			{
				Renderer r = PlugPlayerControlPoint.getInstance().getCurrentRenderer();
				if ( r instanceof LinnRenderer )
				{
					((LinnRenderer)r).setStandby( isChecked );
				}
			}
		} );

		sourceSpinner = (Spinner)this.findViewById( R.id.source );

		songcastLabel = (TextView)this.findViewById( R.id.songcast_label );
		songcastSpinner = (Spinner)this.findViewById( R.id.songcast );
	}

	public void updateState()
	{
		Renderer ro = PlugPlayerControlPoint.getInstance().getCurrentRenderer();
		if ( ro instanceof LinnRenderer )
		{
			final LinnRenderer r = (LinnRenderer)ro;
			standby.setChecked( r.getStandby() );

			final List<String> originalnames = r.getSourceNames();

			final ArrayList<String> names = new ArrayList<String>( originalnames );
			for ( Iterator<String> i = names.iterator(); i.hasNext(); )
			{
				String name = i.next();
				if ( "_hidden_".equals( name ) )
					i.remove();
			}

			int currentIndex = r.getSourceIndex();
			String currentSource = originalnames.get( currentIndex );
			int index = names.indexOf( currentSource );

			ArrayAdapter<String> adapter = new ArrayAdapter<String>( this, android.R.layout.simple_spinner_item, names );
			adapter.setDropDownViewResource( android.R.layout.simple_spinner_dropdown_item );
			sourceSpinner.setAdapter( adapter );
			sourceSpinner.setSelection( index );
			sourceSpinner.setOnItemSelectedListener( new OnItemSelectedListener()
			{
				public void onItemSelected( AdapterView<?> arg0, View arg1, int position, long arg3 )
				{
					String newSource = names.get( position );
					int newIndex = originalnames.indexOf( newSource );
					r.setSourceIndex( newIndex );
				}

				public void onNothingSelected( AdapterView<?> arg0 )
				{
				}
			} );

			if ( r.hasReceiverService() )
			{
				songcastLabel.setVisibility( View.VISIBLE );
				songcastSpinner.setVisibility( View.VISIBLE );

				final ArrayList<String> senders = new ArrayList<String>();
				for ( Renderer sender : PlugPlayerControlPoint.getInstance().getRendererList() )
				{
					if ( sender != r && sender.isAlive() && sender instanceof LinnRenderer && ((LinnRenderer)sender).hasSenderService() )
						senders.add( sender.getName() );
				}

				int senderIndex = -1;
				final Renderer currentSender = r.getSongcastSender();
				if ( currentSender != null )
					for ( int i = 0; i < senders.size(); ++i )
						if ( senders.get( i ).equals( currentSender.getName() ) )
							senderIndex = i;

				if ( senderIndex == -1 )
				{
					senderIndex = 0;
					senders.add( 0, getString( R.string.linn_no_sender ) );
				}

				ArrayAdapter<String> senderAdapter = new ArrayAdapter<String>( this, android.R.layout.simple_spinner_item, senders );
				senderAdapter.setDropDownViewResource( android.R.layout.simple_spinner_dropdown_item );
				songcastSpinner.setAdapter( senderAdapter );
				songcastSpinner.setSelection( senderIndex );
				songcastSpinner.setOnItemSelectedListener( new OnItemSelectedListener()
				{
					public void onItemSelected( AdapterView<?> arg0, View arg1, int position, long arg3 )
					{
						String newSender = senders.get( position );
						for ( Renderer sender : PlugPlayerControlPoint.getInstance().getRendererList() )
						{
							if ( currentSender != sender && newSender.equals( sender.getName() ) )
								r.setSongcastSender( sender );
						}
					}

					public void onNothingSelected( AdapterView<?> arg0 )
					{
					}
				} );
			}
			else
			{
				songcastLabel.setVisibility( View.GONE );
				songcastSpinner.setVisibility( View.GONE );
			}

		}
	}
}
