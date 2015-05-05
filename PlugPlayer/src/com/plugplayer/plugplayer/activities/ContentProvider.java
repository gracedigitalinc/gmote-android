package com.plugplayer.plugplayer.activities;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.plugplayer.plugplayer.upnp.Item;
import com.plugplayer.plugplayer.upnp.PlugPlayerControlPoint;
import com.plugplayer.plugplayer.upnp.Renderer;

public class ContentProvider extends android.content.ContentProvider
{
	public static final String PROVIDER_NAME = "com.plugplayer.plugplayer";

	public static final Uri CONTENT_URI = Uri.parse( "content://" + PROVIDER_NAME + "/" );

	private static final int PLAYLIST_ITEM_ID = 1;

	private static final UriMatcher uriMatcher;
	static
	{
		uriMatcher = new UriMatcher( UriMatcher.NO_MATCH );
		// uriMatcher.addURI( PROVIDER_NAME, "/#/*", ITEM_ID );
		uriMatcher.addURI( PROVIDER_NAME, "playlist/#/#", PLAYLIST_ITEM_ID );
	}

	private Item getItem( Uri uri )
	{
		if ( uriMatcher.match( uri ) == PLAYLIST_ITEM_ID )
		{
			List<String> pathsegs = uri.getPathSegments();
			int renderernum = Integer.parseInt( pathsegs.get( 1 ) );
			int itemnum = Integer.parseInt( pathsegs.get( 2 ) );

			// System.out.println( "Got item " + renderernum + "/" + itemnum );

			Renderer r = PlugPlayerControlPoint.getInstance().getRendererList().get( renderernum );
			Item i = r.getPlaylistEntry( itemnum );

			// System.out.println( "Returning item " + i );

			return i;
		}

		return null;
	}

	@Override
	public String getType( Uri uri )
	{
		System.out.println( "Got get type: " + uri );
		return null;
	}

	@Override
	public ParcelFileDescriptor openFile( Uri uri, String mode ) throws FileNotFoundException
	{
		System.out.println( "Got open: " + uri );

		Item item = getItem( uri );
		File file = item.copyToTmpFile();
		return ParcelFileDescriptor.open( file, ParcelFileDescriptor.MODE_READ_ONLY );
	}

	@Override
	public Cursor query( Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder )
	{
		System.out.println( "Got query: " + uri );
		return null;
	}

	@Override
	public int delete( Uri arg0, String arg1, String[] arg2 )
	{
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Uri insert( Uri uri, ContentValues values )
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean onCreate()
	{
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public int update( Uri uri, ContentValues values, String selection, String[] selectionArgs )
	{
		// TODO Auto-generated method stub
		return 0;
	}
}
