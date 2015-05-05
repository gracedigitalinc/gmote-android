package com.plugplayer.plugplayer.upnp;

import java.util.ArrayList;
import java.util.List;

import android.widget.ImageView;

import com.plugplayer.plugplayer.R;

public abstract class Server extends MediaDevice
{
	public static interface BrowseResultsListener
	{
		void onMoreChildren( List<DirEntry> children, int total );

		void onDone();

		boolean loadMore();

		void onInitialChildren( ArrayList<DirEntry> copy, int totalCount );
	}

	@Override
	public void loadIcon( ImageView imageView )
	{
		if ( getIconURL() != null )
			super.loadIcon( imageView );
		else
			imageView.setImageResource( R.drawable.server );
	}

	public abstract void browseDir( Container parent );

	public abstract void browseDir( Container parent, int i );

	public abstract void browseDir( Container parent, BrowseResultsListener listener );

	public abstract boolean canSearch();

	public abstract void searchDir( Container parent, String query );

	public abstract void searchDir( Container parent, String query, BrowseResultsListener listener );
}
