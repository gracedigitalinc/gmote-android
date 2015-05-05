package com.plugplayer.recivaremote.activities;

import android.app.Activity;
import android.os.Bundle;
//mit fix crash 1: for java.lang.NoSuchMethodError: com.plugplayer.recivaremote.activities.SleeperTimerActivity.setFinishOnTouchOutside
//mit fix :created this class
public class SleeperTimerActivity extends Activity{

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
	}

	public void setFinishOnTouchOutside(boolean finish) {
		// TODO Auto-generated method stub

	}

}
