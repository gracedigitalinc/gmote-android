package com.grace.remote.upnp;

import org.cybergarage.upnp.Device;

import android.util.Log;

import com.plugplayer.plugplayer.upnp.MediaDevice;
import com.plugplayer.plugplayer.upnp.PlugPlayerControlPoint;
import com.plugplayer.plugplayer.upnp.Renderer;
import com.plugplayer.plugplayer.utils.StateMap;

public class GraceControlPoint extends PlugPlayerControlPoint
{
	protected static DeviceFactory rendererFactory = new DeviceFactory()
	{
		public MediaDevice createDevice( Device dev )
		{
			
			MediaDevice device = null;

			device = GraceRenderer.createDevice( dev );

			return device;
		}
	};
	
	protected static RendererStateFactory rendererStateFactory = new RendererStateFactory()
	{
		public Renderer rendererFromState( StateMap rendererState )
		{
			if ( rendererState.getName().equals( GraceRenderer.STATENAME ) )
				return GraceRenderer.createFromState( rendererState );

			return null;
		}
	};

	protected static AndroidRendererFactory androidRendererFactory = new AndroidRendererFactory()
	{
		public Renderer create()
		{
			return null;
		}
	};

	public static void init()
	{
		Log.i(android.util.PlugPlayerUtil.DBG_TAG, "init of graceControlPoint");
		PlugPlayerControlPoint.androidRendererFactory = androidRendererFactory;
		PlugPlayerControlPoint.rendererFactory = rendererFactory;
		PlugPlayerControlPoint.rendererStateFactory = rendererStateFactory;
	}
}
