package com.plugplayer.plugplayer.activities;

import org.cybergarage.http.HTTPServer;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import com.plugplayer.plugplayer.R;

public class SettingsEditor extends Activity
{
	public static final String ADVANCED_SETTINGS = "advancedsettings";
	public static final String INTERNAL_VIDEO = "internalvideo";
	public static final String INTERNAL_AUDIO = "internalaudio";
	public static final String INTERNAL_IMAGE = "internalimage";
	public static final String TAP_TO_PLAY = "taptoplay";
	public static final String DEVICE_DISCOVERY = "devicediscovery";
	public static final String IGNORE_STOP = "ignorestop";
	public static final String IGNORE_PLAY = "ignoreplay";
	public static final String ART_PLAYLIST = "artplaylist2";
	public static final String ART_BROWSE = "artbrowse2";
	public static final String SOAP_TIMEOUT = "soaptimeout";
	public static final String SCROLLING_LABELS = "scrollinglabels";
	public static final String INDEX_THRESHOLD = "indexthreshold";

	public static void initSettings()
	{
		final SharedPreferences preferences = MainActivity.me.getSharedPreferences( SettingsEditor.ADVANCED_SETTINGS, Activity.MODE_PRIVATE );

		boolean internalVideo = preferences.getBoolean( INTERNAL_VIDEO, true );
		preferences.edit().putBoolean( INTERNAL_VIDEO, internalVideo ).commit();

		boolean internalAudio = preferences.getBoolean( INTERNAL_AUDIO, true );
		preferences.edit().putBoolean( INTERNAL_AUDIO, internalAudio ).commit();

		boolean internalImage = preferences.getBoolean( INTERNAL_IMAGE, true );
		preferences.edit().putBoolean( INTERNAL_IMAGE, internalImage ).commit();

		boolean tapToPlay = preferences.getBoolean( TAP_TO_PLAY, true );
		preferences.edit().putBoolean( TAP_TO_PLAY, tapToPlay ).commit();

		boolean deviceDiscovery = preferences.getBoolean( DEVICE_DISCOVERY, true );
		preferences.edit().putBoolean( DEVICE_DISCOVERY, deviceDiscovery ).commit();

		boolean ignoreStop = preferences.getBoolean( IGNORE_STOP, false );
		preferences.edit().putBoolean( IGNORE_STOP, ignoreStop ).commit();

		boolean ignorePlay = preferences.getBoolean( IGNORE_PLAY, false );
		preferences.edit().putBoolean( IGNORE_PLAY, ignorePlay ).commit();

		boolean artplaylist = preferences.getBoolean( ART_PLAYLIST, true );
		preferences.edit().putBoolean( ART_PLAYLIST, artplaylist ).commit();

		boolean artbrowse = preferences.getBoolean( ART_BROWSE, true );
		preferences.edit().putBoolean( ART_BROWSE, artbrowse ).commit();

		int soapTimeout = preferences.getInt( SOAP_TIMEOUT, HTTPServer.DEFAULT_TIMEOUT / 1000 );
		HTTPServer.DEFAULT_TIMEOUT = soapTimeout * 1000;
		preferences.edit().putInt( SOAP_TIMEOUT, soapTimeout ).commit();

		boolean scrollingLabels = preferences.getBoolean( SCROLLING_LABELS, true );
		preferences.edit().putBoolean( SCROLLING_LABELS, scrollingLabels ).commit();

		int indexThreshold = preferences.getInt( INDEX_THRESHOLD, 40 );
		preferences.edit().putInt( INDEX_THRESHOLD, indexThreshold ).commit();
	}

	@Override
	protected void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );
		setContentView( R.layout.settingseditor );

		setTitle( R.string.advanced_settings );

		final SharedPreferences preferences = getSharedPreferences( SettingsEditor.ADVANCED_SETTINGS, Activity.MODE_PRIVATE );

		final CheckBox audio = (CheckBox)findViewById( R.id.audio );
		audio.setChecked( preferences.getBoolean( INTERNAL_AUDIO, true ) );

		final CheckBox video = (CheckBox)findViewById( R.id.video );
		video.setChecked( preferences.getBoolean( INTERNAL_VIDEO, true ) );

		final CheckBox image = (CheckBox)findViewById( R.id.image );
		image.setChecked( preferences.getBoolean( INTERNAL_IMAGE, true ) );

		final CheckBox tap = (CheckBox)findViewById( R.id.tap );
		tap.setChecked( preferences.getBoolean( TAP_TO_PLAY, true ) );

		final CheckBox discovery = (CheckBox)findViewById( R.id.discovery );
		discovery.setChecked( preferences.getBoolean( DEVICE_DISCOVERY, true ) );

		final CheckBox ignorestop = (CheckBox)findViewById( R.id.ignorestop );
		ignorestop.setChecked( preferences.getBoolean( IGNORE_STOP, false ) );

		final CheckBox ignoreplay = (CheckBox)findViewById( R.id.ignoreplay );
		ignoreplay.setChecked( preferences.getBoolean( IGNORE_PLAY, false ) );

		final CheckBox artplaylist = (CheckBox)findViewById( R.id.artplaylist );
		artplaylist.setChecked( preferences.getBoolean( ART_PLAYLIST, true ) );

		final CheckBox artbrowse = (CheckBox)findViewById( R.id.artbrowse );
		artbrowse.setChecked( preferences.getBoolean( ART_BROWSE, true ) );

		final EditText timeout = (EditText)findViewById( R.id.timeout );
		timeout.setText( "" + preferences.getInt( SOAP_TIMEOUT, HTTPServer.DEFAULT_TIMEOUT / 1000 ) );

		final CheckBox scrollinglabels = (CheckBox)findViewById( R.id.scrollinglabels );
		scrollinglabels.setChecked( preferences.getBoolean( SCROLLING_LABELS, true ) );

		final EditText indexthreshold = (EditText)findViewById( R.id.indexthreshold );
		indexthreshold.setText( "" + preferences.getInt( INDEX_THRESHOLD, 40 ) );

		Button save = (Button)findViewById( R.id.save );
		save.setOnClickListener( new OnClickListener()
		{
			public void onClick( View v )
			{
				int soapTimeout = 0;
				try
				{
					soapTimeout = Integer.parseInt( timeout.getText().toString() );
					if ( soapTimeout < 1 )
					{
						Toast.makeText( SettingsEditor.this, R.string.timeout_one, Toast.LENGTH_LONG ).show();
						return;
					}
					HTTPServer.DEFAULT_TIMEOUT = soapTimeout * 1000;
					preferences.edit().putInt( SOAP_TIMEOUT, soapTimeout ).commit();
				}
				catch ( Exception e )
				{
					Toast.makeText( SettingsEditor.this, R.string.timeout_integer, Toast.LENGTH_LONG ).show();
					return;
				}

				int indexThreshold = 0;
				try
				{
					indexThreshold = Integer.parseInt( indexthreshold.getText().toString() );
					if ( soapTimeout < 1 )
					{
						Toast.makeText( SettingsEditor.this, R.string.index_threshold_nonzerointeger, Toast.LENGTH_LONG ).show();
						return;
					}
					preferences.edit().putInt( INDEX_THRESHOLD, indexThreshold ).commit();
				}
				catch ( Exception e )
				{
					Toast.makeText( SettingsEditor.this, R.string.timeout_integer, Toast.LENGTH_LONG ).show();
					return;
				}

				preferences.edit().putBoolean( INTERNAL_VIDEO, video.isChecked() ).commit();
				preferences.edit().putBoolean( INTERNAL_AUDIO, audio.isChecked() ).commit();
				preferences.edit().putBoolean( INTERNAL_IMAGE, image.isChecked() ).commit();
				preferences.edit().putBoolean( TAP_TO_PLAY, tap.isChecked() ).commit();
				preferences.edit().putBoolean( DEVICE_DISCOVERY, discovery.isChecked() ).commit();
				preferences.edit().putBoolean( IGNORE_STOP, ignorestop.isChecked() ).commit();
				preferences.edit().putBoolean( ART_PLAYLIST, artplaylist.isChecked() ).commit();
				preferences.edit().putBoolean( ART_BROWSE, artbrowse.isChecked() ).commit();
				preferences.edit().putBoolean( SCROLLING_LABELS, scrollinglabels.isChecked() ).commit();
				Toast.makeText( SettingsEditor.this, R.string.take_effect, Toast.LENGTH_LONG ).show();
				finish();
			}
		} );
	}
}
