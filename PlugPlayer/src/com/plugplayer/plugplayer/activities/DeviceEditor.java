package com.plugplayer.plugplayer.activities;

import org.cybergarage.upnp.Device;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import com.plugplayer.plugplayer.R;
import com.plugplayer.plugplayer.upnp.AndroidRenderer;
import com.plugplayer.plugplayer.upnp.AndroidServer;
import com.plugplayer.plugplayer.upnp.MP3TunesServer;
import com.plugplayer.plugplayer.upnp.MediaDevice;
import com.plugplayer.plugplayer.upnp.PlugPlayerControlPoint;

public class DeviceEditor extends Activity
{
	public static MediaDevice editDevice = null;

	@Override
	protected void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );
		setContentView( R.layout.deviceeditor );

		if ( editDevice == null )
			finish();

		setTitle( R.string.edit_device );

		final EditText name = (EditText)findViewById( R.id.name );
		name.setText( editDevice.getName() );

		final EditText location = (EditText)findViewById( R.id.location );
		location.setText( editDevice.getLocation() );

		final EditText baseURL = (EditText)findViewById( R.id.baseURL );
		baseURL.setText( editDevice.getBaseOverride() );

		final EditText timeout = (EditText)findViewById( R.id.timeout );
		timeout.setText( "" + editDevice.getSearchTimeout() );

		final CheckBox control = (CheckBox)findViewById( R.id.control );

		final EditText username = (EditText)findViewById( R.id.username );
		final EditText password = (EditText)findViewById( R.id.password );

		if ( editDevice instanceof AndroidRenderer || editDevice instanceof AndroidServer )
		{
			location.setKeyListener( null );
			((View)baseURL.getParent()).setVisibility( View.GONE );
			((View)timeout.getParent()).setVisibility( View.GONE );

			((View)username.getParent()).setVisibility( View.GONE );
			((View)password.getParent()).setVisibility( View.GONE );

			if ( editDevice instanceof AndroidRenderer )
				control.setChecked( ((AndroidRenderer)editDevice).getAllowControl() );
			else
				control.setChecked( ((AndroidServer)editDevice).getAllowControl() );
		}
		else if ( editDevice instanceof MP3TunesServer )
		{
			((View)name.getParent()).setVisibility( View.GONE );
			((View)location.getParent()).setVisibility( View.GONE );
			((View)baseURL.getParent()).setVisibility( View.GONE );
			((View)timeout.getParent()).setVisibility( View.GONE );
			((View)control.getParent()).setVisibility( View.GONE );

			username.setText( ((MP3TunesServer)editDevice).getUsername() );
			password.setText( ((MP3TunesServer)editDevice).getPassword() );
		}
		else
		{
			((View)control.getParent()).setVisibility( View.GONE );

			((View)username.getParent()).setVisibility( View.GONE );
			((View)password.getParent()).setVisibility( View.GONE );
		}

		Button save = (Button)findViewById( R.id.save );
		save.setOnClickListener( new OnClickListener()
		{
			public void onClick( View v )
			{
				if ( editDevice instanceof MP3TunesServer )
				{
					String newUsername = null;
					if ( username.getText() != null )
						newUsername = username.getText().toString();

					if ( newUsername == null || newUsername.length() == 0 )
					{
						Toast.makeText( DeviceEditor.this, getResources().getText( R.string.blank_name ), Toast.LENGTH_LONG ).show();
						return;
					}

					String newPassword = null;
					if ( password.getText() != null )
						newPassword = password.getText().toString();

					if ( newPassword == null || newPassword.length() == 0 )
					{
						Toast.makeText( DeviceEditor.this, getResources().getText( R.string.blank_name ), Toast.LENGTH_LONG ).show();
						return;
					}

					((MP3TunesServer)editDevice).setUsername( newUsername );
					if ( ((MP3TunesServer)editDevice).setPassword( newPassword ) )
					{
						Toast.makeText( DeviceEditor.this, R.string.mp3tunes_success, Toast.LENGTH_LONG ).show();
					}
					else
					{
						Toast.makeText( DeviceEditor.this, R.string.mp3tunes_fail, Toast.LENGTH_LONG ).show();
						return;
					}
				}
				else
				{
					String newName = null;
					if ( name.getText() != null )
						newName = name.getText().toString();

					if ( newName == null || newName.length() == 0 )
					{
						Toast.makeText( DeviceEditor.this, getResources().getText( R.string.blank_name ), Toast.LENGTH_LONG ).show();
						return;
					}

					String newLocation = null;
					if ( location.getText() != null )
						newLocation = location.getText().toString();

					if ( !(editDevice instanceof AndroidRenderer) && !(editDevice instanceof AndroidServer)
							&& (newLocation == null || newLocation.length() == 0) )
					{
						Toast.makeText( DeviceEditor.this, getResources().getText( R.string.blank_url ), Toast.LENGTH_LONG ).show();
						return;
					}

					String newBaseURL = "";
					if ( baseURL.getText() != null && baseURL.getText().length() != 0 )
					{
						newBaseURL = baseURL.getText().toString();
						if ( newBaseURL.endsWith( "/" ) )
							newBaseURL.substring( 0, newBaseURL.length() - 1 );
					}

					int searchTimeout = 0;
					try
					{
						searchTimeout = Integer.parseInt( timeout.getText().toString() );
						// old code - now we allow tiemout = 0 (means assume alive)
						// if ( !(editDevice instanceof AndroidRenderer) && searchTimeout < 1 )
						// {
						// Toast.makeText( DeviceEditor.this, R.string.timeout_one, Toast.LENGTH_LONG ).show();
						// return;
						// }
					}
					catch ( Exception e )
					{
						Toast.makeText( DeviceEditor.this, R.string.timeout_integer, Toast.LENGTH_LONG ).show();
						return;
					}

					if ( !(editDevice instanceof AndroidRenderer) && !(editDevice instanceof AndroidServer)
							&& (!editDevice.getLocation().equals( newLocation ) || !newBaseURL.equals( editDevice.getBaseOverride() )) )
					{
						String testURL = MediaDevice.overrideBase( newBaseURL, newLocation );

						Device dev = PlugPlayerControlPoint.deviceFromLocation( testURL, searchTimeout );
						if ( dev == null )
						{
							Toast.makeText( DeviceEditor.this, R.string.access_device, Toast.LENGTH_LONG ).show();
							return;
						}

						editDevice.updateServices( dev );
					}

					editDevice.setNameOverride( newName );

					if ( !(editDevice instanceof AndroidRenderer) && !(editDevice instanceof AndroidServer) )
					{
						editDevice.setLocationOverride( newLocation );
						editDevice.setBaseOverride( newBaseURL );
						editDevice.setSearchTimeout( searchTimeout );
					}
					else
					{
						if ( editDevice instanceof AndroidRenderer )
							((AndroidRenderer)editDevice).setAllowControl( control.isChecked() );
						else
							((AndroidServer)editDevice).setAllowControl( control.isChecked() );
					}

					MainActivity.me.getControlPoint().deviceUpdated( editDevice );
				}

				finish();
			}
		} );
	}

	@Override
	public boolean onSearchRequested()
	{
		return false;
	}

	@Override
	protected void onDestroy()
	{
		if ( isFinishing() )
			editDevice = null;

		super.onDestroy();
	}
}
