package com.plugplayer.plugplayer.activities;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TabHost;

//XXX Fixes a bug in Android 2.1; remove this when we drop support
public class FixedTabHost extends TabHost
{
	public FixedTabHost( Context context )
	{
		super( context );
	}

	public FixedTabHost( Context context, AttributeSet attrs )
	{
		super( context, attrs );
	}

	@Override
	public void dispatchWindowFocusChanged( boolean hasFocus )
	{
		View v = getCurrentView();
		if ( v != null )
			super.dispatchWindowFocusChanged( hasFocus );
	}
}
