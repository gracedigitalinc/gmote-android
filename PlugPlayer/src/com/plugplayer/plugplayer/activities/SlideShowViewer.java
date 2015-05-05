package com.plugplayer.plugplayer.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.example.android.imagedownloader.ImageDownloader;
import com.plugplayer.plugplayer.R;
import com.plugplayer.plugplayer.upnp.AndroidRenderer;
import com.plugplayer.plugplayer.upnp.Renderer;
import com.plugplayer.plugplayer.upnp.Renderer.PlayState;

public class SlideShowViewer extends Activity
{
	private static SlideShowViewer me = null;
	private static boolean shown = false;
	private static long startTime = 0;

	public static void show()
	{
		if ( !shown )
		{
			shown = true;
			Intent intent = new Intent( (Context)MainActivity.me, MainActivity.me.getSlideShowClass() );
			intent.addFlags( Intent.FLAG_ACTIVITY_NEW_TASK );
			MainActivity.me.startActivity( intent );
		}
	}

	public static void hide()
	{
		if ( me != null )
			me.finish();
	}

	public static boolean isShown()
	{
		return shown;
	}

	static Runnable doneRunable = new Runnable()
	{
		public void run()
		{
			if ( me != null && listener != null )
				listener.imageDoneShowing();
		}
	};

	@Override
	public void onBackPressed()
	{
		Renderer r = MainActivity.me.getControlPoint().getCurrentRenderer();
		if ( r instanceof AndroidRenderer )
			((AndroidRenderer)r).stop();

		super.onBackPressed();
	}

	@Override
	protected void onPause()
	{
		if ( !isFinishing() )
		{
			Renderer r = MainActivity.me.getControlPoint().getCurrentRenderer();
			if ( r instanceof AndroidRenderer )
				((AndroidRenderer)r).stop();

			finish();
		}

		super.onPause();
	}

	@Override
	protected void onStop()
	{
		super.onStop();
		getWindow().setFlags( WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN );
		handler.removeCallbacks( doneRunable );
		me = null;
		resourceURI = null;
		shown = false;
		startTime = 0;
	}

	private ImageView imageView;
	private ProgressBar progress;
	private int errorCount = 0;

	@Override
	public void onCreate( final Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );

		if ( !shown )
		{
			finish();
			return;
		}

		me = this;

		setContentView( R.layout.slideshow );

		getWindow().setFlags( WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN );

		imageView = (ImageView)findViewById( R.id.imageview );
		progress = (ProgressBar)findViewById( R.id.progress );

		// setURI( resourceURI );
		if ( needPlay )
			play();
	}

	private static String resourceURI = null;

	private final Handler handler = new Handler();

	private final ImageDownloader.Callback imageLoadedCallback = new ImageDownloader.Callback()
	{
		public void setImageOnDrawable()
		{
			errorCount = 0;

			progress.setVisibility( View.GONE );

			handler.postDelayed( doneRunable, getTotalSeconds() * 1000 );
			startTime = System.currentTimeMillis();
		}

		public void setImageError()
		{
			progress.setVisibility( View.GONE );
			handler.removeCallbacks( doneRunable );

			errorCount++;
			if ( errorCount > 2 )
			{
				Renderer r = MainActivity.me.getControlPoint().getCurrentRenderer();
				if ( r instanceof AndroidRenderer )
					((AndroidRenderer)r).stop();

				finish();
			}
			else
			{
				if ( listener != null )
					listener.imageDoneShowing();
			}
		}
	};

	public static void setURI( final String resourceURIIn )
	{
		SlideShowViewer.resourceURI = resourceURIIn;
		playState = PlayState.Stopped;
	}

	static PlayState playState = PlayState.Stopped;
	static int pauseTime = 0;
	static boolean needPlay = false;

	public static void play()
	{
		if ( playState == PlayState.Paused )
		{
			playState = PlayState.Playing;
			me.handler.postDelayed( doneRunable, (getTotalSeconds() - pauseTime) * 1000 );
			startTime = System.currentTimeMillis() - (pauseTime * 1000);
			pauseTime = 0;
		}
		else if ( me != null && resourceURI != null && playState != PlayState.Playing )
		{
			me.handler.removeCallbacks( doneRunable );
			startTime = 0;
			pauseTime = 0;
			playState = PlayState.Playing;
			needPlay = false;

			me.handler.post( new Runnable()
			{
				public void run()
				{
					try
					{
						// If me can go away, should we lock here?
						if ( me != null )
						{
							me.progress.setVisibility( View.VISIBLE );
							ImageDownloader.fullsize.download( resourceURI, me.imageView, me.imageLoadedCallback, false );
						}
					}
					catch ( Exception e )
					{
						e.printStackTrace();
					}
				}
			} );
		}
		else
			needPlay = true;
	}

	public static void pause()
	{
		if ( me != null && playState != PlayState.Paused )
		{
			me.handler.removeCallbacks( doneRunable );
			pauseTime = getCurrentSeconds();
			playState = PlayState.Paused;
		}
	}

	public interface TimerListener
	{
		void imageDoneShowing();
	}

	private static TimerListener listener = null;

	public static void setTimerListener( TimerListener listener )
	{
		SlideShowViewer.listener = listener;
	}

	public static int getTotalSeconds()
	{
		return 8;
	}

	public static int getCurrentSeconds()
	{
		if ( startTime == 0 )
			return 0;
		else if ( pauseTime != 0 )
			return pauseTime;
		else
			return (int)((System.currentTimeMillis() - startTime) / 1000);
	}
}
