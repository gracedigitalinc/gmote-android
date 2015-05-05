package com.grace.remote.upnp;

import org.cybergarage.upnp.Device;

import android.util.Log;

import com.plugplayer.plugplayer.upnp.RecivaRenderer;

public class GraceRenderer extends RecivaRenderer
{
//	private static final String models[] = { "557", "556", "547", "548", "569", "567", "570", "568", "592", "708", "877", "709", "762", "842", "680", "627", "623", "789", "648", "957", "728", "649", "681", "671", "621", "591", "608", "592", "618", "617", "594", "511", "560", "663", "514" };
//
//	private static boolean matchModel( String modelNumber )
//	{
//		if ( modelNumber == null || modelNumber.length() == 0 )
//			return false;
//		
//		for( String model : models )
//			if ( model.equals( modelNumber ) )
//				return true;
//		
//		return false;	
//	}
	
	public static RecivaRenderer createDevice( Device dev )
	{
//		if ( !matchModel( dev.getModelNumber() ) )
//			return null;
		Log.i(android.util.PlugPlayerUtil.DBG_TAG, "creating Device = "+dev.getModelNumber());
		return RecivaRenderer.createDevice(dev);
	}
}
