package com.plugplayer.plugplayer.upnp;

public interface ControlPointListener
{
	void mediaServerChanged( Server newServer, Server oldServer );

	void mediaServerListChanged();

	void mediaRendererChanged( Renderer newRenderer, Renderer oldRenderer );

	void mediaRendererListChanged();

	void onErrorFromDevice( String error );
}
