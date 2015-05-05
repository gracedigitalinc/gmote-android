/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.plugplayer.plugplayer.activities;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;

import com.plugplayer.plugplayer.R;

/**
 * Displays an EULA ("End User License Agreement") that the user has to accept before using the application. Your application should call
 * {@link Eula#show(android.app.Activity)} in the onCreate() method of the first activity. If the user accepts the EULA, it will never be shown again. If the
 * user refuses, {@link android.app.Activity#finish()} is invoked on your activity.
 */
public class Eula
{
	private static final String ASSET_EULA = "EULA.txt";
	private static final String PREFERENCE_EULA_ACCEPTED = "eula.accepted";
	private static final String PREFERENCES_EULA = "eula";

	/**
	 * callback to let the activity know when the user has accepted the EULA.
	 */
	static interface OnEulaAgreedTo
	{

		/**
		 * Called when the user has accepted the eula and the dialog closes.
		 */
		void onEulaAgreedTo();
	}

	/**
	 * Displays the EULA if necessary. This method should be called from the onCreate() method of your main Activity.
	 * 
	 * @param activity
	 *            The Activity to finish if the user rejects the EULA.
	 * @return Whether the user has agreed already.
	 */
	
	public static boolean show( final Context activity )
	{
		return show( activity, ASSET_EULA, R.string.eula_title, false, false );
	}

	public static boolean show( final Context activity, boolean closeOnAccept )
	{
		return show( activity, ASSET_EULA, R.string.eula_title, false, closeOnAccept );
	}

	public static boolean show(final Context activity, String assetName, int titleResource, boolean force, final boolean closeOnAccept )
	{
		final SharedPreferences preferences = activity.getSharedPreferences( PREFERENCES_EULA, Activity.MODE_PRIVATE );

		// For testing
		// preferences.edit().putBoolean( PREFERENCE_EULA_ACCEPTED, false ).commit();

		if ( force || !preferences.getBoolean( PREFERENCE_EULA_ACCEPTED, false ) )
		{
			final AlertDialog.Builder builder = new AlertDialog.Builder( activity );
			builder.setTitle( titleResource );
			builder.setPositiveButton( R.string.eula_accept, new DialogInterface.OnClickListener()
			{
				public void onClick( DialogInterface dialog, int which )
				{
					accept( activity, preferences, closeOnAccept );
					if ( activity instanceof OnEulaAgreedTo )
					{
						((OnEulaAgreedTo)activity).onEulaAgreedTo();
					}
				}
			} );
			if ( !force )
			{
				builder.setCancelable( true );
				builder.setNegativeButton( R.string.eula_refuse, new DialogInterface.OnClickListener()
				{
					public void onClick( DialogInterface dialog, int which )
					{
						refuse( activity );
					}
				} );
				builder.setOnCancelListener( new DialogInterface.OnCancelListener()
				{
					public void onCancel( DialogInterface dialog )
					{
						refuse( activity );
					}
				} );
			}
			builder.setMessage( readEula( assetName, activity ) );
			builder.create().show();
			return false;
		}
		return true;
	}

	private static void accept( Context activity, SharedPreferences preferences, boolean closeOnAccept )
	{
		preferences.edit().putBoolean( PREFERENCE_EULA_ACCEPTED, true ).commit();
		
		if ( closeOnAccept && activity instanceof Activity )
			((Activity)activity).finish();
	}

	private static void refuse( Context activity )
	{
		if ( activity instanceof Activity )
			((Activity)activity).finish();
		else
			((Service)activity).stopSelf();
	}

	private static CharSequence readEula( String assetName, Context activity )
	{
		BufferedReader in = null;
		try
		{
			in = new BufferedReader( new InputStreamReader( activity.getAssets().open( assetName ) ) );
			String line;
			StringBuilder buffer = new StringBuilder();
			while ( (line = in.readLine()) != null )
				buffer.append( line ).append( '\n' );
			return buffer;
		}
		catch ( IOException e )
		{
			return "";
		}
		finally
		{
			closeStream( in );
		}
	}

	/**
	 * Closes the specified stream.
	 * 
	 * @param stream
	 *            The stream to close.
	 */
	private static void closeStream( Closeable stream )
	{
		if ( stream != null )
		{
			try
			{
				stream.close();
			}
			catch ( IOException e )
			{
				// Ignore
			}
		}
	}
}