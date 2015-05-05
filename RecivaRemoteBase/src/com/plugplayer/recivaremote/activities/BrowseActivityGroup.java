package com.plugplayer.recivaremote.activities;

import android.app.Activity;
import android.content.Intent;

public class BrowseActivityGroup extends com.plugplayer.plugplayer.activities.BrowseActivityGroup
{
	@Override
	public boolean back()
	{		
		if ( history.size() > 2 )
		{
			return super.back();
		}
		else
		{
			return false;
		}
	}
	
	@Override
	protected Intent newBrowseIntent( Activity parent )
	{
		Intent intent = new Intent( parent, BrowseActivity.class );
		return intent;
	}
}
