package com.plugplayer.plugplayer.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.VideoView;

import com.plugplayer.plugplayer.R;
import com.plugplayer.plugplayer.upnp.AndroidRenderer;
import com.plugplayer.plugplayer.upnp.Renderer;

public class VideoViewer extends Activity implements OnPreparedListener
{
	private static VideoViewer me = null;
	private static OnClickListener prevListener = null;
	private static OnClickListener nextListener = null;
	private static String resourceURI = null;
	private static boolean playing = false;
	private static OnCompletionListener onCompletionListener = null;
	private static OnErrorListener onErrorListener = null;
	private static boolean shown = false;

	public static void show()
	{
		if ( !shown )
		{
			shown = true;
			Intent intent = new Intent( (Context)MainActivity.me, MainActivity.me.getVideoViewerClass() );
			intent.addFlags( Intent.FLAG_ACTIVITY_NEW_TASK );
			MainActivity.me.startActivity( intent );
		}
	}

	public static void hide()
	{
		if ( me != null )
			me.finish();
		// me.moveTaskToBack( true );
	}

	public static boolean isShown()
	{
		return shown;
	}

	public static boolean isPlaying()
	{
		try
		{
			return me != null && me.videoView.isPlaying();
		}
		catch ( Exception e )
		{
			return false;
		}
	}

	public static int getCurrentSeconds()
	{
		if ( me != null )
			return (int)(me.videoView.getCurrentPosition() / 1000.0);
		else
			return -1;
	}

	public static int getTotalSeconds()
	{
		if ( me != null )
			return (int)(me.videoView.getDuration() / 1000.0);
		else
			return -1;
	}

	public static void seekTo( final int second )
	{
		if ( me != null )
			me.runOnUiThread( new Runnable()
			{
				public void run()
				{
					me.videoView.seekTo( second * 1000 );
				}
			} );
	}

	public static void play()
	{
		playing = true;

		if ( me != null )
			me.runOnUiThread( new Runnable()
			{
				public void run()
				{
					me.videoView.start();
				}
			} );
	}

	public static void pause()
	{
		playing = false;

		if ( me != null )
			me.runOnUiThread( new Runnable()
			{
				public void run()
				{
					me.videoView.pause();
				}
			} );
	}

	public static void setNextPrev( OnClickListener prev, OnClickListener next )
	{
		prevListener = prev;
		nextListener = next;

		if ( me != null )
		{
			me.mediaController.setPrevNextListeners( prevListener, nextListener );
		}
	}

	public static void setOnCompletionListener( OnCompletionListener listener )
	{
		onCompletionListener = listener;

		if ( me != null )
			me.videoView.setOnCompletionListener( listener );
	}

	public static void setOnErrorListener( OnErrorListener listener )
	{
		onErrorListener = listener;

		if ( me != null )
			me.videoView.setOnErrorListener( listener );
	}

	public static void setURI( String resourceURIIn )
	{
		resourceURI = resourceURIIn;

		if ( me != null && resourceURI != null )
		{
			me.runOnUiThread( new Runnable()
			{
				public void run()
				{
					me.progress.setVisibility( View.VISIBLE );
					me.videoView.setVideoURI( Uri.parse( resourceURI ) );
				}
			} );
		}
	}

	public void onPrepared( MediaPlayer arg0 )
	{
		progress.setVisibility( View.GONE );
	}

	@Override
	public void onBackPressed()
	{
		// Not clear why it was crashing here without this check; perhaps the control point went away because network dropped? or app was closed?
		if ( MainActivity.me != null && MainActivity.me.getControlPoint() != null )
		{
			Renderer r = MainActivity.me.getControlPoint().getCurrentRenderer();
			if ( r instanceof AndroidRenderer )
				((AndroidRenderer)r).stop();
		}

		super.onBackPressed();
	}

	boolean wasPlaying = false;
	int playLocation = 0;

	@Override
	protected void onPause()
	{
		super.onPause();
		wasPlaying = videoView.isPlaying();
		if ( wasPlaying )
			playLocation = getCurrentSeconds();
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		if ( wasPlaying )
		{
			seekTo( playLocation );
			play();
		}
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		getWindow().setFlags( WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN );
		me = null;
		resourceURI = null;
		prevListener = null;
		nextListener = null;
		playing = false;
		onCompletionListener = null;
		onErrorListener = null;
		shown = false;
	}

	private ProgressBar progress;
	private VideoView videoView;
	private MediaController mediaController;

	@Override
	public void onCreate( final Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );

		setContentView( R.layout.video );

		getWindow().setFlags( WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN );

		progress = (ProgressBar)findViewById( R.id.progress );

		videoView = (VideoView)findViewById( R.id.videoView );
		mediaController = new MediaController( this )
		{
			long lastChange = 0;

			@Override
			public void show( int timeout )
			{
				long currentTime = System.currentTimeMillis();

				if ( currentTime - lastChange > 500 )
					super.show( timeout );

				lastChange = currentTime;
			}

			@Override
			public void hide()
			{
				long currentTime = System.currentTimeMillis();

				if ( currentTime - lastChange > 500 )
					super.hide();

				lastChange = currentTime;
			};

		};
		videoView.setMediaController( mediaController );
		videoView.setOnPreparedListener( this );

		me = this;

		setNextPrev( prevListener, nextListener );
		setOnCompletionListener( onCompletionListener );
		setOnErrorListener( onErrorListener );
		setURI( resourceURI );

		if ( playing )
			play();
	}

	public void videobacktap( View v )
	{
		final SharedPreferences preferences = getSharedPreferences( SettingsEditor.ADVANCED_SETTINGS, Activity.MODE_PRIVATE );

		if ( preferences.getBoolean( SettingsEditor.INTERNAL_VIDEO, true ) )
		{
			if ( mediaController == null )
				return;

			if ( mediaController.isShowing() )
				mediaController.hide();
			else
				mediaController.show( 3000 );
		}
	}
}
