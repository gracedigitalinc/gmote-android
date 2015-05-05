package com.plugplayer.recivaremote.upnp;

import org.cybergarage.upnp.Device;


import com.plugplayer.plugplayer.upnp.MediaDevice;
import com.plugplayer.plugplayer.upnp.PlugPlayerControlPoint;
import com.plugplayer.plugplayer.upnp.RecivaRenderer;
import com.plugplayer.plugplayer.upnp.Renderer;
import com.plugplayer.plugplayer.upnp.Server;
import com.plugplayer.plugplayer.upnp.UPNPServer;
import com.plugplayer.plugplayer.utils.StateMap;

public class RecivaControlPoint extends PlugPlayerControlPoint
{
	protected static DeviceFactory rendererFactory = new DeviceFactory()
	{
		public MediaDevice createDevice( Device dev )
		{
			MediaDevice device = null;

			device = RecivaRenderer.createDevice( dev );

			return device;
		}
	};
	
	protected static RendererStateFactory rendererStateFactory = new RendererStateFactory()
	{
		public Renderer rendererFromState( StateMap rendererState )
		{
			if ( rendererState.getName().equals( RecivaRenderer.STATENAME ) )
				return RecivaRenderer.createFromState( rendererState );

			return null;
		}
	};

	protected static ServerStateFactory serverStateFactory = new ServerStateFactory()
	{
		public Server serverFromState( StateMap serverState )
		{
			if ( serverState.getName().equals( UPNPServer.STATENAME ) )
				return UPNPServer.createFromState( serverState );

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
	
	protected static AndroidServerFactory androidServerFactory = new AndroidServerFactory()
	{
		public Server[] create()
		{
			return new Server[] { };
		}
	};

	public static void init()
	{
		PlugPlayerControlPoint.androidRendererFactory = androidRendererFactory;
		PlugPlayerControlPoint.rendererFactory = rendererFactory;
		PlugPlayerControlPoint.rendererStateFactory = rendererStateFactory;
		PlugPlayerControlPoint.androidServerFactory = androidServerFactory;
		PlugPlayerControlPoint.serverStateFactory = serverStateFactory;
	}
}
