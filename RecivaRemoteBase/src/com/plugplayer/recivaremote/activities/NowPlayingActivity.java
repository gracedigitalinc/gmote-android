package com.plugplayer.recivaremote.activities;

import org.cybergarage.upnp.UPnP;
import org.cybergarage.xml.Node;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.imagedownloader.ImageDownloader;
import com.plugplayer.plugplayer.upnp.ControlPointListener;
import com.plugplayer.plugplayer.upnp.PlugPlayerControlPoint;
import com.plugplayer.plugplayer.upnp.RecivaRenderer;
import com.plugplayer.plugplayer.upnp.RecivaRenderer.RecivaRendererListener;
import com.plugplayer.plugplayer.upnp.Renderer;
import com.plugplayer.plugplayer.upnp.Renderer.PlayMode;
import com.plugplayer.plugplayer.upnp.Renderer.PlayState;
import com.plugplayer.plugplayer.upnp.Server;
import com.plugplayer.recivaremote.R;

public class NowPlayingActivity extends Activity implements ControlPointListener, RecivaRendererListener
{
	private final Handler handler = new Handler();

	TextView titleText;
	ClickableSpan clickableSpan;
	ImageButton powerButton;
	TextView instructions;
	ImageView art;
	TextView info;
	LinearLayout buttons;
	ProgressBar progress;
	ImageButton muteButton;
	ImageButton downButton;
	ImageButton upButton;
	ProgressBar stationprogress;
	ImageButton control1;
	ImageButton control2;
	ImageButton control3;
	ImageButton control4;
	private boolean pandora = false;
	private static NowPlayingActivity NowPlayingActivityReference;
	Thread radioSearchTimeOutThread = null;
	@Override
	public void onCreate( Bundle savedInstanceState )
	{
		
		super.onCreate( savedInstanceState );
		NowPlayingActivityReference = this;
		setContentView( R.layout.recivanowplaying );
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "onCreate() of NowPlayingActivit: RecivaRemoteBase" );
		titleText = (TextView)findViewById( R.id.title );
		clickableSpan = new ClickableSpan() {
		    @Override
		    public void onClick(View textView) {
		    	Intent intent = new Intent( NowPlayingActivity.this, com.plugplayer.recivaremote.activities.GraceFAQActivity.class );
				startActivity( intent );
		    }
		};
/*not required when clickablespan exist
		titleText.setOnTouchListener(new OnTouchListener() {
			
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				// TODO Auto-generated method stub
				Log.i(android.util.PlugPlayerUtil.DBG_TAG+"1", "event = "+event);
				if(event.getAction()==MotionEvent.ACTION_DOWN){
					Intent intent = new Intent( NowPlayingActivity.this, com.plugplayer.recivaremote.activities.GraceFAQActivity.class );
					startActivity( intent );
					return true;
				}
				return false;
				
			}
		});*/
		powerButton = (ImageButton)findViewById( R.id.power );
		powerButton.setOnClickListener( new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				Log.i( android.util.PlugPlayerUtil.DBG_TAG, "powerButton onClick() of NowPlayingActivity: RecivaRemoteBase" );
				PlugPlayerControlPoint controlPoint = PlugPlayerControlPoint.getInstance();
				RecivaRenderer r = (RecivaRenderer)controlPoint.getCurrentRenderer();
				if ( r != null )
					r.setPowerState( !r.isPowerOn() );
			}
		});
		
		instructions = (TextView)findViewById( R.id.instructions );
		art = (ImageView)findViewById( R.id.art );
		info = (TextView)findViewById( R.id.info );
		buttons = (LinearLayout)findViewById( R.id.buttons );
		progress = (ProgressBar)findViewById( R.id.progress );
		muteButton = (ImageButton)findViewById( R.id.volumemute );
		muteButton.setOnClickListener( new OnClickListener()
		{
			Runnable blinkerOn = new Runnable()
			{
				@Override
				public void run()
				{
					Log.i( android.util.PlugPlayerUtil.DBG_TAG, "muteButton blink ON onClickListener() of NowPlayingActivity: RecivaRemoteBase" );
					muteButton.setImageResource( R.drawable.button_volumemute );
					PlugPlayerControlPoint controlPoint = PlugPlayerControlPoint.getInstance();
					RecivaRenderer r = (RecivaRenderer)controlPoint.getCurrentRenderer();
					if ( r != null && r.isMute() )
						handler.postDelayed( blinkerOff, 500 );
				}
			};

			Runnable blinkerOff = new Runnable()
			{
				@Override
				public void run()
				{
					Log.i( android.util.PlugPlayerUtil.DBG_TAG, "muteButton blink OFF onClickListener() of NowPlayingActivity: RecivaRemoteBase" );
					PlugPlayerControlPoint controlPoint = PlugPlayerControlPoint.getInstance();
					RecivaRenderer r = (RecivaRenderer)controlPoint.getCurrentRenderer();
					if ( r != null && r.isMute() )
					{
						muteButton.setImageResource( 0 );
						handler.postDelayed( blinkerOn, 500 );
					}
				}
			};

			@Override
			public void onClick(View v)
			{
				Log.i( android.util.PlugPlayerUtil.DBG_TAG, "muteButton onClick() of NowPlayingActivity: RecivaRemoteBase" );
				PlugPlayerControlPoint controlPoint = PlugPlayerControlPoint.getInstance();
				RecivaRenderer r = (RecivaRenderer)controlPoint.getCurrentRenderer();
				if ( r != null )
				{
					r.toggleMute();
					
					if ( r.isMute() )
					{
						handler.postAtTime( blinkerOff, 100 );
					}
				}
			}
		});

		downButton = (ImageButton)findViewById( R.id.volumedown );
		downButton.setOnClickListener( new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				Log.i( android.util.PlugPlayerUtil.DBG_TAG, "downButton onClick() of NowPlayingActivity: RecivaRemoteBase" );
				PlugPlayerControlPoint controlPoint = PlugPlayerControlPoint.getInstance();
				RecivaRenderer r = (RecivaRenderer)controlPoint.getCurrentRenderer();
				if ( r != null )
					r.volumeDec();
			}
		});

		upButton = (ImageButton)findViewById( R.id.volumeup );
		upButton.setOnClickListener( new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				Log.i( android.util.PlugPlayerUtil.DBG_TAG, "upButton onClick() of NowPlayingActivity: RecivaRemoteBase" );
				PlugPlayerControlPoint controlPoint = PlugPlayerControlPoint.getInstance();
				RecivaRenderer r = (RecivaRenderer)controlPoint.getCurrentRenderer();
				if ( r != null )
					r.volumeInc();
			}
		});

		stationprogress = (ProgressBar)findViewById( R.id.stationprogress );
		control1 = (ImageButton)findViewById( R.id.control1 );
		control1.setOnClickListener( new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				Log.i( android.util.PlugPlayerUtil.DBG_TAG, "control1 onClick() of NowPlayingActivity: RecivaRemoteBase" );
				PlugPlayerControlPoint controlPoint = PlugPlayerControlPoint.getInstance();
				final RecivaRenderer r = (RecivaRenderer)controlPoint.getCurrentRenderer();

				if ( pandora )
				{
					r.pandoraThumbsDown();
					control1.setImageResource(R.drawable.button_thumbs_dn_active);
				}
			}
		});
	
		control2 = (ImageButton)findViewById( R.id.control2 );
		control2.setOnClickListener( new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				Log.i( android.util.PlugPlayerUtil.DBG_TAG, "control2 onClick() of NowPlayingActivity: RecivaRemoteBase" );
				PlugPlayerControlPoint controlPoint = PlugPlayerControlPoint.getInstance();
				final RecivaRenderer r = (RecivaRenderer)controlPoint.getCurrentRenderer();

				if ( pandora )
				{
					r.pandoraThumbsUp();
					control2.setImageResource(R.drawable.button_thumbs_up_active);
				}
				else
					r.prev();
			}
		});

		control3 = (ImageButton)findViewById( R.id.control3 );
		control3.setOnClickListener( new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				Log.i( android.util.PlugPlayerUtil.DBG_TAG, "control3 onClick() of NowPlayingActivity: RecivaRemoteBase" );
				PlugPlayerControlPoint controlPoint = PlugPlayerControlPoint.getInstance();
				final RecivaRenderer r = (RecivaRenderer)controlPoint.getCurrentRenderer();

				if ( r.getPlayState() == PlayState.Playing )
				{
					r.pause();
					control3.setImageResource( R.drawable.button_play );
				}
				else
				{
					r.play();
					control3.setImageResource( R.drawable.button_pause );
				}
			}
		});

		
		control4 = (ImageButton)findViewById( R.id.control4 );
		control4.setOnClickListener( new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				Log.i( android.util.PlugPlayerUtil.DBG_TAG, "control4 onClick() of NowPlayingActivity: RecivaRemoteBase" );
				PlugPlayerControlPoint controlPoint = PlugPlayerControlPoint.getInstance();
				final RecivaRenderer r = (RecivaRenderer)controlPoint.getCurrentRenderer();

				if ( pandora )
					r.pandoraNext();
				else
					r.next();
			}
		});
		
		PlugPlayerControlPoint controlPoint = PlugPlayerControlPoint.getInstance();
		controlPoint.addControlPointListener( this );
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "calling mediaRendererChanged()from onCreate (old = null)of NowPlayingActivity: RecivaRemoteBase" );
		mediaRendererChanged( controlPoint.getCurrentRenderer(), null );
	}

	private void updatePowerButton( RecivaRenderer renderer )
	{
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "updatePowerButton() of NowPlayingActivity: RecivaRemoteBase" );
		if ( renderer == null ){
			Log.i( android.util.PlugPlayerUtil.DBG_TAG, "updatePowerButton() rendrer = null, return of NowPlayingActivity: RecivaRemoteBase" );
			return;
		}
		
		if ( renderer.isPowerOn() )
		{
			Log.i( android.util.PlugPlayerUtil.DBG_TAG, "updatePowerButton() PowerOn, NowPlayingActivity: RecivaRemoteBase" );
			powerButton.setImageResource( R.drawable.greenpower );
			powerButton.setVisibility( View.VISIBLE );
			instructions.setVisibility( View.GONE );
			art.setVisibility( View.VISIBLE );
			info.setVisibility( View.VISIBLE );
			buttons.setVisibility( View.VISIBLE );
		}
		else
		{
			Log.i( android.util.PlugPlayerUtil.DBG_TAG, "updatePowerButton() PowerOff, NowPlayingActivity: RecivaRemoteBase" );
			powerButton.setImageResource( R.drawable.redpower );
			powerButton.setVisibility( View.VISIBLE );
			instructions.setVisibility( View.VISIBLE );
			art.setVisibility( View.GONE );
			info.setVisibility( View.GONE );
			buttons.setVisibility( View.GONE );
			((LinearLayout)control1.getParent()).setVisibility( View.GONE );
			((LinearLayout)control2.getParent()).setVisibility( View.GONE );
			((LinearLayout)control3.getParent()).setVisibility( View.GONE );
			((LinearLayout)control4.getParent()).setVisibility( View.GONE );
		}
	}
	
	/*
<reciva><playback-details><state>Connecting</state>
<station id="14042" custommenuid="0"><logo>http://977music.com/images/logo.jpg</logo>
</station>
<playlist-entry></playlist-entry>
<stream id="b571bde547755bffad059fc3be8e43df"><url>http://scfire-ntc-aa02.stream.aol.com:80/stream/1040</url>
<title>.977 The '80s Channel</title>
<album-art-url>http://977music.com/images/logo.jpg</album-art-url>
</stream>
</playback-details>
</reciva>
	 */
	private void updateStationInfo( String playbackXMLString )
	{
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "updateStationInfo(), return of NowPlayingActivity: RecivaRemoteBase" );
		try
		{
			info.setText( "" );
			
			if ( playbackXMLString == null || playbackXMLString.length() == 0 )
				return;	

			PlugPlayerControlPoint controlPoint = PlugPlayerControlPoint.getInstance();
			final RecivaRenderer r = (RecivaRenderer)controlPoint.getCurrentRenderer();
			boolean power = r.isPowerOn();
			if ( !power )
				return;

			
			Node playbackDoc = UPnP.getXMLParser().parse(playbackXMLString);
			playbackDoc = playbackDoc.getNode("playback-details");			
			if ( playbackDoc == null )
				return;

			Node stationNode = playbackDoc.getNode("station");
		
			Node streamNode = playbackDoc.getNode("stream");
			if ( streamNode == null )
				return;
			
			StringBuffer infoText = new StringBuffer();
			String title = streamNode.getNodeValue("title");
			String artist = "";
			String album = "";

			Node entryNode = playbackDoc.getNode("playlist-entry");
			if (entryNode != null)
			{
				String entryTitle = entryNode.getNodeValue("title");
				if (entryTitle != null && entryTitle.length() > 0) title = entryTitle; // prefer entry title over stream title
				artist = entryNode.getNodeValue("artist");
				album = entryNode.getNodeValue("album");
			}
			
			if (artist == null || artist.length() == 0)
			{
				artist = streamNode.getNodeValue("metadata"); //sirius puts extra info here.
			}

			infoText.append(title);
			if(artist != null && artist.length() > 0)infoText.append ("\n" + artist);
			if(album != null && album.length() > 0)infoText.append ("\n" + album);
			info.setText(infoText ); 
						
			boolean stopped = false;
			boolean connecting = false;
			String state = playbackDoc.getNodeValue( "state" );
			stopped = state.equals( "Stopped" );
			connecting = state.equals( "Connecting" );
			
			if (connecting)
				stationprogress.setVisibility( View.VISIBLE );
			else
				stationprogress.setVisibility( View.GONE );
			
			String artURL = null;
			
			String streamType = streamNode.getAttributeValue( "type" );
			if ( streamType.equals( "MediaStream" ) )
			{
				pandora = false;
				((LinearLayout)control1.getParent()).setVisibility( View.INVISIBLE );
				((LinearLayout)control2.getParent()).setVisibility( View.VISIBLE );
				((LinearLayout)control3.getParent()).setVisibility( View.VISIBLE );
				((LinearLayout)control4.getParent()).setVisibility( View.VISIBLE );
				control2.setImageResource( R.drawable.button_prev );
				if ( r.getPlayState() == PlayState.Playing )
					control3.setImageResource( R.drawable.button_pause );
				else
					control3.setImageResource( R.drawable.button_play );
				
				if ( entryNode != null )
				{
					artURL = entryNode.getNodeValue("album-art-url" );
				}

			}
			else if ( playbackXMLString.contains("PANDORA" ) )
			{
				pandora = true;
				((LinearLayout)control1.getParent()).setVisibility( View.VISIBLE );
				((LinearLayout)control2.getParent()).setVisibility( View.VISIBLE );
				((LinearLayout)control3.getParent()).setVisibility( View.VISIBLE );
				((LinearLayout)control4.getParent()).setVisibility( View.VISIBLE );
				control1.setImageResource(R.drawable.button_thumbs_dn);
				
				if (entryNode != null && entryNode.getNodeValue("rating").equals("1"))
				{
					control2.setImageResource( R.drawable.button_thumbs_up_active);
				}
				else if (entryNode != null && entryNode.getNodeValue("rating").equals("0"))
				{
					control2.setImageResource( R.drawable.button_thumbs_up );
				}
				else
				{
					control2.setImageResource( R.drawable.button_thumbs_up );
				}
				if ( r.getPlayState() == PlayState.Playing )
					control3.setImageResource( R.drawable.button_pause );
				else
					control3.setImageResource( R.drawable.button_play );
			}
			else
			{
				((LinearLayout)control1.getParent()).setVisibility( View.GONE );
				((LinearLayout)control2.getParent()).setVisibility( View.GONE );
				((LinearLayout)control3.getParent()).setVisibility( View.GONE );
				((LinearLayout)control4.getParent()).setVisibility( View.GONE );
			}
			
			if( !stopped )
			{
				if ( artURL == null || artURL.length() == 0 )
					artURL = streamNode.getNodeValue("album-art-url" );

				if ( (artURL == null || artURL.length() == 0) && stationNode != null)
					artURL = stationNode.getNodeValue( "logo" );
				
				ImageDownloader.fullsize.download( artURL, art );
			}
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}
	}
	boolean foundRadio = false;
	@Override
	public void mediaRendererChanged( final Renderer newRenderer, final Renderer oldRenderer)
	{
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "inside mediaRendererChanged(), NowPlayingActivity: RecivaRemoteBase" );
		
		handler.post( new Runnable()
		{
			public void run()
			{
				RecivaRenderer oldOne = (RecivaRenderer)oldRenderer;
				RecivaRenderer newOne = (RecivaRenderer)newRenderer;
				
				stationprogress.setVisibility( View.GONE );
				((LinearLayout)control1.getParent()).setVisibility( View.GONE );
				((LinearLayout)control2.getParent()).setVisibility( View.GONE );
				((LinearLayout)control3.getParent()).setVisibility( View.GONE );
				if(radioSearchTimeOutThread == null || radioSearchTimeOutThread.getState() == Thread.State.TERMINATED){
					radioSearchTimeOutThread = new Thread(new Runnable() {

						@Override
						public void run() {
							while(!foundRadio){
								try {
									Thread.sleep(5000);
								} catch (InterruptedException e) {
									
									e.printStackTrace();
								}
								RecivaRenderer newOne_t = (RecivaRenderer)newRenderer;
								//for ( Renderer r : PlugPlayerControlPoint.getInstance().getRendererList() )
								if(com.plugplayer.recivaremote.activities.RadiosActivity.radioActivityReference!=null){
									Log.i( android.util.PlugPlayerUtil.DBG_TAG, "radioActivityReference is not null");
									
									for(int i=0;i<com.plugplayer.recivaremote.activities.RadiosActivity.radioActivityReference.getRadioListSize();i++){
										Log.i( android.util.PlugPlayerUtil.DBG_TAG, "RadioList i="+i+" title = "+com.plugplayer.recivaremote.activities.RadiosActivity.radioActivityReference.getListView().getItemIdAtPosition(i));
									}
								}else{
									Log.i( android.util.PlugPlayerUtil.DBG_TAG, "radioActivityReference is null");
								}
								if((newOne_t == null 
										&& (com.plugplayer.recivaremote.activities.RadiosActivity.radioActivityReference==null||com.plugplayer.recivaremote.activities.RadiosActivity.radioActivityReference.getRadioListSize() <= 1)) 
										&& NowPlayingActivityReference.hasWindowFocus()){
									NowPlayingActivityReference.runOnUiThread(new Runnable() {
										  public void run() {
											  Toast.makeText(NowPlayingActivityReference.getBaseContext(), "No Radios found!! will continue searching", Toast.LENGTH_SHORT).show();
										  }
										});
									Log.i( android.util.PlugPlayerUtil.DBG_TAG, "No Radios found!! will continue searching, NowPlayingActivity: RecivaRemoteBase" );
									
								}else{
									break;
								}
							}
						}
					});
				}
				if ( oldOne != null )
					oldOne.removeRecivaRendererListener( NowPlayingActivity.this );
				
				if ( newOne != null )
				{
					foundRadio = true; radioSearchTimeOutThread=null;
					Log.i( android.util.PlugPlayerUtil.DBG_TAG, "mediaRendererChanged() newOne!=null, NowPlayingActivity: RecivaRemoteBase" );
					newOne.addRecivaRendererListener( NowPlayingActivity.this );
					
					titleText.setText( newOne.getName() );
					titleText.setSelected(false);
					titleText.setSingleLine(true);
					progress.setVisibility( View.GONE );
					updatePowerButton( newOne );
					updateStationInfo( newOne.lastPlaybackXMLString() );
					//playStateChanged( newOne.getPlayState() );
				}
				else
				{
					Log.i( android.util.PlugPlayerUtil.DBG_TAG, "mediaRendererChanged() newOne==null, NowPlayingActivity: RecivaRemoteBase, thread state =" +radioSearchTimeOutThread.getState());
					foundRadio=false;
					
					if(radioSearchTimeOutThread.getState() == Thread.State.NEW){
						radioSearchTimeOutThread.start();
					}
					
					
					SpannableString ss = new SpannableString("Searching for Radios\nTroubleshooting guide");
					ss.setSpan(clickableSpan, 21, 42, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
					titleText.setSingleLine(false);
					//titleText.setText( "Searching for Radios\nClick me for Grace Remote app FAQ's" );
					titleText.setText( ss );
					titleText.setMovementMethod(LinkMovementMethod.getInstance());
					//titleText.setSelected(true);
					progress.setVisibility( View.VISIBLE );
					powerButton.setVisibility( View.GONE );
					instructions.setVisibility( View.GONE );
					art.setVisibility( View.GONE );
					info.setVisibility( View.GONE );
					buttons.setVisibility( View.GONE );
				}
			}
		} );
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		PlugPlayerControlPoint controlPoint = PlugPlayerControlPoint.getInstance();
		mediaRendererChanged(controlPoint.getCurrentRenderer(), null);
	}

	@Override
	public void mediaRendererListChanged()
	{
		Log.i(android.util.PlugPlayerUtil.DBG_TAG, "inside mediaRendererListChanged, no implementation:NowPlayingActivity");
	}

	@Override
	public void mediaServerChanged(Server newServer, Server oldServer)
	{
		//Log.i( android.util.PlugPlayerUtil.DBG_TAG, "inside mediaServerChanged(), NowPlayingActivity: RecivaRemoteBase" );
	}

	@Override
	public void mediaServerListChanged()
	{
		//Log.i( android.util.PlugPlayerUtil.DBG_TAG, "inside mediaServerListChanged(), NowPlayingActivity: RecivaRemoteBase" );
	}


	@Override
	public void onErrorFromDevice(String error)
	{
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "inside onErrorFromDevice(), NowPlayingActivity: RecivaRemoteBase" );
	}

	@Override
	public void playStateChanged(final PlayState state)
	{
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "inside playStateChanged(), NowPlayingActivity: RecivaRemoteBase" );
//		handler.post( new Runnable()
//		{			
//			@Override
//			public void run()
//			{
//				if ( !pandora )
//				{
//					if ( state != PlayState.Playing )
//					{
//						control2.setImageResource( R.drawable.button_play );
//					}
//					else
//					{
//						control2.setImageResource( R.drawable.button_pause );
//					}
//				}
//			}
//		});
	}

	@Override
	public void playModeChanged(PlayMode mode)
	{
		//Log.i( android.util.PlugPlayerUtil.DBG_TAG, "inside playModeChanged(), NowPlayingActivity: RecivaRemoteBase" );
	}

	@Override
	public void playlistChanged()
	{
		//Log.i( android.util.PlugPlayerUtil.DBG_TAG, "inside playlistChanged(), NowPlayingActivity: RecivaRemoteBase" );
	}

	@Override
	public void volumeChanged(float volume)
	{
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "inside volumeChanged(), NowPlayingActivity: RecivaRemoteBase" );
	}

	@Override
	public void trackNumberChanged(int trackNumber)
	{
		//Log.i( android.util.PlugPlayerUtil.DBG_TAG, "inside trackNumberChanged(), NowPlayingActivity: RecivaRemoteBase" );
	}

	@Override
	public void timeStampChanged(int seconds)
	{
		//Log.i( android.util.PlugPlayerUtil.DBG_TAG, "inside timeStampChanged(), NowPlayingActivity: RecivaRemoteBase" );
	}

	@Override
	public void error(String message)
	{
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "inside error(), NowPlayingActivity: RecivaRemoteBase" );
	}

	@Override
	public void radioStationChanged( final String playbackXMLString )
	{
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "inside radioStationChanged(), NowPlayingActivity: RecivaRemoteBase" );
		handler.post( new Runnable()
		{
			public void run()
			{
				updateStationInfo( playbackXMLString );
			}
		});
	}

	@Override
	public void emitPowerChanged(boolean powerState)
	{
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "inside emitPowerChanged(), NowPlayingActivity: RecivaRemoteBase" );
		PlugPlayerControlPoint controlPoint = PlugPlayerControlPoint.getInstance();
		final RecivaRenderer r = (RecivaRenderer)controlPoint.getCurrentRenderer();
		
		handler.post( new Runnable()
		{
			public void run()
			{
				updatePowerButton( r );
			}
		});
	}
}
