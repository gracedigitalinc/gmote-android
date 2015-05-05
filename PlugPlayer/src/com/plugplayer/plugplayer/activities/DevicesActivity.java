package com.plugplayer.plugplayer.activities;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import org.cybergarage.upnp.UPnP;
import org.cybergarage.xml.Node;
import org.cybergarage.xml.Parser;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.plugplayer.plugplayer.R;
import com.plugplayer.plugplayer.upnp.AndroidRenderer;
import com.plugplayer.plugplayer.upnp.AndroidServer;
import com.plugplayer.plugplayer.upnp.ControlPointListener;
import com.plugplayer.plugplayer.upnp.MP3TunesServer;
import com.plugplayer.plugplayer.upnp.MediaDevice;
import com.plugplayer.plugplayer.upnp.PlugPlayerControlPoint;
import com.plugplayer.plugplayer.upnp.Renderer;
import com.plugplayer.plugplayer.upnp.Server;

public class DevicesActivity extends ListActivity implements ControlPointListener
{
	private final Handler handler = new Handler();

	public static boolean cloudUPnP = false;

	PlugPlayerControlPoint controlPoint;

	@Override
	public void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );

		controlPoint = PlugPlayerControlPoint.getInstance();
		controlPoint.addControlPointListener( this );

		List<Renderer> renderers = controlPoint.getRendererList();
		List<Server> servers = controlPoint.getServerList();
		setListAdapter( new DeviceListAdapter( this, renderers, servers, getListView() ) );

		final ListView lv = getListView();
		lv.setTextFilterEnabled( false );
		lv.setOnItemClickListener( new OnItemClickListener()
		{
			public void onItemClick( AdapterView<?> parent, View view, int position, long id )
			{
				if ( position == ((DeviceListAdapter)getListAdapter()).cloudButton )
				{
					LayoutInflater inflater = (LayoutInflater)getSystemService( LAYOUT_INFLATER_SERVICE );
					final View layout = inflater.inflate( R.layout.cloud_dialog, (ViewGroup)findViewById( R.id.layout_root ) );

					AlertDialog.Builder builder = new AlertDialog.Builder( DevicesActivity.this );
					builder.setTitle( R.string.cloudupnp_add_title );
					builder.setView( layout );
					builder.setPositiveButton( R.string.cloudupnp_add_button, new DialogInterface.OnClickListener()
					{
						public void onClick( DialogInterface dialog, int id )
						{
							EditText server = (EditText)layout.findViewById( R.id.server_field );
							EditText username = (EditText)layout.findViewById( R.id.user_field );
							EditText password = (EditText)layout.findViewById( R.id.pass_field );

							String hash = "";
							try
							{
								hash = digest( username.getText() + "" + password.getText() );
							}
							catch ( Exception e )
							{
								e.printStackTrace();
							}
							String descriptionURL = "http://" + server.getText() + "/" + username.getText() + "/" + hash;

							try
							{
								Parser parser = UPnP.getXMLParser();
								Node rootNode = parser.parse( new URL( descriptionURL ) );
								if ( rootNode == null )
									descriptionURL = null;
								else
								{
									Node deviceNode = rootNode.getNode( "device" );
									if ( deviceNode == null )
										descriptionURL = null;
									else
									{
										String friendlyName = deviceNode.getNodeValue( "friendlyName" );
										if ( friendlyName == null || friendlyName.equals( "" ) || friendlyName.contains( "Invalid Password" ) )
											descriptionURL = null;
									}
								}
							}
							catch ( Exception e )
							{
								descriptionURL = null;
								e.printStackTrace();
							}

							if ( descriptionURL == null || !controlPoint.addDevice( descriptionURL ) )
								Toast.makeText( getBaseContext(), R.string.error_reading_url, 7 ).show();
							else
								Toast.makeText( getBaseContext(), R.string.device_added, 7 ).show();
						}

						private String digest( String text ) throws NoSuchAlgorithmException, UnsupportedEncodingException
						{
							MessageDigest md = MessageDigest.getInstance( "SHA-1" );
							byte[] sha1hash = new byte[40];
							md.update( text.getBytes( "iso-8859-1" ), 0, text.length() );
							sha1hash = md.digest();

							StringBuffer buf = new StringBuffer();
							for ( int i = 0; i < sha1hash.length; i++ )
							{
								int halfbyte = (sha1hash[i] >>> 4) & 0x0F;
								int two_halfs = 0;
								do
								{
									if ( (0 <= halfbyte) && (halfbyte <= 9) )
										buf.append( (char)('0' + halfbyte) );
									else
										buf.append( (char)('A' + (halfbyte - 10)) );
									halfbyte = sha1hash[i] & 0x0F;
								} while ( two_halfs++ < 1 );
							}

							return buf.toString();
						}
					} );
					builder.setNegativeButton( R.string.cancel, new DialogInterface.OnClickListener()
					{
						public void onClick( DialogInterface dialog, int id )
						{
							dialog.cancel();
						}
					} );
					builder.create().show();
					return;
				}

				MediaDevice device = (MediaDevice)lv.getItemAtPosition( position );

				// Not sure how this could have happened; perhaps a race condition?
				if ( device == null )
					return;

				device.updateAlive();

				if ( !device.isAlive() )
				{
					((BaseAdapter)getListAdapter()).notifyDataSetChanged();
					return;
				}

				if ( device instanceof Renderer )
					controlPoint.setCurrentRenderer( (Renderer)device );
				else
					controlPoint.setCurrentServer( (Server)device );
			}
		} );
		lv.setOnCreateContextMenuListener( new OnCreateContextMenuListener()
		{
			public void onCreateContextMenu( ContextMenu menu, View v, ContextMenuInfo menuInfo )
			{
				int position = ((AdapterContextMenuInfo)menuInfo).position;
				MediaDevice d = (MediaDevice)lv.getAdapter().getItem( position );

				if ( d != null )
				{
					if ( !(d instanceof AndroidRenderer) && !(d instanceof AndroidServer) && !(d instanceof MP3TunesServer) )
						menu.add( 0, position, 0, R.string.delete_device );
					menu.add( 0, 1000 + position, 1, R.string.edit_device );
					menu.add( 0, -9999, 2, R.string.cancel );
				}
			}
		} );
	}

	@Override
	public void onBackPressed()
	{
	}

	@Override
	public boolean onContextItemSelected( MenuItem item )
	{
		if ( item.getItemId() == -9999 )
			return super.onContextItemSelected( item );

		if ( item.getItemId() < 1000 )
		{
			MediaDevice d = (MediaDevice)getListView().getAdapter().getItem( item.getItemId() );
			controlPoint.removeDevice( d );
			return true;
		}
		else
		{
			MediaDevice d = (MediaDevice)getListView().getAdapter().getItem( item.getItemId() - 1000 );
			DeviceEditor.editDevice = d;
			Intent intent = new Intent( this, DeviceEditor.class );
			startActivity( intent );
			return true;
		}
	}

	@Override
	public boolean onCreateOptionsMenu( Menu menu )
	{
		menu.add( 0, 1, 0, R.string.advanced_settings );
		menu.add( 0, 0, 1, R.string.quit_app );
		return true;
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem item )
	{
		if ( item.getItemId() == 0 ) // Quit
		{
			// XXX Add "are you sure?"
			MainActivity.me.quit();
			return true;
		}
		else if ( item.getItemId() == 1 ) // Settings
		{
			Intent intent = new Intent( this, SettingsEditor.class );
			startActivity( intent );
		}

		return super.onOptionsItemSelected( item );
	}

	@Override
	public boolean onSearchRequested()
	{
		return false;
	}

	private void refreshAsync()
	{
		handler.post( new Runnable()
		{
			public void run()
			{
				((DeviceListAdapter)getListAdapter()).renderers.clear();
				((DeviceListAdapter)getListAdapter()).renderers.addAll( PlugPlayerControlPoint.getInstance().getRendererList() );
				((DeviceListAdapter)getListAdapter()).servers.clear();
				((DeviceListAdapter)getListAdapter()).servers.addAll( PlugPlayerControlPoint.getInstance().getServerList() );
				((DeviceListAdapter)getListAdapter()).notifyDataSetChanged();
			}
		} );
	}

	public class DeviceListAdapter extends BaseAdapter
	{
		final private List<Renderer> renderers;
		final private List<Server> servers;

		private final int rendererHeader = 0;
		// private final int rendererStart = 1;
		private int rendererEmpty;
		private int spacer;
		private int serverHeader;
		private int serverEmpty;
		private int spacer2;
		private int manualEdit;
		private int cloudButton;

		public DeviceListAdapter( Context context, List<Renderer> renderers, List<Server> servers, ListView listView )
		{
			this.renderers = new ArrayList<Renderer>( renderers );
			this.servers = new ArrayList<Server>( servers );
			updateIndexes();
		}

		private void updateIndexes()
		{
			rendererEmpty = renderers.isEmpty() ? rendererHeader + 1 : -1;
			spacer = renderers.isEmpty() ? rendererHeader + 2 : rendererHeader + renderers.size() + 1;

			serverHeader = spacer + 1;

			serverEmpty = servers.isEmpty() ? serverHeader + 1 : -1;
			spacer2 = servers.isEmpty() ? serverHeader + 2 : serverHeader + servers.size() + 1;

			if ( cloudUPnP )
			{
				cloudButton = spacer2 + 1;
				manualEdit = cloudButton + 1;

			}
			else
			{
				cloudButton = 999;
				manualEdit = spacer2 + 1;
			}
		}

		@Override
		public void notifyDataSetChanged()
		{
			updateIndexes();
			super.notifyDataSetChanged();
		}

		@Override
		public void notifyDataSetInvalidated()
		{
			updateIndexes();
			super.notifyDataSetInvalidated();
		}

		public View getView( int position, View convertView, ViewGroup parent )
		{
			if ( position == cloudButton )
			{
				if ( convertView == null )
				{
					LayoutInflater vi = (LayoutInflater)getSystemService( Context.LAYOUT_INFLATER_SERVICE );
					convertView = vi.inflate( R.layout.device_list_item, null );
				}

				TextView t = (TextView)convertView.findViewById( R.id.name );
				ImageView i = (ImageView)convertView.findViewById( R.id.icon );
				ImageView check = (ImageView)convertView.findViewById( R.id.check );

				t.setText( R.string.cloudupnp_add_title );
				i.setImageResource( R.drawable.cloudupnp );
				check.setVisibility( View.INVISIBLE );
			}
			else if ( position == manualEdit )
			{
				if ( convertView == null )
				{
					LayoutInflater vi = (LayoutInflater)getSystemService( Context.LAYOUT_INFLATER_SERVICE );
					convertView = vi.inflate( R.layout.device_list_manual, null );

					final EditText e = (EditText)convertView.findViewById( R.id.edit );
					e.setOnKeyListener( new OnKeyListener()
					{
						public boolean onKey( final View v, int keyCode, KeyEvent event )
						{
							if ( (event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER) )
							{
								final String url = e.getText().toString();
								if ( !controlPoint.addDevice( url ) )
								{
									Toast.makeText( getBaseContext(), R.string.error_reading_url, 7 ).show();
									((InputMethodManager)getSystemService( Context.INPUT_METHOD_SERVICE )).hideSoftInputFromWindow( e.getWindowToken(), 0 );
								}
								else
								{
									Toast.makeText( getBaseContext(), R.string.device_added, 7 ).show();
									((InputMethodManager)getSystemService( Context.INPUT_METHOD_SERVICE )).hideSoftInputFromWindow( e.getWindowToken(), 0 );
								}

								e.setText( null );
								handler.post( new Runnable()
								{
									public void run()
									{
										View fv = v.focusSearch( View.FOCUS_LEFT );
										if ( fv != null )
											fv.requestFocus();
									}
								} );
								return true;
							}

							return false;
						}
					} );
				}
			}
			else if ( position == rendererHeader || position == serverHeader )
			{
				if ( convertView == null )
				{
					LayoutInflater vi = (LayoutInflater)getSystemService( Context.LAYOUT_INFLATER_SERVICE );
					convertView = vi.inflate( R.layout.device_list_header, null );
				}

				TextView t = (TextView)convertView;

				if ( position == rendererHeader )
				{
					t.setText( R.string.media_renderers );
				}
				else if ( position == serverHeader )
				{
					t.setText( R.string.media_servers );
				}
			}
			else if ( position == serverEmpty || position == rendererEmpty || position == spacer || position == spacer2 )
			{
				if ( convertView == null )
				{
					LayoutInflater vi = (LayoutInflater)getSystemService( Context.LAYOUT_INFLATER_SERVICE );
					convertView = vi.inflate( R.layout.device_list_empty, null );
				}

				TextView t = (TextView)convertView;

				if ( position == serverEmpty )
					t.setText( R.string.no_servers_found );
				else if ( position == rendererEmpty )
					t.setText( R.string.no_renderers_found );
				else
					t.setText( " " );
			}
			else
			{
				if ( convertView == null )
				{
					LayoutInflater vi = (LayoutInflater)getSystemService( Context.LAYOUT_INFLATER_SERVICE );
					convertView = vi.inflate( R.layout.device_list_item, null );
				}

				TextView t = (TextView)convertView.findViewById( R.id.name );
				ImageView i = (ImageView)convertView.findViewById( R.id.icon );
				ImageView check = (ImageView)convertView.findViewById( R.id.check );

				MediaDevice d = (MediaDevice)getItem( position );

				check.setVisibility( View.INVISIBLE );
				if ( d instanceof Renderer && d == controlPoint.getCurrentRenderer() )
					check.setVisibility( View.VISIBLE );
				if ( d instanceof Server && d == controlPoint.getCurrentServer() )
					check.setVisibility( View.VISIBLE );

				d.loadIcon( i );

				t.setText( d.getName() );

				if ( d.isAlive() )
				{
					i.setAlpha( 255 );
					t.setTextColor( 0xFFFFFFFF );
				}
				else
				{
					i.setAlpha( 100 );
					t.setTextColor( 0x44FFFFFF );
				}
			}

			return convertView;
		}

		public int getCount()
		{
			// Renderers + header -OR- empty + header
			int rval = renderers.isEmpty() ? 2 : renderers.size() + 1;

			// Spacer
			rval++;

			// Servers + header -OR- empty + header
			rval += servers.isEmpty() ? 2 : servers.size() + 1;

			// Spacer 2
			rval++;

			// Manual Entry
			rval++;

			// CloudUPnP
			if ( cloudUPnP )
				rval++;

			return rval;
		}

		public Object getItem( int position )
		{
			if ( position == rendererHeader || position == serverHeader || position == serverEmpty || position == rendererEmpty || position == spacer
					|| position == spacer2 || position == manualEdit || position == cloudButton )
				return null;

			if ( position < serverHeader )
				return renderers.get( position - 1 );
			else
				return servers.get( position - serverHeader - 1 );
		}

		public long getItemId( int position )
		{
			return position;
		}

		@Override
		public int getItemViewType( int position )
		{
			if ( position == rendererHeader || position == serverHeader )
				return 1;

			if ( position == serverEmpty || position == rendererEmpty || position == spacer || position == spacer2 )
				return 2;

			if ( position == manualEdit )
				return 3;

			return 0;
		}

		@Override
		public int getViewTypeCount()
		{
			return 4;
		}

		@Override
		public boolean hasStableIds()
		{
			return false;
		}

		@Override
		public boolean isEmpty()
		{
			return false;
		}

		@Override
		public boolean areAllItemsEnabled()
		{
			return false;
		}

		@Override
		public boolean isEnabled( int position )
		{
			if ( position == rendererHeader || position == serverHeader || position == serverEmpty || position == rendererEmpty || position == spacer
					|| position == spacer2 )
				return false;

			// if ( position == manualEdit )
			return true;

			// MediaDevice d = (MediaDevice)getItem( position );

			// return d.isAlive();
		}
	}

	public void mediaServerChanged( Server newServer, Server oldServer )
	{
		refreshAsync();
	}

	public void mediaServerListChanged()
	{
		refreshAsync();
	}

	public void mediaRendererChanged( Renderer newRenderer, Renderer oldRenderer )
	{
		refreshAsync();
	}

	public void mediaRendererListChanged()
	{
		Log.i( android.util.PlugPlayerUtil.DBG_TAG, "inside mediaRendererListChanged, refresh:DevicesActivity" );
		refreshAsync();
	}

	public void onErrorFromDevice( String error )
	{
	}
}
