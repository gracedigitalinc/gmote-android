package com.plugplayer.plugplayer.upnp;

import java.util.List;

public interface LinnRenderer
{
	boolean getStandby();

	void setStandby( boolean isChecked );

	List<String> getSourceNames();

	int getSourceIndex();

	void setSourceIndex( int newIndex );

	boolean hasProductService();

	String getOtherUDN();

	boolean hasReceiverService();

	boolean hasSenderService();

	Renderer getSongcastSender();

	void setSongcastSender( Renderer sender );
}
