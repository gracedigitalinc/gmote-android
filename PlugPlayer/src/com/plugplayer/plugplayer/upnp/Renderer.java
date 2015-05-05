package com.plugplayer.plugplayer.upnp;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import android.widget.ImageView;

import com.plugplayer.plugplayer.R;
import com.plugplayer.plugplayer.activities.NowPlayingActivity;

public abstract class Renderer extends MediaDevice
{
	private final List<RendererListener> listeners;

	public boolean inserting = false;

	public Renderer()
	{
		listeners = new ArrayList<RendererListener>();
	}

	private final transient ScheduledExecutorService scheduler = Executors.newScheduledThreadPool( 1 );
	transient ScheduledFuture timerHandle = null;

	@Override
	public void setActive( boolean active, boolean hardStop )
	{
		if ( active == getActive() )
			return;

		if ( active )
		{
			timerHandle = scheduler.scheduleWithFixedDelay( new Runnable()
			{
				public void run()
				{
					Renderer.this.updateCurrentTimeStamp();
				}
			}, 1000, 1000, TimeUnit.MILLISECONDS );
		}
		else
		{
			if ( timerHandle != null )
			{
				timerHandle.cancel( true );
				timerHandle = null;
			}
		}
		super.setActive( active, hardStop );
	}

	protected abstract void updateCurrentTimeStamp();

	public static enum PlayMode
	{
		Unsupported( "UNSUPPORTED", R.drawable.playmode_normal, R.string.unsupported ), Normal( "NORMAL", R.drawable.playmode_normal, R.string.normal ), Shuffle(
				"SHUFFLE", R.drawable.playmode_shuffle, R.string.shuffle ), RepeatOne( "REPEAT_ONE", R.drawable.playmode_repeat_one, R.string.repeat_one ), RepeatAll(
				"REPEAT_ALL", R.drawable.playmode_repeat_all, R.string.repeat_all ), Random( "RANDOM", R.drawable.playmode_shuffle, R.string.random ), Direct1(
				"DIRECT_1", R.drawable.playmode_normal, R.string.direct ), Intro( "INTRO", R.drawable.playmode_normal, R.string.intro );

		private PlayMode( String UPNPvalue, int icon, int visibleNameRes )
		{
			this.UPNPvalue = UPNPvalue;
			this.icon = icon;
			this.visibleNameRes = visibleNameRes;
		}

		private final String UPNPvalue;
		private final int icon;
		private final int visibleNameRes;

		public PlayMode next()
		{
			switch ( this )
			{
				case Unsupported:
					return Normal;
				case Normal:
					return Shuffle;
				case Shuffle:
					return RepeatOne;
				case RepeatOne:
					return RepeatAll;
				case RepeatAll:
					return Random;
				case Random:
					return Direct1;
				case Direct1:
					return Intro;
				case Intro:
					return Normal;
			}

			return Unsupported;
		}

		public static PlayMode fromUpnpValue( String UPNPvalue )
		{
			PlayMode rval = Unsupported;

			for ( PlayMode mode : PlayMode.values() )
				if ( mode.UPNPvalue.equals( UPNPvalue ) )
					return mode;

			return rval;
		}

		public String upnpValue()
		{
			return UPNPvalue;
		}

		public int getIcon()
		{
			return icon;
		}

		public int getVisibleNameRes()
		{
			return visibleNameRes;
		}
	}

	public static enum PlayState
	{
		Stopped( "STOPPED" ), Playing( "PLAYING" ), Transitioning( "TRANSITIONING" ), Paused( "PAUSED_PLAYBACK" ), NoMedia( "NO_MEDIA_PRESENT" );

		private PlayState( String UPNPvalue )
		{
			this.UPNPvalue = UPNPvalue;
		}

		private final String UPNPvalue;

		public static PlayState fromUpnpValue( String UPNPvalue )
		{
			PlayState rval = NoMedia;

			for ( PlayState mode : PlayState.values() )
				if ( mode.UPNPvalue.equals( UPNPvalue ) )
					return mode;

			return rval;
		}

		public String upnpValue()
		{
			return UPNPvalue;
		}
	}

	@Override
	public void loadIcon( ImageView imageView )
	{
		if ( getIconURL() != null )
			super.loadIcon( imageView );
		else
			imageView.setImageResource( R.drawable.speaker );
	}

	public void addRendererListener( RendererListener listener )
	{
		super.addMediaDeviceListener( listener );

		synchronized ( listeners )
		{
			listeners.add( listener );
		}
	}

	public void removeRendererListener( RendererListener listener )
	{
		super.removeMediaDeviceListener( listener );

		synchronized ( listeners )
		{
			listeners.remove( listener );
		}
	}

	protected void emitPlayStateChanged( Renderer.PlayState state )
	{
		synchronized ( listeners )
		{
			for ( RendererListener listener : listeners )
				listener.playStateChanged( state );
		}
	}

	protected void emitPlayModeChanged( Renderer.PlayMode mode )
	{
		synchronized ( listeners )
		{
			for ( RendererListener listener : listeners )
				listener.playModeChanged( mode );
		}
	}

	public void emitPlaylistChanged()
	{
		synchronized ( listeners )
		{
			for ( RendererListener listener : listeners )
				listener.playlistChanged();
		}
	}

	protected void emitVolumeChanged( float volume )
	{
		synchronized ( listeners )
		{
			for ( RendererListener listener : listeners )
				listener.volumeChanged( volume );
		}
	}

	protected void emitTrackNumberChanged( int trackNumber )
	{
		synchronized ( listeners )
		{
			for ( RendererListener listener : listeners )
				listener.trackNumberChanged( trackNumber );
		}
	}

	protected void emitTimeStampChanged( int seconds )
	{
		synchronized ( listeners )
		{
			for ( RendererListener listener : listeners )
				listener.timeStampChanged( seconds );
		}
	}

	public abstract boolean hasNext();

	public abstract int nextTrackNumber();

	public abstract void next();

	public abstract boolean hasPrev();

	public abstract int prevTrackNumber();

	public abstract void prev();

	public abstract void play();

	public abstract void pause();

	public abstract void stop();

	public abstract PlayState getPlayState();

	public abstract int getCurrentSeconds();

	public abstract int getTotalSeconds();

	public abstract void setPlayMode( PlayMode mode );

	public abstract PlayMode getPlayMode();

	public abstract void setTrackNumber( int newTrackNumber );

	public abstract int getTrackNumber();

	public abstract void setVolume( float newVolume );

	public abstract float getVolume();

	public abstract void volumeInc();

	public abstract void volumeDec();

	public abstract boolean isMute();

	public abstract void setMute( boolean newMute );

	public abstract void insertPlaylistEntry( Item newEntry, int index );

	public abstract void removePlaylistEntry( int index );

	public abstract void movePlaylistEntry( int fromIndex, int toIndex );

	public abstract void removePlaylistEntries( int index, int count );

	public abstract Item getPlaylistEntry( int index );

	public abstract int getPlaylistEntryCount();

	public abstract boolean supportsSeek();

	public abstract boolean seekToSecond( int second );

	private boolean volumeChanging = false;

	public void beginVolumeChange()
	{
		volumeChanging = true;
		NowPlayingActivity.handler.removeCallbacks( clearVolumeChanging );
	}

	public void endVolumeChange()
	{
		NowPlayingActivity.handler.postDelayed( clearVolumeChanging, 2000 );
	}

	private final Runnable clearVolumeChanging = new Runnable()
	{
		public void run()
		{
			volumeChanging = false;
		}
	};

	public boolean isVolumeChanging()
	{
		return volumeChanging;
	}

	public void setPlayState( PlayState stopped )
	{
	}
}
