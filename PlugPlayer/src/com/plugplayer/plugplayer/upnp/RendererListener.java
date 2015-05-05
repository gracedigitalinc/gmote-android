package com.plugplayer.plugplayer.upnp;

public interface RendererListener extends MediaDeviceListener
{
	public void playStateChanged( Renderer.PlayState state );

	public void playModeChanged( Renderer.PlayMode mode );

	public void playlistChanged();

	public void volumeChanged( float volume );

	public void trackNumberChanged( int trackNumber );

	public void timeStampChanged( int seconds );
}
