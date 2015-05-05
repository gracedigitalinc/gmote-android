package com.plugplayer.plugplayer.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

import com.plugplayer.plugplayer.upnp.AndroidRenderer;
import com.plugplayer.plugplayer.upnp.PlugPlayerControlPoint;
import com.plugplayer.plugplayer.upnp.Renderer;
import com.plugplayer.plugplayer.upnp.Renderer.PlayState;

public class MediaReceiver extends BroadcastReceiver
{
	@Override
	public void onReceive( Context context, Intent intent )
	{
		if ( Intent.ACTION_MEDIA_BUTTON.equals( intent.getAction() ) )
		{
			KeyEvent event = (KeyEvent)intent.getParcelableExtra( Intent.EXTRA_KEY_EVENT );
			if ( event != null && event.getAction() == KeyEvent.ACTION_DOWN )
			{
				PlugPlayerControlPoint controlPoint = PlugPlayerControlPoint.getInstance();

				if ( controlPoint != null )
				{
					Renderer r = controlPoint.getCurrentRenderer();
					if ( r != null )
					{
						switch ( event.getKeyCode() )
						{
							// case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
							// case KeyEvent.KEYCODE_MEDIA_REWIND:

							case KeyEvent.KEYCODE_MEDIA_NEXT:
								if ( r.hasNext() )
									r.next();
								abortBroadcast();
								break;

							case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
								if ( r.getPlayState() == PlayState.Playing )
									r.pause();
								else
									r.play();
								abortBroadcast();
								break;

							case 126: // KeyEvent.KEYCODE_MEDIA_PLAY:
								if ( r.getPlayState() == PlayState.Paused )
									r.play();
								abortBroadcast();
								break;

							case 127: // KeyEvent.KEYCODE_MEDIA_PAUSE:
								if ( r.getPlayState() == PlayState.Playing )
									r.pause();
								abortBroadcast();
								break;

							case KeyEvent.KEYCODE_MEDIA_STOP:
								if ( r.getPlayState() == PlayState.Playing )
									r.pause();
								abortBroadcast();
								break;

							case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
								if ( r.hasPrev() )
									r.prev();
								abortBroadcast();
								break;

							case KeyEvent.KEYCODE_VOLUME_UP:
							case KeyEvent.KEYCODE_VOLUME_DOWN:
							{
								if ( r instanceof AndroidRenderer )
								{
									r.getVolume();
								}
							}
								break;

						}
					}
				}
			}
		}
	}
}
