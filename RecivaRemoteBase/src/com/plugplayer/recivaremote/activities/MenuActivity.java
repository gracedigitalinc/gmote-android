package com.plugplayer.recivaremote.activities;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

//import com.plugplayer.plugplayer.activities.DeviceEditor;
import com.plugplayer.plugplayer.activities.MainActivity;
import com.plugplayer.plugplayer.upnp.PlugPlayerControlPoint;
import com.plugplayer.plugplayer.upnp.RecivaRenderer;
import com.plugplayer.plugplayer.upnp.RecivaRenderer.MenuEntry;
import com.plugplayer.plugplayer.upnp.Renderer;
//import com.plugplayer.plugplayer.upnp.RecivaRenderer.MenuListener;
import com.plugplayer.plugplayer.upnp.RecivaRenderer.MenuEntry.MenuType;
import com.plugplayer.recivaremote.R;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
//import android.app.Dialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.TextView.OnEditorActionListener;

public class MenuActivity extends ListActivity 
{
	private final Handler handler = new Handler();

	MenuEntry parentEntry;
	MenuEntryAdapter adapter;
	ProgressBar progressBar;
	TextView title;
	boolean loadingError;
	
	public void onBack()
	{
	}
	
	@Override
	public void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );
		setContentView( R.layout.menu );

		progressBar = (ProgressBar)findViewById( R.id.progress );
		progressBar.setIndeterminate( true );
		progressBar.setVisibility( View.VISIBLE );

		title = (TextView)findViewById( R.id.title );

		parentEntry = MenuActivityGroup.group.topParentEntry;
		title.setText( parentEntry.title );

		adapter = new MenuEntryAdapter( this );
		
		RecivaRenderer r = (RecivaRenderer)PlugPlayerControlPoint.getInstance().getCurrentRenderer();
	    loadingError = false;		
		if (r != null) {
			if ( MenuActivityGroup.group.searchText != null )
				r.enterText( MenuActivityGroup.group.searchText, parentEntry.uuid, new ArrayList<MenuEntry>(), adapter );
			else
			{
				boolean returnValue = r.getMenu( parentEntry.number, new ArrayList<MenuEntry>(), null, adapter );
				if(returnValue == false && r.isPowerOn()) {
				final AlertDialog.Builder alert = new AlertDialog.Builder((Context) MainActivity.me);
			    alert.setTitle( "Unable to load selected item!" );
				progressBar.setVisibility( View.INVISIBLE );			    
				alert.setPositiveButton("Ok",new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog,int which) 
                    {
                	    loadingError = false;	                    	
        			    onBackPressed(true);
                    }
                });
			    alert.setMessage(parentEntry.title);
			    alert.setCancelable(false);
			    alert.show();

			    loadingError = false;
			    return;
				}
			}
		}
		
		MainActivity.me.getWindow().setFeatureInt( Window.FEATURE_PROGRESS, 10000 );
		setListAdapter( adapter );

		final ListView lv = getListView();
		lv.setTextFilterEnabled( false );

		lv.setOnItemClickListener( new OnItemClickListener()
		{
			public void onItemClick( AdapterView<?> parent, View view, int position, long id )
			{
				final MenuEntry currentEntry = (MenuEntry)lv.getItemAtPosition( position );

				if ( currentEntry.type == MenuType.Unknown )
				{
					onBackPressed();
				}
				else if ( "Sleep Timer".equals(currentEntry.title) )
				{
					showDialog(0);
				}
				else if ( currentEntry.type == MenuType.Station || currentEntry.type == MenuType.Function || currentEntry.type == MenuType.EnqueueMedia || currentEntry.type == MenuType.Script)
				{
					((RecivaRenderer)PlugPlayerControlPoint.getInstance().getCurrentRenderer()).getMenu( currentEntry.number, null, currentEntry );
					
					MainActivity.me.setTab( 0 );
				}
				else if ( ( "Sign in".equals(currentEntry.title) || ("Change Username".equals(currentEntry.title) ) && currentEntry.type == MenuType.TextInput ) )
				{
					final AlertDialog.Builder alert = new AlertDialog.Builder((Context) MainActivity.me);
				    alert.setTitle( currentEntry.title );
					alert.setPositiveButton("Ok", null );
				    alert.setMessage("Please " + currentEntry.title.toLowerCase() + " directly on your radio.");
				    alert.setCancelable(false);
				    alert.show();
				}
				else if ( currentEntry.type == MenuType.TextInput || currentEntry.type == MenuType.Search )
				{
					boolean returnValue = ((RecivaRenderer)PlugPlayerControlPoint.getInstance().getCurrentRenderer()).getMenu( currentEntry.number, null, currentEntry );
					final AlertDialog.Builder alert = new AlertDialog.Builder((Context) MainActivity.me);
				    alert.setTitle( currentEntry.prompt );
				    final EditText textInput = new EditText((Context) MainActivity.me);
				    textInput.setMaxLines( 1 );
				    alert.setView(textInput);
				    final DialogInterface.OnClickListener clickListener = 
				    new DialogInterface.OnClickListener()
				    {
				        public void onClick(DialogInterface dialog, int whichButton)
				        {
				        	String text = textInput.getText().toString();
				        	MenuActivityGroup.pushContainer( MenuActivity.this, currentEntry, text );
				        }
				    };
					alert.setPositiveButton("Ok", clickListener );
				    final AlertDialog alertDialog = alert.show();
				    textInput.setOnEditorActionListener( new OnEditorActionListener()
				    {
						@Override
						public boolean onEditorAction(TextView v, int actionId, KeyEvent event)
						{
							if ( event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER )
							{
								clickListener.onClick( alertDialog, AlertDialog.BUTTON_POSITIVE );
								alertDialog.dismiss();
								return true;
							}
							
							return false;
						}
					});

				}
				else if ( "Alarm Clock".equals(currentEntry.title) || "Set Alarms".equals(currentEntry.title))
				{
					Intent intent = new Intent( MenuActivity.this, AlarmActivity.class );
					startActivity( intent );
				}
				else if ( "Media Player".equals(currentEntry.title) || "MediaPlayer".equals(currentEntry.title) )
				{
					MainActivity.me.setTab( 2 );
				}
				else
				{
					MenuActivityGroup.pushContainer( MenuActivity.this, currentEntry );
					adapter.stopLoading();
				}
			}
		} );
	}
	
	@Override
	public void onBackPressed()
	{
		adapter.stopLoading();
		
		MenuActivityGroup.group.back();
	}

	public void onBackPressed(boolean error)
	{
		adapter.stopLoading();
		
		MenuActivityGroup.group.back(error);
	}
		
	private class MenuEntryAdapter extends ArrayAdapter<MenuEntry> implements RecivaRenderer.MenuListener
	{
		private boolean keepLoading = true;
		
		public MenuEntryAdapter(Context context)
		{
			super(context, 0);
		}

		@Override
		public View getView( final int position, View convertView, ViewGroup parent )
		{
			MenuEntry entry = getItem( position );

			if ( convertView == null )
			{
				LayoutInflater vi = (LayoutInflater)getSystemService( Context.LAYOUT_INFLATER_SERVICE );

				if ( entry.type == MenuType.Menu || entry.type == MenuType.Deferred )
					convertView = vi.inflate( R.layout.menucontainer_list_item, null );
				else
					convertView = vi.inflate( R.layout.menuitem_list_item, null );
			}
			
			TextView t = (TextView)convertView.findViewById( R.id.title );
			t.setText( entry.title );

			return convertView;
		}
		
		@Override
		public int getItemViewType( int position )
		{
			MenuEntry entry = getItem( position );

			if ( entry.type == MenuType.Menu || entry.type == MenuType.Deferred )
				return 0;
			else
				return 1;
		}
		
		@Override
		public int getViewTypeCount()
		{
			return 2;
		}
		
		public void stopLoading()
		{
			keepLoading = false;
		}
		
		@Override
		public boolean isStillLoading()
		{
			return keepLoading;
		}

		@Override
		public void addMenuEntries( final int total, final int count, final List<MenuEntry> menu ) 
		{			
			if ( (menu == null) || (menu.size() == 0)) return;
			
			handler.post( new Runnable()
			{
				@Override
				public void run()
				{
					for( MenuEntry entry : menu )
						if ( entry.title.equals( "Settings" ) );
						else if ( parentEntry.title.equals( "Menu" ) && entry.title.equals( "WeatherBug" ) );
						else if ( parentEntry.title.equals( "Internet Radio" ) && entry.title.equals( "Weather" ) );
						else
							add( entry );
					
					if ( total == count )
					{
						progressBar.setVisibility( View.INVISIBLE );
					}
					else
					{
						progressBar.setVisibility( View.VISIBLE );
						progressBar.setIndeterminate( false );
						progressBar.setMax( total );
						progressBar.setProgress( getCount() );
					}

					progressBar.invalidate();
				}
			});
		}

		@Override
		public void updateProgress(float f)
		{
		}		
	}

	private TimePickerDialog.OnTimeSetListener mTimeSetListener = new TimePickerDialog.OnTimeSetListener()
	{
        public void onTimeSet(TimePicker view, int hourOfDay, int minute)
        {
    		RecivaRenderer r = (RecivaRenderer)PlugPlayerControlPoint.getInstance().getCurrentRenderer();
    		r.setSleepTime(hourOfDay*3600 + minute*60);
        }
	};
	
	@Override
	protected void onPrepareDialog(int id, Dialog dialog)
	{
	    switch (id)
	    {
	    	case 0:
	    		RecivaRenderer r = (RecivaRenderer)PlugPlayerControlPoint.getInstance().getCurrentRenderer();
	    		int time = r.getSleepTimeRemaining(); 
	    		SimpleDateFormat format = new SimpleDateFormat( "HH:mm" );
	    		format.setTimeZone(TimeZone.getTimeZone("UTC"));
	    		String timeStr = (time > 0) ? format.format( (time+60)*1000 ) : getString(R.string.sleep_timer_off);
	    		dialog.setTitle(getString(R.string.set_sleep_timer, timeStr));
	    }
	}
	
	@Override	    
	protected void onResume()
	{
		super.onResume();
		System.out.println("OnResume called");
	}
	@Override
	protected Dialog onCreateDialog(int id)
	{
	    switch (id)
	    {
	    	case 0:
	    		AlertDialog d = new TimePickerDialog(getParent(), mTimeSetListener, 0, 0, true);
	    		d.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.turn_sleep_timer_off), new DialogInterface.OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
			    		RecivaRenderer r = (RecivaRenderer)PlugPlayerControlPoint.getInstance().getCurrentRenderer();
			    		r.setSleepTime(-1);
					}
				});
	    		return d;
	    }
	    
	    return null;
	}
}
