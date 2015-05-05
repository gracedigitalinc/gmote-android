package com.plugplayer.plugplayer.activities;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.example.android.imagedownloader.ImageDownloader;
import com.plugplayer.plugplayer.R;
import com.plugplayer.plugplayer.upnp.ControlPointListener;
import com.plugplayer.plugplayer.upnp.Item;
import com.plugplayer.plugplayer.upnp.PlugPlayerControlPoint;
import com.plugplayer.plugplayer.upnp.Renderer;
import com.plugplayer.plugplayer.upnp.Renderer.PlayMode;
import com.plugplayer.plugplayer.upnp.Renderer.PlayState;
import com.plugplayer.plugplayer.upnp.RendererListener;
import com.plugplayer.plugplayer.upnp.Server;

public class NowPlayingActivity extends Activity implements ControlPointListener, RendererListener, OnSeekBarChangeListener// , ImageLoaderListener
{
	protected PlugPlayerControlPoint controlPoint;

	TextView title;
	protected TextView album;
	protected TextView artist;

	protected TextView beginTime;
	protected TextView endTime;
	protected SeekBar seekBar;
	SeekBar volumeBar;

	protected ImageView albumArt;
	ProgressBar artProgress;

	protected ProgressBar progressBar;

	ImageButton prevButton;
	ImageButton stopButton;
	protected ImageButton playButton;
	ImageButton nextButton;

	ImageButton volumedownButton;
	ImageButton volumeupButton;

	public static NowPlayingActivity me = null;

	public static Handler handler = new Handler(); // TODO too much of a hack?

	private float newVolume = -1;
	private Thread volumeThread = null;
	private final Runnable volumeRunnable = new Runnable()
	{
		public void run()
		{
			float useVolume = 0;

			while ( true )
			{
				synchronized ( NowPlayingActivity.this )
				{
					try
					{
						while ( newVolume < 0 )
							NowPlayingActivity.this.wait();

						useVolume = newVolume;
						newVolume = -1;
					}
					catch ( InterruptedException e )
					{
						e.printStackTrace();
					}
				}

				Renderer r = controlPoint.getCurrentRenderer();

				if ( r != null )
				{
					float currentVolume = r.getVolume();

					if ( currentVolume != useVolume )
						r.setVolume( useVolume );
				}
			}
		}
	};

	@Override
	public void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );
		setContentView( R.layout.nowplaying );

		me = this;

		if ( volumeThread == null )
		{
			volumeThread = new Thread( volumeRunnable );
			volumeThread.start();
		}

		title = (TextView)findViewById( R.id.title );
		album = (TextView)findViewById( R.id.album );
		artist = (TextView)findViewById( R.id.artist );

		beginTime = (TextView)findViewById( R.id.begintime );
		endTime = (TextView)findViewById( R.id.endtime );
		albumArt = (ImageView)findViewById( R.id.albumArt );
		artProgress = (ProgressBar)findViewById( R.id.artProgress );

		seekBar = (SeekBar)findViewById( R.id.seekbar );
		seekBar.setOnSeekBarChangeListener( this );

		progressBar = (ProgressBar)findViewById( R.id.progressbar );

		controlPoint = PlugPlayerControlPoint.getInstance();
		controlPoint.addControlPointListener( this );

		prevButton = (ImageButton)findViewById( R.id.prev );
		prevButton.setOnClickListener( new OnClickListener()
		{
			public void onClick( View v )
			{
				Renderer r = controlPoint.getCurrentRenderer();
				if ( r != null && r.hasPrev() )
					r.prev();
			}
		} );

		stopButton = (ImageButton)findViewById( R.id.stop );
		stopButton.setOnClickListener( new OnClickListener()
		{
			public void onClick( View v )
			{
				Renderer r = controlPoint.getCurrentRenderer();
				if ( r != null )
				{
					r.setPlayState( PlayState.Stopped );
					r.stop();
				}
			}
		} );

		playButton = (ImageButton)findViewById( R.id.play );
		playButton.setOnClickListener( new OnClickListener()
		{
			public void onClick( View v )
			{
				Renderer r = controlPoint.getCurrentRenderer();
				if ( r != null )
				{
					if ( r.getPlayState() == PlayState.Playing )
						r.pause();
					else
						r.play();
				}
			}
		} );

		nextButton = (ImageButton)findViewById( R.id.next );
		nextButton.setOnClickListener( new OnClickListener()
		{
			public void onClick( View v )
			{
				Renderer r = controlPoint.getCurrentRenderer();
				if ( r != null && r.hasNext() )
					r.next();
			}
		} );

		volumedownButton = (ImageButton)findViewById( R.id.volumedown );
		volumedownButton.setOnClickListener( new OnClickListener()
		{
			public void onClick( View v )
			{
				Renderer r = controlPoint.getCurrentRenderer();
				if ( r != null )
					r.volumeDec();
			}
		} );

		volumeBar = (SeekBar)findViewById( R.id.volumebar );
		volumeBar.setMax( 100 );
		volumeBar.setOnSeekBarChangeListener( this );
		volumeBar.setOnKeyListener( new View.OnKeyListener()
		{
			public boolean onKey( View v, int keyCode, KeyEvent event )
			{
				Renderer r = controlPoint.getCurrentRenderer();
				if ( r == null || event.getAction() != KeyEvent.ACTION_DOWN )
					return false;

				switch ( keyCode )
				{
					case KeyEvent.KEYCODE_DPAD_LEFT:
						if ( r != null )
							r.volumeDec();
						return true;

					case KeyEvent.KEYCODE_DPAD_RIGHT:
						if ( r != null )
							r.volumeInc();
						return true;

					default:
						return false;
				}
			}

		} );

		volumeupButton = (ImageButton)findViewById( R.id.volumeup );
		volumeupButton.setOnClickListener( new OnClickListener()
		{
			public void onClick( View v )
			{
				Renderer r = controlPoint.getCurrentRenderer();
				if ( r != null )
					r.volumeInc();
			}
		} );

		mediaRendererChanged( controlPoint.getCurrentRenderer(), null );
	}

	@Override
	public boolean dispatchKeyEvent( KeyEvent event )
	{
		if ( event.getAction() == KeyEvent.ACTION_UP )
			switch ( event.getKeyCode() )
			{
				case KeyEvent.KEYCODE_VOLUME_UP:
				case KeyEvent.KEYCODE_VOLUME_DOWN:
					controlPoint.getCurrentRenderer().getVolume();
			}

		return super.dispatchKeyEvent( event );
	}

	@Override
	public void onBackPressed()
	{
	}

	@Override
	public boolean onPrepareOptionsMenu( Menu menu )
	{
		menu.clear();

		final Renderer r = controlPoint.getCurrentRenderer();
		if ( r != null )
		{
			PlayMode mode = r.getPlayMode();

			MenuItem item = menu.add( 0, 1, 1, mode.getVisibleNameRes() );
			item.setIcon( mode.getIcon() );

			menu.add( 0, 0, 1, R.string.cancel );
		}

		return true;
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem item )
	{
		if ( item.getItemId() == 1 ) // playmode
		{
			final Renderer r = controlPoint.getCurrentRenderer();
			PlayMode mode = r.getPlayMode();
			r.setPlayMode( mode.next() );

			handler.post( new Runnable()
			{
				public void run()
				{
					NowPlayingActivity.this.openOptionsMenu();
				}
			} );

			return true;
		}

		return super.onOptionsItemSelected( item );
	}

	@Override
	public boolean onSearchRequested()
	{
		return false;
	}

	public void mediaRendererChanged( Renderer newRenderer, Renderer oldRenderer )
	{
		if ( oldRenderer != null )
			oldRenderer.removeRendererListener( this );

		if ( newRenderer != null )
		{
			newRenderer.addRendererListener( this );

			trackNumberChanged( newRenderer.getTrackNumber() );
			volumeChanged( newRenderer.getVolume() );
			playStateChanged( newRenderer.getPlayState() );
		}
		else
		{
			handler.post( new Runnable()
			{
				public void run()
				{
					updateItem( null );
				}
			} );
		}
	}

	public void mediaServerChanged( Server newServer, Server oldServer )
	{
	}

	public void mediaServerListChanged()
	{
	}

	public void mediaRendererListChanged()
	{
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "inside mediaRendererListChanged, no implementation:NowPlayingActivity:PP" );
	}

	public void error( String message )
	{
		// TODO Auto-generated method stub

	}

	public void playStateChanged( final PlayState state )
	{
		handler.post( new Runnable()
		{
			public void run()
			{
				if ( state == PlayState.Playing )
					playButton.setImageResource( R.drawable.pause );
				else
					playButton.setImageResource( R.drawable.play );
			}
		} );
	}

	public void playModeChanged( PlayMode mode )
	{
	}

	public void playlistChanged()
	{
		Renderer r = controlPoint.getCurrentRenderer();
		if ( r != null )
			trackNumberChanged( r.getTrackNumber() );
	}

	public void volumeChanged( final float volume )
	{
		Renderer r = controlPoint.getCurrentRenderer();
		if ( r != null && (!r.isVolumeChanging() || r.isMute()) )
		{
			handler.post( new Runnable()
			{
				public void run()
				{
					volumeBar.setProgress( (int)(100 * volume) );
				}
			} );
		}
	}

	protected void updateArt( Item item )
	{
		String artURL = item.getArtURL();
		ImageDownloader.fullsize.download( artURL, albumArt );
	}

	protected void updateSeek()
	{
		Renderer r = controlPoint.getCurrentRenderer();
		if ( r != null && (!r.supportsSeek() || r.getTotalSeconds() == -1) )
		{
			seekBar.setVisibility( View.GONE );
			progressBar.setVisibility( View.VISIBLE );
		}
		else
		{
			seekBar.setVisibility( View.VISIBLE );
			progressBar.setVisibility( View.GONE );
		}
	}

	public void trackNumberChanged( final int trackNumber )
	{
		handler.post( new Runnable()
		{
			public void run()
			{
				Renderer r = controlPoint.getCurrentRenderer();

				if ( r == null )
				{
					updateItem( null );
					return;
				}

				updateSeek();

				if ( r.hasPrev() )
				{
					prevButton.setEnabled( true );
					prevButton.setAlpha( 255 );
				}
				else
				{
					prevButton.setEnabled( false );
					prevButton.setAlpha( 100 );
				}

				if ( r.hasNext() )
				{
					nextButton.setEnabled( true );
					nextButton.setAlpha( 255 );
				}
				else
				{
					nextButton.setEnabled( false );
					nextButton.setAlpha( 100 );
				}

				Item item = null;
				if ( trackNumber >= 0 && trackNumber < r.getPlaylistEntryCount() )
					item = r.getPlaylistEntry( trackNumber );

				updateItem( item );
			}
		} );
	}

	protected void updateItem( Item item )
	{
		Renderer r = controlPoint.getCurrentRenderer();

		if ( r != null && item != null )
		{
			title.setText( item.getTitle() );
			album.setText( item.getAlbum() );
			artist.setText( item.getArtist() );

			albumArt.setVisibility( View.VISIBLE );
			artProgress.setVisibility( View.GONE );

			updateArt( item );

			if ( r.hasNext() )
			{
				int index = r.nextTrackNumber();
				Item nextItem = r.getPlaylistEntry( index );
				if ( nextItem != null )
				{
					String artURL = nextItem.getArtURL();
					ImageDownloader.fullsize.download( artURL, new ImageView( NowPlayingActivity.this ) );
				}
			}
		}
		else
		{
			title.setText( " " );
			album.setText( " " );
			artist.setText( " " );

			progressBar.setMax( 0 );
			progressBar.setProgress( 0 );

			seekBar.setMax( 0 );
			seekBar.setProgress( 0 );

			beginTime.setText( "00:00" );
			endTime.setText( "00:00" );

			albumArt.setImageResource( R.drawable.note );
		}
	}

	// public void onImageLoaded( Object handle, final Bitmap image )
	// {
	// handler.post( new Runnable()
	// {
	// public void run()
	// {
	// imageHandle = null;
	//
	// albumArt.setVisibility( View.VISIBLE );
	// artProgress.setVisibility( View.GONE );
	//
	// if ( image == null )
	// albumArt.setImageResource( R.drawable.note );
	// else
	// albumArt.setImageBitmap( image );
	// }
	// } );
	// }

	public void timeStampChanged( final int seconds )
	{
		if ( !seekdown )
			handler.post( new Runnable()
			{
				public void run()
				{
					timeStampChangedInternal( seconds );
				}
			} );
	}

	protected void timeStampChangedInternal( final int seconds )
	{
		beginTime.setText( String.format( "%02d:%02d", seconds / 60, seconds % 60 ) );

		int totalSeconds = controlPoint.getCurrentRenderer().getTotalSeconds();
		int remaining = totalSeconds - seconds;
		endTime.setText( String.format( "%02d:%02d", remaining / 60, remaining % 60 ) );

		seekBar.setMax( totalSeconds );
		seekBar.setProgress( seconds );

		progressBar.setMax( totalSeconds );
		progressBar.setProgress( seconds );

		updateSeek();

		Renderer r = controlPoint.getCurrentRenderer();

		if ( r != null && r.hasPrev() )
		{
			prevButton.setEnabled( true );
			prevButton.setAlpha( 255 );
		}
		else
		{
			prevButton.setEnabled( false );
			prevButton.setAlpha( 100 );
		}
	}

	int seekSecond;

	public void onProgressChanged( SeekBar arg0, int arg1, boolean arg2 )
	{
		if ( arg2 )
		{
			if ( arg0 == seekBar )
			{
				seekSecond = seekBar.getProgress();
				timeStampChangedInternal( seekSecond );
			}
			else
			{
				// Renderer r = controlPoint.getCurrentRenderer();

				synchronized ( NowPlayingActivity.this )
				{
					newVolume = arg0.getProgress() / 100.0f;
					NowPlayingActivity.this.notify();
				}
				// r.setVolume( arg0.getProgress() / 100.0f );
			}
		}
	}

	boolean seekdown = false;

	public void onStartTrackingTouch( SeekBar arg0 )
	{
		Renderer r = controlPoint.getCurrentRenderer();

		if ( r == null )
			return;

		if ( arg0 == seekBar )
		{
			seekdown = true;
			seekSecond = seekBar.getProgress();
		}
		else
		{
			r.beginVolumeChange();
		}
	}

	public void onStopTrackingTouch( SeekBar arg0 )
	{
		Renderer r = controlPoint.getCurrentRenderer();

		if ( r == null )
			return;

		if ( arg0 == seekBar )
		{
			seekdown = false;
			r.seekToSecond( seekSecond );
		}
		else
		{
			r.endVolumeChange();
		}
	}

	public void onErrorFromDevice( String error )
	{
	}
}
