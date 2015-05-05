package com.plugplayer.recivaremote.activities;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.plugplayer.plugplayer.upnp.PlugPlayerControlPoint;
import com.plugplayer.plugplayer.upnp.RecivaRenderer;
import com.plugplayer.plugplayer.upnp.RecivaRenderer.Alarm;
import com.plugplayer.plugplayer.upnp.RecivaRenderer.Preset;
import com.plugplayer.recivaremote.R;

import android.app.Activity;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Spinner;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.TimePicker;

public class AlarmActivity extends Activity
{
	private Spinner number;
	private CheckBox enabled;
	private TextView time;
	private Spinner repeat;
	private Spinner day;
	private Spinner type;
	
	private List<String> reps;
	private List<String> types;
	private List<Preset> presets;
	
	private int pickHour;
	private int pickMinute;

	private Alarm currentAlarm;
	private int currentAlarmIndex;
		
	@Override
	public void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );
		setContentView( R.layout.alarm );
				
		RecivaRenderer r = (RecivaRenderer)PlugPlayerControlPoint.getInstance().getCurrentRenderer();
		int n = r.getNumberOfAlarms();

		Integer numbers[] = new Integer[n];
		for( int i = 0; i < n; ++i )
			numbers[i] = i+1;
		
		number = (Spinner)findViewById( R.id.number );
		ArrayAdapter<Integer> adapter = new ArrayAdapter<Integer>( this, android.R.layout.simple_spinner_item, numbers );
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		number.setAdapter( adapter );
		number.setOnItemSelectedListener( new OnItemSelectedListener()
		{
			public void onItemSelected( AdapterView<?> arg0, View arg1, int position, long arg3 )
			{
				updateAlarm( position );
			}

			public void onNothingSelected( AdapterView<?> arg0 )
			{
			}
		} );
		
		enabled = (CheckBox)findViewById( R.id.enabled );
		enabled.setOnCheckedChangeListener( new OnCheckedChangeListener()
		{
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
			{
				if ( currentAlarm == null )
				{
					Log.w( MainActivity.appName, "AlarmActivity.setOnCheckedChangeListener(): currentAlarm is null"); 
					return;
				}

				if ( currentAlarm.enabled == isChecked ) return;
				
				currentAlarm.enabled = isChecked;
				
	    		RecivaRenderer r = (RecivaRenderer)PlugPlayerControlPoint.getInstance().getCurrentRenderer();
				r.setAlarmEnabled( currentAlarmIndex, currentAlarm.enabled );
			}
		});
		
		time = (TextView)findViewById( R.id.time );
		time.setClickable( true );
		time.setOnClickListener( new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				if ( currentAlarm == null )
				{
					Log.w( MainActivity.appName, "AlarmActivity.onClick(): currentAlarm is null"); 
					return;
				}
				pickHour = currentAlarm.time.getHours();
				pickMinute = currentAlarm.time.getMinutes();
				showDialog( 0 );
			}
		});
		
		repeat = (Spinner)findViewById( R.id.repeat );
		reps = Arrays.asList( new String[] { "once (specified day)", "once (any day)", "always", "weekly", "weekdays", "weekends" } );
		ArrayAdapter<String> repeatAdapter = new ArrayAdapter<String>( this, android.R.layout.simple_spinner_item, reps );
		repeatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		repeat.setAdapter( repeatAdapter );
		repeat.setOnItemSelectedListener( new OnItemSelectedListener()
		{
			public void onItemSelected( AdapterView<?> arg0, View arg1, int position, long arg3 )
			{
				if ( currentAlarm == null )
				{
					Log.w( MainActivity.appName, "AlarmActivity.onItemSelected(): currentAlarm is null"); 
					return;
				}
				
				if ( currentAlarm.rep.equals( reps.get(position) ) ) return;

				currentAlarm.rep = reps.get(position);

	    		RecivaRenderer r = (RecivaRenderer)PlugPlayerControlPoint.getInstance().getCurrentRenderer();
	    		r.setAlarmRep( currentAlarmIndex, currentAlarm.rep );

				if ( position == 0 || position == 3 )
				{
					day.setSelection( currentAlarm.day );
					((TableRow)day.getParent()).setVisibility( View.VISIBLE );
				}
				else
				{
					((TableRow)day.getParent()).setVisibility( View.GONE );
				}
			}

			public void onNothingSelected( AdapterView<?> arg0 )
			{
			}
		} );		
		
		day = (Spinner)findViewById( R.id.day );
		ArrayAdapter<String> dayAdapter = new ArrayAdapter<String>( this, android.R.layout.simple_spinner_item, new String[] { "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday" } );
		dayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		day.setAdapter( dayAdapter );
		day.setOnItemSelectedListener( new OnItemSelectedListener()
		{
			public void onItemSelected( AdapterView<?> arg0, View arg1, int position, long arg3 )
			{
				if ( currentAlarm == null )
				{
					Log.w( MainActivity.appName, "AlarmActivity.onItemSelected(): currentAlarm is null"); 
					return;
				}
				
				if ( currentAlarm.day == position ) return;

				currentAlarm.day = position;
				
	    		RecivaRenderer r = (RecivaRenderer)PlugPlayerControlPoint.getInstance().getCurrentRenderer();
	    		r.setAlarmDay( currentAlarmIndex, currentAlarm.day );
			}

			public void onNothingSelected( AdapterView<?> arg0 )
			{
			}
		} );

		type = (Spinner)findViewById( R.id.type );
		ArrayAdapter<String> typeAdapter = new ArrayAdapter<String>( this, android.R.layout.simple_spinner_item, Collections.EMPTY_LIST );
		typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		type.setAdapter( typeAdapter );
		type.setOnItemSelectedListener( new OnItemSelectedListener()
		{
			public void onItemSelected( AdapterView<?> arg0, View arg1, int position, long arg3 )
			{
				if ( currentAlarm == null )
				{
					Log.w( MainActivity.appName, "AlarmActivity.onItemSelected(): currentAlarm is null"); 
					return;
				}
				
				if ( currentAlarm.type.equals( types.get(position) ) ) return;

				currentAlarm.type = types.get(position);
				
	    		RecivaRenderer r = (RecivaRenderer)PlugPlayerControlPoint.getInstance().getCurrentRenderer();
	    		r.setAlarmType( currentAlarmIndex, currentAlarm.type );
	    		
	    		updateAlarm( position );
			}

			public void onNothingSelected( AdapterView<?> arg0 )
			{
			}
		} );
		
		Spinner preset = (Spinner)findViewById( R.id.preset );
		ArrayAdapter<String> presetAdapter = new ArrayAdapter<String>( this, android.R.layout.simple_spinner_item, Collections.EMPTY_LIST );
		presetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		preset.setAdapter( presetAdapter );
		preset.setOnItemSelectedListener( new OnItemSelectedListener()
		{
			public void onItemSelected( AdapterView<?> arg0, View arg1, int position, long arg3 )
			{
				if ( currentAlarm == null )
				{
					Log.w( MainActivity.appName, "AlarmActivity.onItemSelected(): currentAlarm is null"); 
					return;
				}
				
				Preset preset = presets.get(position);

				if ( currentAlarm.name.equals( preset.name ) ) return;
				currentAlarm.name = preset.name;

	    		RecivaRenderer r = (RecivaRenderer)PlugPlayerControlPoint.getInstance().getCurrentRenderer();
	    		r.setAlarmStationPreset( currentAlarmIndex, position + 1 );
			}

			public void onNothingSelected( AdapterView<?> arg0 )
			{
			}
		} );
		
		number.setSelection( 0 );
	}
	
	private void updateAlarm( int alarmIndex )
	{
		currentAlarmIndex = alarmIndex;
		RecivaRenderer r = (RecivaRenderer)PlugPlayerControlPoint.getInstance().getCurrentRenderer();
		if(r == null){
			Log.w( MainActivity.appName, "AlarmActivity.updateAlarm(): getCurrentRenderer returned null"); 
			return;
		}
		currentAlarm = r.getAlarm( alarmIndex );
		if ( currentAlarm == null )
		{
			Log.w( MainActivity.appName, "AlarmActivity.updateAlarm(): r.getAlarm(" + alarmIndex + ") returned null"); 
			return;
		}
		
		enabled.setChecked( currentAlarm.enabled );
		time.setText( new SimpleDateFormat( "HH:mm" ).format( currentAlarm.time ) );
		int repIndex = reps.indexOf( currentAlarm.rep );
		repeat.setSelection( repIndex );
		if ( repIndex == 0 || repIndex == 3 )
		{
			day.setSelection( currentAlarm.day );
			((TableRow)day.getParent()).setVisibility( View.VISIBLE );
		}
		else
		{
			((TableRow)day.getParent()).setVisibility( View.GONE );
		}
		
		types = r.getAlarmTypes();
		presets = r.getPresets();
		
		ArrayAdapter<String> typeAdapter = new ArrayAdapter<String>( this, android.R.layout.simple_spinner_item, types );
		type.setAdapter( typeAdapter );		
		type.setSelection( types.indexOf( currentAlarm.type ) );

		int presetIndex = 0;
		List<String> presetNames = new ArrayList<String>();
		for( int i = 0; i < presets.size(); ++i )
		{
			String name = presets.get(i).name;
			
			if ( name == null )
				name = "";
			presetNames.add( name );
			if ( name.equals( currentAlarm.name ) )
				presetIndex = i;
		}
		Spinner preset = (Spinner)findViewById( R.id.preset );
		ArrayAdapter<String> presetAdapter = new ArrayAdapter<String>( this, android.R.layout.simple_spinner_item, presetNames );
		preset.setAdapter( presetAdapter );
		preset.setSelection( presetIndex );

		View row = findViewById( R.id.presetrow );
		if ( currentAlarm.type.equals("Internet radio station") )
			row.setVisibility( View.VISIBLE );
		else
			row.setVisibility( View.GONE );
	}
		
	private TimePickerDialog.OnTimeSetListener mTimeSetListener = new TimePickerDialog.OnTimeSetListener()
	{
        public void onTimeSet(TimePicker view, int hourOfDay, int minute)
        {
        	currentAlarm.time.setHours( hourOfDay );
        	currentAlarm.time.setMinutes( minute );
        	
    		String timeStr = new SimpleDateFormat( "HH:mm:ss" ).format( currentAlarm.time );
    		time.setText( timeStr );
    		
    		RecivaRenderer r = (RecivaRenderer)PlugPlayerControlPoint.getInstance().getCurrentRenderer();
    		r.setAlarmTime( currentAlarmIndex, currentAlarm.time );
        }
	};
	    
	@Override
	protected Dialog onCreateDialog(int id)
	{
	    switch (id)
	    {
	    	case 0:
	    		return new TimePickerDialog(this, mTimeSetListener, pickHour, pickMinute, true);
	    }
	    
	    return null;
	}
}
