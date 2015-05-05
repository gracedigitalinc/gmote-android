package com.plugplayer.plugplayer.activities;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.TabWidget;

public class FixedTabWidget extends TabWidget
{
	public FixedTabWidget( Context context )
	{
		super( context );
	}

	public FixedTabWidget( Context context, AttributeSet attrs )
	{
		super( context, attrs );
	}

	public FixedTabWidget( Context context, AttributeSet attrs, int defStyle )
	{
		super( context, attrs, defStyle );
	}

	@Override
	public void dispatchDraw( Canvas canvas )
	{
		if ( getTabCount() != 0 )
			super.dispatchDraw( canvas );
	}
}
