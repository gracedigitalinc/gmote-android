package com.plugplayer.plugplayer.upnp;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.cybergarage.http.HTTPRequest;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;

import com.plugplayer.plugplayer.activities.MainActivity;

public class FixedMediaPlayer extends MediaPlayer
{
	boolean wavSource = false;

	@Override
	public void setDataSource( String path ) throws IOException, IllegalArgumentException, IllegalStateException
	{
		wavSource = false;
		super.setDataSource( path );
	}

	@Override
	public void setDataSource( Context context, Uri uri ) throws IOException, IllegalArgumentException, SecurityException, IllegalStateException
	{
		wavSource = false;
		super.setDataSource( context, uri );
	}

	@Override
	public void setDataSource( FileDescriptor fd ) throws IOException, IllegalArgumentException, IllegalStateException
	{
		wavSource = false;
		super.setDataSource( fd );
	}

	@Override
	public void setDataSource( FileDescriptor fd, long offset, long length ) throws IOException, IllegalArgumentException, IllegalStateException
	{
		wavSource = false;
		super.setDataSource( fd, offset, length );
	}

	String urlString = null;

	public void setWAVDataSource( String urlString )
	{
		wavSource = true;
		this.urlString = urlString;
	}

	OnErrorListener errorListener = null;

	@Override
	public void setOnErrorListener( OnErrorListener listener )
	{
		errorListener = listener;
		super.setOnErrorListener( listener );
	}

	OnPreparedListener preparedListener = null;

	@Override
	public void setOnPreparedListener( OnPreparedListener listener )
	{
		preparedListener = listener;
		super.setOnPreparedListener( listener );
	}

	OnCompletionListener completionListener = null;

	@Override
	public void setOnCompletionListener( OnCompletionListener listener )
	{
		completionListener = listener;
		super.setOnCompletionListener( listener );
	};

	InputStream is = null;

	Thread prepareThread = null;

	private void stopPrepare() throws InterruptedException, IOException
	{
		if ( prepareThread != null && state == State.Preparing )
		{
			state = State.Unknown;

			if ( is != null )
			{
				is.close();
				is = null;
			}

			if ( prepareThread != null )
				prepareThread.join();
		}
	}

	@Override
	public void prepareAsync() throws IllegalStateException
	{
		if ( wavSource )
		{
			try
			{
				stopPrepare();

				if ( state != State.Unknown )
					return;

				state = State.Preparing;

				prepareThread = new Thread( prepareRunnable );
				prepareThread.start();
			}
			catch ( Exception e )
			{
				e.printStackTrace();
				errorListener.onError( this, -1, -1 );
			}
		}
		else
			super.prepareAsync();
	}

	public static enum State
	{
		Playing, Paused, Stopped, Preparing, Unknown
	};

	State state = State.Unknown;

	AudioTrack audioTrack = null;
	Thread streamThread = null;

	int sampleRate = 44100;
	int sampleSize = 2;
	int channels = 2;

	@Override
	public void start() throws IllegalStateException
	{
		if ( wavSource )
		{
			try
			{
				if ( state == State.Stopped )
				{
					int channelConfig = channels == 2 ? AudioFormat.CHANNEL_CONFIGURATION_STEREO : AudioFormat.CHANNEL_OUT_MONO;
					int format = sampleSize == 2 ? AudioFormat.ENCODING_PCM_16BIT : AudioFormat.ENCODING_PCM_8BIT;
					int bufferSize = AudioTrack.getMinBufferSize( sampleRate, channelConfig, format );

					Log.i( MainActivity.appName, "AudioTrack: " + sampleRate + "," + channels + "," + sampleSize );
					Log.i( MainActivity.appName, "AudioTrack: " + bufferSize );

					audioTrack = new AudioTrack( AudioManager.STREAM_MUSIC, sampleRate, channelConfig, format, bufferSize, AudioTrack.MODE_STREAM );

					state = State.Playing;

					streamThread = new Thread( streamingRunnable );
					streamThread.start();
				}

				state = State.Playing;

				audioTrack.play();
			}
			catch ( Exception e )
			{
				e.printStackTrace();
				errorListener.onError( this, -2, -2 );
			}
		}
		else
			super.start();
	}

	@Override
	public void pause() throws IllegalStateException
	{
		if ( wavSource )
		{
			if ( state != State.Paused )
			{
				state = State.Paused;
				audioTrack.pause();
			}
		}
		else
			super.pause();
	}

	@Override
	public void stop() throws IllegalStateException
	{
		System.out.println( "stop" );

		if ( wavSource )
		{
			try
			{
				stopPrepare();

				state = State.Stopped;

				if ( audioTrack != null )
					audioTrack.stop();

				if ( streamThread != null )
				{
					streamThread.join();
					streamThread = null;
				}

				if ( is != null )
				{
					is.close();
					is = null;
				}
			}
			catch ( Exception e )
			{
				e.printStackTrace();
				state = State.Stopped;
			}
		}
		else
			super.stop();
	};

	@Override
	public void reset()
	{
		System.out.println( "reset" );

		if ( wavSource )
		{
			try
			{
				stop();
				if ( audioTrack != null )
					audioTrack.release();
				audioTrack = null;
				wavDuration = 0;
				seekOffset = 0;
				wavSource = false;
				state = State.Unknown;
			}
			catch ( Exception e )
			{
				e.printStackTrace();
			}
		}
		else
			super.reset();
	}

	int wavDuration = 0;

	@Override
	public int getDuration()
	{
		if ( wavSource )
		{
			return wavDuration;
		}
		else
			return super.getDuration();
	}

	int seekOffset = 0;

	@Override
	public int getCurrentPosition()
	{
		if ( wavSource )
		{
			if ( audioTrack != null )
			{
				int frames = audioTrack.getPlaybackHeadPosition();
				int mills = (int)((frames / (float)sampleRate) * 1000);
				return seekOffset + mills;
			}

			return 0;
		}
		else
			return super.getCurrentPosition();
	}

	@Override
	public void seekTo( int msec ) throws IllegalStateException
	{
		if ( wavSource )
		{
			try
			{
				State originalState = state;

				System.out.println( "stopping" );

				stop();

				// audioTrack.stop();

				// audioTrack.flush();

				seekOffset = 0;
				seekOffset = msec /*- getCurrentPosition()*/;

				int seekBytes = (int)(dataStart + msec / 1000.0 * sampleRate * channels * sampleSize);

				// URL url = new URL( urlString );
				// HttpURLConnection httpConn = (HttpURLConnection)url.openConnection();
				// httpConn.setRequestProperty( "Connection", "close" );
				// httpConn.setRequestProperty( "User-Agent", HTTPRequest.UserAgent );
				// httpConn.setRequestProperty( "Range", "bytes=" + seekBytes + "-" );

				client = new DefaultHttpClient();
				HttpConnectionParams.setSocketBufferSize( client.getParams(), IO_BUFFER_SIZE );
				getRequest = new HttpGet( urlString );
				getRequest.setHeader( "User-Agent", HTTPRequest.UserAgent );
				getRequest.setHeader( "Range", "bytes=" + seekBytes + "-" );
				HttpResponse response = client.execute( getRequest );
				entity = response.getEntity();

				System.out.println( "getting input stream" );
				is = entity.getContent();
				// is = httpConn.getInputStream();

				// byte bytes[] = new byte[16 * 1024 * channels * sampleSize];
				// int read = is.read( bytes );
				// audioTrack.write( bytes, 0, read );

				Thread.sleep( 1000 );

				state = State.Playing;

				System.out.println( "starting stream thread" );
				streamThread = new Thread( streamingRunnable );
				streamThread.start();

				System.out.println( "starting audio track" );
				audioTrack.play();

				if ( originalState == State.Paused )
					audioTrack.pause();
			}
			catch ( Exception e )
			{
				e.printStackTrace();
				errorListener.onError( FixedMediaPlayer.this, -5, -5 );
			}
		}
		else
			super.seekTo( msec );
	}

	@Override
	public boolean isPlaying()
	{
		if ( wavSource )
			return state == State.Playing;
		else
			return super.isPlaying();
	}

	int dataStart = 0;

	final int IO_BUFFER_SIZE = 8 * 1024;

	HttpEntity entity;
	HttpClient client;
	HttpGet getRequest = null;

	Runnable prepareRunnable = new Runnable()
	{
		public void run()
		{
			try
			{
				// URL url = new URL( urlString );

				// HttpURLConnection httpConn = (HttpURLConnection)url.openConnection();
				// httpConn.setRequestProperty( "Connection", "close" );
				// httpConn.setRequestProperty( "User-Agent", HTTPRequest.UserAgent );

				client = new DefaultHttpClient();
				HttpConnectionParams.setSocketBufferSize( client.getParams(), IO_BUFFER_SIZE );
				getRequest = new HttpGet( urlString );
				getRequest.setHeader( "User-Agent", HTTPRequest.UserAgent );
				HttpResponse response = client.execute( getRequest );
				entity = response.getEntity();

				System.out.println( "opening prepare stream" );
				is = entity.getContent();
				// is = httpConn.getInputStream();

				byte header[] = new byte[12];
				is.read( header );
				if ( (!new String( header, 0, 4 ).equals( "RIFF" )) || (!new String( header, 8, 4 ).equals( "WAVE" )) )
					throw new Exception( "Not a WAVE file" );

				byte format[] = new byte[24];
				is.read( format );
				if ( (!new String( format, 0, 4 ).equals( "fmt " )) )
					throw new Exception( "Not a WAVE file" );

				int formatCode = ((format[1 + 8] & 0xff) << 8) + (format[0 + 8] & 0xff);
				if ( formatCode != 0x001 )
					throw new Exception( "Format not supported:" + formatCode );

				channels = ((format[1 + 10] & 0xff) << 8) + (format[0 + 10] & 0xff);
				sampleRate = ((format[3 + 12] & 0xff) << 24) + ((format[2 + 12] & 0xff) << 16) + ((format[1 + 12] & 0xff) << 8) + (format[0 + 12] & 0xff);
				sampleSize = (int)Math.max( 1, Math.ceil( (((format[1 + 22] & 0xff) << 8) + (format[0 + 22] & 0xff)) / 8.0 ) );

				int x = 4;
				byte databuf[] = new byte[4];
				is.read( databuf );
				while ( !"data".equals( new String( databuf, 0, 4 ) ) )
				{
					databuf[0] = databuf[1];
					databuf[1] = databuf[2];
					databuf[2] = databuf[3];
					if ( is.read( databuf, 3, 1 ) == 0 )
						break;

					x++;
				}

				is.read( databuf );
				int bytes = ((databuf[3] & 0xff) << 24) + ((databuf[2] & 0xff) << 16) + ((databuf[1] & 0xff) << 8) + (databuf[0] & 0xff);
				wavDuration = (int)(1000 * (bytes / ((float)sampleRate * channels * sampleSize)));

				dataStart = 12 + 24 + x + 4;

				if ( state == State.Preparing )
				{
					state = State.Stopped;
					preparedListener.onPrepared( FixedMediaPlayer.this );
				}
			}
			catch ( Exception e )
			{
				// We aborted the prepare, so just return.
				if ( state != State.Preparing )
					return;

				// If we can't handle this format, don't throw an error, try again using the built-in wav player.
				try
				{
					wavSource = false;
					setDataSource( urlString );
					prepareAsync();
				}
				catch ( Exception e1 )
				{
					// TODO Auto-generated catch block
					e1.printStackTrace();
					errorListener.onError( FixedMediaPlayer.this, -4, -4 );
				}
			}
		}
	};

	Runnable streamingRunnable = new Runnable()
	{
		public void run()
		{
			System.out.println( "stream start" );

			try
			{
				int read = 1;

				byte buf[] = new byte[16 * 1024 * channels * sampleSize];

				while ( read > 0 && state != State.Stopped )
				{
					read = is.read( buf );
					int written = 0;

					while ( written < read && state != State.Stopped )
					{
						written += audioTrack.write( buf, written, (read - written) );
						if ( written < read )
						{
							int mills = (int)(1000 * (read - written) / ((float)sampleRate * channels * sampleSize));
							Thread.sleep( mills );
						}
					}
				}
			}
			catch ( Exception e )
			{
				e.printStackTrace();
				errorListener.onError( FixedMediaPlayer.this, -3, -3 );
			}

			if ( state != State.Stopped )
			{
				MainActivity.me.getHandler().post( new Runnable()
				{
					public void run()
					{
						completionListener.onCompletion( FixedMediaPlayer.this );
					}
				} );
			}
		}
	};
}
