package com.grace.remote.activities;

import android.util.Log;

import com.grace.remote.upnp.GraceControlPoint;

public class MainActivity extends com.plugplayer.recivaremote.activities.MainActivity
{
	static
	{
		Log.i(android.util.PlugPlayerUtil.DBG_TAG, "calling GraceControlPoint init()");
		GraceControlPoint.init();
	}
	
	@Override
	protected void licenseCheck()
	{
	}
	
	protected Class getRadiosActivityClass()
	{
		return GraceRadiosActivity.class;
	}
	
}
