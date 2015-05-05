package com.plugplayer.plugplayer.activities;

import java.io.File;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Handler;
import android.view.Window;

import com.plugplayer.plugplayer.upnp.PlugPlayerControlPoint;

public interface MainActivityInterface
{
	SharedPreferences getSharedPreferences( String a, int b );

	File getFilesDir();

	AssetManager getAssets();

	ContentResolver getContentResolver();

	Object getSystemService( String name );

	void startActivity( Intent intent );

	Handler getHandler();

	PlugPlayerControlPoint getControlPoint();

	Resources getResources();

	File getCacheDir();

	void quit();

	boolean onSearchRequested();

	void setTab( int i );

	Window getWindow();

	Class<?> getSlideShowClass();

	Class<?> getVideoViewerClass();
}
