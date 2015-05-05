/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Modified for API level 7, always "Correct" downloading, hashbased image comparison, etc, etc - HAA
 */

package com.example.android.imagedownloader;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.widget.ImageView;

import com.android.vending.licensing.util.Base64;
import com.plugplayer.plugplayer.R;
import com.plugplayer.plugplayer.activities.MainActivity;
import com.plugplayer.plugplayer.upnp.AndroidServer;
import com.plugplayer.plugplayer.upnp.DirEntry;

/**
 * This helper class download images from the Internet and binds those with the provided ImageView.
 * 
 * <p>
 * It requires the INTERNET permission, which should be added to your application's manifest file.
 * </p>
 * 
 * A local cache of downloaded images is maintained internally to improve performance.
 */
public class ImageDownloader
{
	public static boolean USE_TMPFILE = false;
	public static boolean USE_CHECKSUM = true;
	public static float ADJUSTMENT = 0.0f;
	public static boolean POWEROF2 = false;

	final int IO_BUFFER_SIZE = 8 * 1024;

	public static interface Callback
	{
		void setImageOnDrawable();

		void setImageError();
	}

	private static final Bitmap thumbplaceholder = BitmapFactory.decodeResource( MainActivity.me.getResources(), R.drawable.notethumb );
	public static final ImageDownloader thumbnail = new ImageDownloader( 50, 25, MainActivity.me.getResources(), thumbplaceholder );

	private static final Bitmap fullplaceholder = BitmapFactory.decodeResource( MainActivity.me.getResources(), R.drawable.note );
	public static final ImageDownloader fullsize = new ImageDownloader( MainActivity.defaultSize, 1, MainActivity.me.getResources(), fullplaceholder );

	private final int targetSize;
	private final Resources resources;
	private final Bitmap defaultBitmap;

	// Soft cache for bitmaps kicked out of hard cache
	private final ConcurrentHashMap<Long, SoftReference<Bitmap>> sSoftBitmapCache;
	private final HashMap<Long, Bitmap> sHardBitmapCache;

	@SuppressWarnings( "serial" )
	public ImageDownloader( final int targetSize, final int hardCacheSize, final Resources resources, final Bitmap defaultBitmap )
	{
		this.targetSize = targetSize;
		this.defaultBitmap = defaultBitmap;
		this.resources = resources;

		sSoftBitmapCache = new ConcurrentHashMap<Long, SoftReference<Bitmap>>( hardCacheSize / 2 );
		// Hard cache, with a fixed maximum capacity and a life duration

		sHardBitmapCache = new LinkedHashMap<Long, Bitmap>( hardCacheSize / 2, 0.75f, true )
		{
			@Override
			protected boolean removeEldestEntry( LinkedHashMap.Entry<Long, Bitmap> eldest )
			{
				if ( size() > hardCacheSize )
				{
					// Entries push-out of hard reference cache are transferred to soft reference cache
					sSoftBitmapCache.put( eldest.getKey(), new SoftReference<Bitmap>( eldest.getValue() ) );
					return true;
				}
				else
					return false;
			}
		};
	}

	/**
	 * Download the specified image from the Internet and binds it to the provided ImageView. The binding is immediate if the image is found in the cache and
	 * will be done asynchronously otherwise. A null bitmap will be associated to the ImageView if an error occurs.
	 * 
	 * @param url
	 *            The URL of the image to download.
	 * @param imageView
	 *            The ImageView to bind the downloaded image to.
	 */
	public void download( String url, ImageView imageView )
	{
		download( url, imageView, false );
	}

	public void download( String url, ImageView imageView, boolean cacheOnly )
	{
		download( url, imageView, cacheOnly, null, true );
	}

	public void download( String url, ImageView imageView, Callback callback, boolean showDefault )
	{
		download( url, imageView, false, callback, showDefault );
	}

	public void download( String url, ImageView imageView, boolean cacheOnly, Callback callback, boolean showDefault )
	{
		if ( url == null || url.length() == 0 || url == DirEntry.NOART )
		{
			imageView.setImageBitmap( defaultBitmap );

			if ( callback != null )
				callback.setImageOnDrawable();

			return;
		}

		resetPurgeTimer();
		Bitmap bitmap = getBitmapFromCache( url );

		if ( bitmap == null && !cacheOnly )
		{
			forceDownload( url, imageView, callback, showDefault );
		}
		else if ( bitmap == null && cacheOnly )
		{
			imageView.setImageBitmap( defaultBitmap );

			if ( callback != null )
				callback.setImageOnDrawable();

			return;
		}
		else
		{
			// System.out.println( "Returned cached bitmap '" + url + "'" );

			synchronized ( tasksQueued )
			{
				// cancelPotentialDownload( url, imageView, true );
				cancelPotentialDownload( null, imageView, true );
			}

			imageView.setImageBitmap( bitmap );

			if ( callback != null )
				callback.setImageOnDrawable();
		}
	}

	/*
	 * Same as download but the image is always downloaded and the cache is not used. Kept private at the moment as its interest is not clear. private void
	 * forceDownload(String url, ImageView view) { forceDownload(url, view, null); }
	 */

	/**
	 * Same as download but the image is always downloaded and the cache is not used. Kept private at the moment as its interest is not clear.
	 * 
	 * @param callback
	 * @param showDefault
	 */
	private void forceDownload( String url, ImageView imageView, Callback callback, boolean showDefault )
	{
		// State sanity: url is guaranteed to never be null in DownloadedDrawable and cache keys.
		if ( url == null )
		{
			imageView.setImageDrawable( null );

			if ( callback != null )
				callback.setImageOnDrawable();

			return;
		}

		synchronized ( tasksQueued )
		{
			BitmapDownloaderTask task = cancelPotentialDownload( url, imageView, false );

			if ( task != null )
			{
				task.addImageView( imageView );

				Bitmap tempBitmap = defaultBitmap;
				if ( !showDefault )
				{
					if ( imageView.getDrawable() != null && imageView.getDrawable() instanceof BitmapDrawable )
						tempBitmap = ((BitmapDrawable)imageView.getDrawable()).getBitmap();
					else
						tempBitmap = null;
				}

				DownloadedDrawable downloadedDrawable = new DownloadedDrawable( task, resources, tempBitmap, callback, showDefault );
				imageView.setImageDrawable( downloadedDrawable );
				// System.out.println( "Attached task to " + imageView );
				task.executeLIFO( url );
			}
		}
	}

	/**
	 * Returns true if the current download has been canceled or if there was no download in progress on this image view. Returns false if the download in
	 * progress deals with the same url. The download is not stopped in that case.
	 */
	private BitmapDownloaderTask cancelPotentialDownload( String url, ImageView imageView, boolean cancelOnly )
	{
		BitmapDownloaderTask bitmapDownloaderTask = getBitmapDownloaderTask( imageView );

		// Check to see if this imageview is currently loading this image.
		if ( bitmapDownloaderTask != null )
		{
			String bitmapUrl = bitmapDownloaderTask.url;
			if ( (bitmapUrl == null) || (!bitmapUrl.equals( url )) )
			{
				// This imageview is downloading a different image.
				bitmapDownloaderTask.removeImage( imageView );
				// System.out.println( "removed imageview reference(" + bitmapDownloaderTask.imageViewReferences.size() + ") from (" + imageView + ")" );

				if ( bitmapDownloaderTask.imageViewReferences.isEmpty() )
				{
					// System.out.println( "Canceling task for '" + bitmapUrl + "'" );
					bitmapDownloaderTask.cancel( true );
				}
			}
			else
			{
				// The same URL is already being downloaded by the same imageView.
				return null;
			}
		}

		if ( cancelOnly )
			return null;

		// Check to see if another imageview is currently loading this image
		for ( BitmapDownloaderTask task : tasksQueued )
		{
			if ( task.url.equals( url ) )
				return task;
		}

		for ( BitmapDownloaderTask task : tasksRunning )
		{
			if ( task.url.equals( url ) )
				return task;
		}

		return new BitmapDownloaderTask( imageView );
	}

	/**
	 * @param imageView
	 *            Any imageView
	 * @return Retrieve the currently active download task (if any) associated with this imageView. null if there is no such task.
	 */
	private static BitmapDownloaderTask getBitmapDownloaderTask( ImageView imageView )
	{
		if ( imageView != null )
		{
			Drawable drawable = imageView.getDrawable();
			if ( drawable instanceof DownloadedDrawable )
			{
				DownloadedDrawable downloadedDrawable = (DownloadedDrawable)drawable;
				return downloadedDrawable.getBitmapDownloaderTask();
			}
		}
		return null;
	}

	private static Callback getCallback( ImageView imageView )
	{
		if ( imageView != null )
		{
			Drawable drawable = imageView.getDrawable();
			if ( drawable instanceof DownloadedDrawable )
			{
				DownloadedDrawable downloadedDrawable = (DownloadedDrawable)drawable;
				return downloadedDrawable.getImageCallback();
			}
		}
		return null;
	}

	private static boolean showDefault( ImageView imageView )
	{
		if ( imageView != null )
		{
			Drawable drawable = imageView.getDrawable();
			if ( drawable instanceof DownloadedDrawable )
			{
				DownloadedDrawable downloadedDrawable = (DownloadedDrawable)drawable;
				return downloadedDrawable.showDefault();
			}
		}

		return true;
	}

	Bitmap downloadBitmap( String url )
	{
		String originalURL = url;

		try
		{
			if ( url.startsWith( "thumbnail:" ) )
			{
				url = "file:" + AndroidServer.getThumbnailForVideo( url.replace( "thumbnail:", "" ) );
			}

			File tmpFile = null;
			if ( USE_TMPFILE && !url.startsWith( "file:" ) )
			{
				HttpClient client = new DefaultHttpClient();
				HttpConnectionParams.setSocketBufferSize( client.getParams(), IO_BUFFER_SIZE );

				HttpGet getRequest = null;

				String authStringEnc = null;
				try
				{
					getRequest = new HttpGet( url );

					String userInfo = new URL( url ).getUserInfo();
					if ( userInfo != null )
					{
						authStringEnc = Base64.encode( userInfo.getBytes() );
						getRequest.setHeader( "Authorization", "Basic " + authStringEnc );
					}
				}
				catch ( Exception e )
				{
					Log.e( MainActivity.appName, "Couldn't get bitmap '" + url + "'", e );
					return null;
				}

				try
				{
					HttpResponse response = client.execute( getRequest );
					int statusCode = response.getStatusLine().getStatusCode();
					if ( statusCode != HttpStatus.SC_OK )
					{
						Log.w( MainActivity.appName, "Error " + statusCode + " while retrieving bitmap from " + url );
						return null;
					}

					HttpEntity entity = response.getEntity();
					if ( entity != null )
					{
						InputStream inputStream = null;
						OutputStream outputStream = null;

						try
						{
							inputStream = entity.getContent();

							File cacheDir = ((Context)MainActivity.me).getCacheDir();
							tmpFile = File.createTempFile( "img", null, cacheDir );
							url = tmpFile.toURL().toExternalForm();
							outputStream = new FileOutputStream( tmpFile );

							final byte[] buffer = new byte[IO_BUFFER_SIZE];

							while ( true )
							{
								int amountRead = inputStream.read( buffer );

								if ( amountRead == -1 )
									break;

								outputStream.write( buffer, 0, amountRead );
							}
						}
						finally
						{
							if ( outputStream != null )
								outputStream.close();

							if ( inputStream != null )
								inputStream.close();

							entity.consumeContent();
						}
					}
				}
				catch ( Exception e )
				{
					Log.e( MainActivity.appName, "Couldn't get bitmap '" + url + "'", e );
				}
			}

			if ( new URL( url ).getProtocol().equals( "file" ) )
			{
				String path = "";
				if ( url.startsWith( "file://" ) )
					path = url.replace( "file://", "" );
				else
					path = url.replace( "file:", "" );

				File f = new File( Uri.decode( path ) );
				FileInputStream is = new FileInputStream( f );

				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inSampleSize = 1;
				options.inJustDecodeBounds = true;

				Bitmap b = BitmapFactory.decodeStream( is, null, options );

				if ( options.outHeight > 0 && options.outWidth > 0 )
				{
					int size = Math.max( options.outHeight, options.outWidth );
					int sampleSize = (int)((size / (float)targetSize) + ADJUSTMENT); // anything over 1.5 would get sampled by 2
					if ( POWEROF2 )
					{
						if ( sampleSize == 3 )
							sampleSize = 4;
						if ( sampleSize > 4 && sampleSize < 8 )
							sampleSize = 8;
						if ( sampleSize > 8 && sampleSize < 16 )
							sampleSize = 16;
					}
					options.inSampleSize = sampleSize;
					options.inJustDecodeBounds = false;

					is.close();
					is = new FileInputStream( f );

					// Log.e( MainActivity.appName, originalURL + "Pre-Load (sample size:" + sampleSize + ") " + options.outWidth + "x" + options.outHeight );
					b = BitmapFactory.decodeStream( is, null, options );
					// Log.e( MainActivity.appName, originalURL + "Loaded (target size:" + targetSize + ") " + options.outWidth + "x" + options.outHeight );
				}

				is.close();

				// This eats up too much temp memory to be useful in practice
				// // Samples size is a rough pass at making this the right size, we then scale it to eek out a few more bytes savings.
				// int size = Math.max( options.outHeight, options.outWidth );
				// float sampleSize = size / (float)targetSize;
				// b = Bitmap.createScaledBitmap( b, (int)(options.outWidth / sampleSize), (int)(options.outHeight / sampleSize), true );

				if ( tmpFile != null )
					tmpFile.delete();

				return b;
			}
		}
		catch ( Exception e )
		{
			e.printStackTrace();
			return null;
		}

		// AndroidHttpClient is not allowed to be used from the main thread
		// final HttpClient client = (mode == Mode.NO_ASYNC_TASK) ? new DefaultHttpClient() : AndroidHttpClient.newInstance( "Android" );
		HttpClient client = new DefaultHttpClient();

		// It seems some manufactures have fucked this up, so we need to set it back to something sane.
		HttpConnectionParams.setSocketBufferSize( client.getParams(), IO_BUFFER_SIZE );

		HttpGet getRequest = null;

		String authStringEnc = null;
		try
		{
			getRequest = new HttpGet( url );

			String userInfo = new URL( url ).getUserInfo();
			if ( userInfo != null )
			{
				authStringEnc = Base64.encode( userInfo.getBytes() );
				getRequest.setHeader( "Authorization", "Basic " + authStringEnc );
			}
		}
		catch ( Exception e )
		{
			Log.e( MainActivity.appName, "Couldn't get bitmap '" + url + "'", e );
			return null;
		}

		try
		{
			HttpResponse response = client.execute( getRequest );
			int statusCode = response.getStatusLine().getStatusCode();
			if ( statusCode != HttpStatus.SC_OK )
			{
				Log.w( MainActivity.appName, "Error " + statusCode + " while retrieving bitmap from " + url );
				return null;
			}

			HttpEntity entity = response.getEntity();
			if ( entity != null )
			{
				InputStream inputStream = null;
				try
				{
					// BufferedHttpEntity bufHttpEntity = new BufferedHttpEntity( entity );

					inputStream = entity.getContent();

					BitmapFactory.Options options = new BitmapFactory.Options();
					options.inSampleSize = 1;
					options.inJustDecodeBounds = true;

					BufferedInputStream io = new BufferedInputStream( new FlushedInputStream( inputStream ), IO_BUFFER_SIZE );
					io.mark( IO_BUFFER_SIZE );

					Bitmap b = BitmapFactory.decodeStream( io, null, options );

					try
					{
						io.reset();
					}
					catch ( IOException e )
					{
						inputStream.close();
						entity.consumeContent();

						client = new DefaultHttpClient();
						getRequest = new HttpGet( url );
						if ( authStringEnc != null )
							getRequest.setHeader( "Authorization", "Basic " + authStringEnc );
						response = client.execute( getRequest );
						entity = response.getEntity();
						// bufHttpEntity = new BufferedHttpEntity( entity );
						inputStream = entity.getContent();
						io = new BufferedInputStream( new FlushedInputStream( inputStream ), IO_BUFFER_SIZE );
					}

					if ( options.outHeight > 0 && options.outWidth > 0 )
					{
						int size = Math.max( options.outHeight, options.outWidth );
						int sampleSize = (int)((size / (float)targetSize) + ADJUSTMENT); // anything over 1.5 would get sampled by 2
						if ( POWEROF2 )
						{
							if ( sampleSize == 3 )
								sampleSize = 4;
							if ( sampleSize > 4 && sampleSize < 8 )
								sampleSize = 8;
							if ( sampleSize > 8 && sampleSize < 16 )
								sampleSize = 16;
						}
						options.inSampleSize = sampleSize;
						options.inJustDecodeBounds = false;

						// Log.i( "TEST", "Loading '" + url + "', with sampleSize " + options.inSampleSize );
						b = BitmapFactory.decodeStream( io, null, options );
					}

					// This eats up too much temp memory to be useful in practice
					// // Samples size is a rough pass at making this the right size, we then scale it to eek out a few more bytes savings.
					// int size = Math.max( options.outHeight, options.outWidth );
					// float sampleSize = size / (float)targetSize;
					// b = Bitmap.createScaledBitmap( b, (int)(options.outWidth / sampleSize), (int)(options.outHeight / sampleSize), true );

					// System.out.println( "Downloaded bitmap '" + url + "'" );
					return b;
				}
				finally
				{
					if ( inputStream != null )
					{
						inputStream.close();
					}
					entity.consumeContent();
				}
			}
		}
		catch ( IOException e )
		{
			getRequest.abort();
			Log.w( MainActivity.appName, "I/O error while retrieving bitmap from " + url, e );
		}
		catch ( IllegalStateException e )
		{
			getRequest.abort();
			Log.w( MainActivity.appName, "Incorrect URL: " + url );
		}
		catch ( Exception e )
		{
			getRequest.abort();
			Log.w( MainActivity.appName, "Error while retrieving bitmap from " + url, e );
		}
		finally
		{
			// if ( (client instanceof AndroidHttpClient) )
			// {
			// ((AndroidHttpClient)client).close();
			// }
		}
		return null;
	}

	/*
	 * An InputStream that skips the exact number of bytes provided, unless it reaches EOF.
	 */
	static class FlushedInputStream extends FilterInputStream
	{
		public FlushedInputStream( InputStream inputStream )
		{
			super( inputStream );
		}

		@Override
		public long skip( long n ) throws IOException
		{
			long totalBytesSkipped = 0L;
			while ( totalBytesSkipped < n )
			{
				long bytesSkipped = in.skip( n - totalBytesSkipped );
				if ( bytesSkipped == 0L )
				{
					int b = read();
					if ( b < 0 )
					{
						break; // we reached EOF
					}
					else
					{
						bytesSkipped = 1; // we read one byte
					}
				}
				totalBytesSkipped += bytesSkipped;
			}
			return totalBytesSkipped;
		}
	}

	/**
	 * The actual AsyncTask that will asynchronously download the image.
	 */

	private final LinkedList<BitmapDownloaderTask> tasksQueued = new LinkedList<BitmapDownloaderTask>();
	private final HashSet<BitmapDownloaderTask> tasksRunning = new HashSet<BitmapDownloaderTask>();

	class BitmapDownloaderTask extends AsyncTask<String, Void, Bitmap>
	{
		private static final int MAX_THREADS = 10;

		private boolean queued = false;

		public void executeLIFO( String urlIn )
		{
			// System.out.println( "execute LIFO for '" + urlIn + "'" );

			synchronized ( tasksQueued )
			{
				url = urlIn;

				if ( !queued )
				{
					queued = true;
					// tasksQueued.addLast( this );
					tasksQueued.addFirst( this );
					// System.out.println( "queued in LIFO for '" + url + "' (" + tasksQueued.size() + ")" );
					executeNext();
				}
			}
		}

		public void addImageView( ImageView imageView )
		{
			imageViewReferences.add( new WeakElement<ImageView>( imageView ) );
			// Log.i( "TEST", "Task has " + imageViewReferences.size() + " images attached." );
		}

		public void removeImage( ImageView imageView )
		{
			imageViewReferences.remove( new WeakElement<ImageView>( imageView ) );
		}

		public void executeNext()
		{
			synchronized ( tasksQueued )
			{
				tasksRunning.remove( this );

				// System.out.println( "executeNext (" + tasksRunning.size() + "," + tasksQueued.isEmpty() + ")" );

				while ( tasksRunning.size() < MAX_THREADS && !tasksQueued.isEmpty() )
				{
					BitmapDownloaderTask nextTask = tasksQueued.removeFirst();

					// System.out.println( "queue now (" + tasksQueued.size() + ")" );

					if ( !nextTask.isCancelled() )
					{
						tasksRunning.add( nextTask );
						nextTask.execute( nextTask.url );
						// System.out.println( "executing task for '" + nextTask.url );
					}
				}
			}
		}

		private class WeakElement<T extends Object> extends WeakReference<T>
		{
			private final int hash; /*
									 * Hashcode of key, stored here since the key may be tossed by the GC
									 */

			private WeakElement( T o )
			{
				super( o );
				hash = o.hashCode();
			}

			/*
			 * A WeakElement is equal to another WeakElement iff they both refer to objects that are, in turn, equal according to their own equals methods
			 */
			@Override
			public boolean equals( Object o )
			{
				if ( this == o )
					return true;
				if ( !(o instanceof WeakElement) )
					return false;
				Object t = this.get();
				Object u = ((WeakElement<?>)o).get();
				if ( t == u )
					return true;
				if ( (t == null) || (u == null) )
					return false;
				return t.equals( u );
			}

			@Override
			public int hashCode()
			{
				return hash;
			}
		}

		private String url;
		private final HashSet<WeakElement<ImageView>> imageViewReferences;

		public BitmapDownloaderTask( ImageView imageView )
		{
			imageViewReferences = new HashSet<WeakElement<ImageView>>();
			addImageView( imageView );
		}

		/**
		 * Actual download method.
		 */
		@Override
		protected Bitmap doInBackground( String... params )
		{
			return downloadBitmap( url );
		}

		@Override
		protected void onCancelled()
		{
			synchronized ( tasksQueued )
			{
				// System.out.println( "Cancelled task, executing next" );
				executeNext();
			}
		}

		/**
		 * Once the image is downloaded, associates it to the imageView
		 */
		@Override
		protected void onPostExecute( Bitmap bitmap )
		{
			addBitmapToCache( url, bitmap );

			if ( isCancelled() )
			{
				bitmap = null;
			}

			synchronized ( tasksQueued )
			{
				for ( WeakReference<ImageView> imageViewReference : imageViewReferences )
				{
					if ( imageViewReference != null )
					{
						ImageView imageView = imageViewReference.get();
						BitmapDownloaderTask bitmapDownloaderTask = getBitmapDownloaderTask( imageView );
						// Change bitmap only if this process is still associated with it
						// Or if we don't use any bitmap to task association (NO_DOWNLOADED_DRAWABLE mode)
						if ( this == bitmapDownloaderTask )
						{
							Callback callback = getCallback( imageView );

							if ( bitmap == null )
							{
								if ( showDefault( imageView ) )
									imageView.setImageBitmap( defaultBitmap );

								if ( callback != null )
									callback.setImageError();
							}
							else
							{
								imageView.setImageBitmap( bitmap );

								if ( callback != null )
									callback.setImageOnDrawable();
							}
						}
					}
				}

				// System.out.println( "Done task, executing next" );
				executeNext();
			}
		}
	}

	/**
	 * A fake Drawable that will be attached to the imageView while the download is in progress.
	 * 
	 * <p>
	 * Contains a reference to the actual download task, so that a download task can be stopped if a new binding is required, and makes sure that only the last
	 * started download process can bind its result, independently of the download finish order.
	 * </p>
	 */
	static class DownloadedDrawable extends BitmapDrawable
	{
		private final WeakReference<BitmapDownloaderTask> bitmapDownloaderTaskReference;
		private final WeakReference<ImageDownloader.Callback> callbackReference;
		private final boolean showDefault;

		public DownloadedDrawable( BitmapDownloaderTask bitmapDownloaderTask, Resources resources, Bitmap bitmap, ImageDownloader.Callback callback,
				boolean showDefault )
		{
			super( resources, bitmap );
			bitmapDownloaderTaskReference = new WeakReference<BitmapDownloaderTask>( bitmapDownloaderTask );
			callbackReference = new WeakReference<ImageDownloader.Callback>( callback );
			this.showDefault = showDefault;
		}

		public BitmapDownloaderTask getBitmapDownloaderTask()
		{
			return bitmapDownloaderTaskReference.get();
		}

		public ImageDownloader.Callback getImageCallback()
		{
			return callbackReference.get();
		}

		public boolean showDefault()
		{
			return showDefault;
		}
	}

	/*
	 * Cache-related fields and methods.
	 * 
	 * We use a hard and a soft cache. A soft reference cache is too aggressively cleared by the Garbage Collector.
	 */

	private static final int DELAY_BEFORE_PURGE = 5 * 60 * 1000; // in milliseconds

	private final ConcurrentHashMap<String, Long> urlCache = new ConcurrentHashMap<String, Long>();

	private final Handler purgeHandler = new Handler();

	private final Runnable purger = new Runnable()
	{
		public void run()
		{
			clearCache();
		}
	};

	/**
	 * Adds this bitmap to the cache.
	 * 
	 * @param bitmap
	 *            The newly downloaded bitmap.
	 */
	private void addBitmapToCache( String url, Bitmap bitmap )
	{
		if ( bitmap != null )
		{
			synchronized ( sHardBitmapCache )
			{
				final long bitmapHash;

				if ( USE_CHECKSUM )
				{
					java.util.zip.Adler32 alder32 = new java.util.zip.Adler32();
					for ( int y = 0; y < bitmap.getHeight(); ++y )
						for ( int x = 0; x < bitmap.getWidth(); ++x )
							alder32.update( bitmap.getPixel( x, y ) );

					bitmapHash = alder32.getValue();
				}
				else
					bitmapHash = url.hashCode();

				// System.out.println( url + ": " + bitmapHash );

				urlCache.put( url, bitmapHash );

				// Move element to first position, so that it is removed last
				sHardBitmapCache.remove( bitmapHash );
				sHardBitmapCache.put( bitmapHash, bitmap );

				// System.out.println( "Cache Size:" + sHardBitmapCache.size() );
			}
		}
	}

	/**
	 * @param url
	 *            The URL of the image that will be retrieved from the cache.
	 * @return The cached bitmap or null if it was not found.
	 */
	private Bitmap getBitmapFromCache( String url )
	{
		Long bitmapHash = urlCache.get( url );
		if ( bitmapHash == null )
			return null;

		// First try the hard reference cache
		synchronized ( sHardBitmapCache )
		{
			final Bitmap bitmap = sHardBitmapCache.get( bitmapHash );
			if ( bitmap != null )
			{
				// Bitmap found in hard cache
				// Move element to first position, so that it is removed last
				sHardBitmapCache.remove( bitmapHash );
				sHardBitmapCache.put( bitmapHash, bitmap );
				return bitmap;
			}
		}

		// Then try the soft reference cache
		SoftReference<Bitmap> bitmapReference = sSoftBitmapCache.get( bitmapHash );
		if ( bitmapReference != null )
		{
			final Bitmap bitmap = bitmapReference.get();
			if ( bitmap != null )
			{
				// Bitmap found in soft cache
				return bitmap;
			}
			else
			{
				// Soft reference has been Garbage Collected
				sSoftBitmapCache.remove( bitmapHash );

				// We never need to clear the URL cache. It's small and has lots of good info.
				// urlCache.remove( url );
			}
		}

		return null;
	}

	/**
	 * Clears the image cache used internally to improve performance. Note that for memory efficiency reasons, the cache will automatically be cleared after a
	 * certain inactivity delay.
	 */
	public void clearCache()
	{
		// We never need to clear the URL cache. It's small and has lots of good info.
		// urlCache.remove( url );
		sHardBitmapCache.clear();
		sSoftBitmapCache.clear();
	}

	/**
	 * Allow a new delay before the automatic cache clear is done.
	 */
	private void resetPurgeTimer()
	{
		purgeHandler.removeCallbacks( purger );
		purgeHandler.postDelayed( purger, DELAY_BEFORE_PURGE );
	}
}
