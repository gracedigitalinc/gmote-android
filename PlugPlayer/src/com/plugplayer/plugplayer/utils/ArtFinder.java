package com.plugplayer.plugplayer.utils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Handler;
import android.widget.ImageView;

import com.example.android.imagedownloader.ImageDownloader;
import com.plugplayer.plugplayer.R;
import com.plugplayer.plugplayer.activities.MainActivity;
import com.plugplayer.plugplayer.upnp.Container;
import com.plugplayer.plugplayer.upnp.DirEntry;
import com.plugplayer.plugplayer.upnp.Server;

public class ArtFinder
{
	private static final Bitmap thumbplaceholder = BitmapFactory.decodeResource( MainActivity.me.getResources(), R.drawable.notethumb );

	static int taskCount = 0;

	static private LinkedList<ArtFinderTask> taskQueue = new LinkedList<ArtFinderTask>();

	private static final Handler queueHandler = new Handler();

	private static final Runnable queuer = new Runnable()
	{
		public void run()
		{
			while ( taskCount < 5 && !taskQueue.isEmpty() )
			{
				ArtFinderTask task = taskQueue.removeLast();
				if ( !task.isCancelled() )
					task.execute();
			}

			if ( !taskQueue.isEmpty() )
			{
				queueHandler.removeCallbacks( queuer );
				queueHandler.postDelayed( queuer, 500 );
			}
		}
	};

	public static void loadArt( Container c, Server s, ImageView imageView )
	{
		String urlString = c.getArtURL();
		if ( urlString != null )
		{
			cancelPotentialDownload( c, imageView );
			ImageDownloader.thumbnail.download( urlString == Container.NOART ? null : s.overrideBase( urlString ), imageView );
			return;
		}

		if ( cancelPotentialDownload( c, imageView ) )
		{
			ArtFinderTask task = new ArtFinderTask( imageView, s, c );
			DownloadedDrawable downloadedDrawable = new DownloadedDrawable( task, MainActivity.me.getResources(), thumbplaceholder );
			imageView.setImageDrawable( downloadedDrawable );

			if ( taskCount < 5 && taskQueue.isEmpty() )
			{
				task.execute();
			}
			else
			{
				// LIFO queued; so things we've already scrolled by go to the bottom and will probably be canceled already.
				taskQueue.addLast( task );
				queueHandler.removeCallbacks( queuer );
				queueHandler.postDelayed( queuer, 500 );

				// System.out.println( taskCount + " current art tasks." );
				// System.out.println( taskQueue.size() + " queued art tasks." );
			}
		}
	}

	private static class ArtFinderTask extends AsyncTask<Void, Void, String>
	{
		private final Server s;
		private final Container c;
		private final WeakReference<ImageView> imageViewReference;

		boolean dec = false;

		private void dec()
		{
			if ( dec )
			{
				taskCount--;

				// System.out.println( taskCount + " current art tasks." );
				// System.out.println( taskQueue.size() + " queued art tasks." );
			}

			dec = false;
		}

		public ArtFinderTask( ImageView imageView, Server s, Container c )
		{
			imageViewReference = new WeakReference<ImageView>( imageView );
			this.s = s;
			this.c = c;
		}

		@Override
		protected void onPreExecute()
		{
			dec = true;
			taskCount++;

			// System.out.println( taskCount + " current art tasks." );
			// System.out.println( taskQueue.size() + " queued art tasks." );
		}

		@Override
		protected String doInBackground( Void... v )
		{
			return findChildArtURL( s, c, true );
		}

		@Override
		protected void onPostExecute( String bitmapUrl )
		{
			dec();

			if ( imageViewReference != null )
			{
				ImageView imageView = imageViewReference.get();
				ArtFinderTask bitmapDownloaderTask = getArtFinderTask( imageView );

				if ( this == bitmapDownloaderTask )
				{
					ImageDownloader.thumbnail.download( bitmapUrl, imageView );
				}
			}
		}

		@Override
		protected void onCancelled()
		{
			dec();
		}
	}

	private static String findChildArtURL( Server s, Container parent, boolean fetch )
	{
		String urlString = parent.getArtURL();
		if ( urlString != null )
			return urlString;

		// Make sure there are at least 10 children in the parent
		if ( fetch && (parent.getTotalCount() < 0 || parent.getChildCount() < 10) )
			s.browseDir( parent, 10 );

		if ( parent.getChildCount() == 0 )
		{
			// parent.setArtURL( NOART );
			return null;
		}

		// This isn't needed because as we add children from a "browse", they'll set their parents art URL if needed.
		// for ( DirEntry child : parent.allChildren )
		// {
		// if ( child instanceof Item )
		// {
		// urlString = null;
		// if ( (urlString = ((Item)child).getArtURL()) != null )
		// {
		// parent.setArtURL( urlString );
		// return urlString;
		// }
		// }
		// }

		// Could have changed after loading children
		urlString = parent.getArtURL();
		if ( urlString != null )
			return urlString;

		// We only fetch from the server from the first container
		boolean first = true;

		// XXX Does this need to be synchronized?
		List<DirEntry> childrenCopy = new ArrayList<DirEntry>( parent.getChildren() );

		for ( DirEntry child : childrenCopy )
		{
			if ( child instanceof Container )
			{
				urlString = findChildArtURL( s, (Container)child, first );
				if ( urlString != null )
					return urlString;

				first = false;
			}
		}

		parent.setArtURL( Container.NOART );
		return null;
	}

	private static boolean cancelPotentialDownload( Container container, ImageView imageView )
	{
		ArtFinderTask artFinderTask = getArtFinderTask( imageView );

		if ( artFinderTask != null )
		{
			Container c = artFinderTask.c;
			if ( (c == null) || (!c.equals( container )) )
			{
				artFinderTask.cancel( true );
			}
			else
			{
				// The same container is already being searched.
				return false;
			}
		}
		return true;
	}

	private static ArtFinderTask getArtFinderTask( ImageView imageView )
	{
		if ( imageView != null )
		{
			Drawable drawable = imageView.getDrawable();
			if ( drawable instanceof DownloadedDrawable )
			{
				DownloadedDrawable downloadedDrawable = (DownloadedDrawable)drawable;
				return downloadedDrawable.getArtFinderTask();
			}
		}
		return null;
	}

	static class DownloadedDrawable extends BitmapDrawable
	{
		private final WeakReference<ArtFinderTask> artFinderTaskReference;

		public DownloadedDrawable( ArtFinderTask artFinderTask, Resources resources, Bitmap bitmap )
		{
			super( resources, bitmap );
			artFinderTaskReference = new WeakReference<ArtFinderTask>( artFinderTask );
		}

		public ArtFinderTask getArtFinderTask()
		{
			return artFinderTaskReference.get();
		}
	}
}
