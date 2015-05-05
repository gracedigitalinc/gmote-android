package com.plugplayer.plugplayer.utils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;

import org.cybergarage.upnp.UPnP;
import org.cybergarage.xml.Node;
import org.cybergarage.xml.ParserException;

public class StateMap
{
	private String name = "FOO";
	private final HashMap<String, Object> map = new HashMap<String, Object>();

	public void setName( String name )
	{
		this.name = name;
	}

	public String getName()
	{
		return name;
	}

	public StateList getList( String key )
	{
		return (StateList)map.get( key );
	}

	public StateMap getMap( String key )
	{
		return (StateMap)map.get( key );
	}

	public String getValue( String key, String defaultValue )
	{
		String rval = (String)map.get( key );

		if ( rval == null )
			return defaultValue;
		else
			return rval;
	}

	public int getValue( String key, int defaultValue )
	{
		String rval = (String)map.get( key );

		if ( rval == null )
			return defaultValue;
		else
			return Integer.parseInt( rval );
	}

	public boolean getValue( String key, boolean defaultValue )
	{
		String rval = (String)map.get( key );

		if ( rval == null )
			return defaultValue;
		else
			return Boolean.parseBoolean( rval );
	}

	public void setList( String key, StateList value )
	{
		map.put( key, value );
	}

	public void setMap( String key, StateMap value )
	{
		map.put( key, value );
	}

	public void setValue( String key, String value )
	{
		map.put( key, value );
	}

	public void setValue( String key, int value )
	{
		map.put( key, "" + value );
	}

	public void setValue( String key, boolean value )
	{
		map.put( key, "" + value );
	}

	public void writeXML( String filename ) throws IOException
	{
		String xml = writeXML().toString();

		FileOutputStream fos = new FileOutputStream( filename );
		fos.write( xml.getBytes() );
		fos.close();
	}

	private Node writeXML()
	{
		Node parent = new Node( getName() );

		for ( Entry<String, Object> e : map.entrySet() )
		{
			String key = e.getKey();
			Object value = e.getValue();
			if ( value instanceof String )
			{
				Node child = new Node( "ENTRY" );
				child.addAttribute( "key", key );
				child.setValue( (String)value );
				parent.addNode( child );
			}
			else if ( value instanceof StateMap )
			{
				StateMap childMap = (StateMap)value;
				Node child = childMap.writeXML();
				child.addAttribute( "key", key );
				parent.addNode( child );
			}
			else if ( value instanceof StateList )
			{
				StateList childMapList = (StateList)value;
				Node list = new Node( "LIST" );
				list.addAttribute( "key", key );
				for ( StateMap childMap : childMapList )
				{
					Node child = childMap.writeXML();
					list.addNode( child );
				}
				parent.addNode( list );
			}

		}

		return parent;
	}

	public static StateMap fromXML( String filename ) throws IOException, ParserException
	{
		StateMap rval = new StateMap();

		Node parent = UPnP.getXMLParser().parse( new FileInputStream( filename ) );
		rval.readXML( parent );

		return rval;
	}

	private void readXML( Node parent )
	{
		setName( parent.getName() );

		for ( int i = 0; i < parent.getNNodes(); ++i )
		{
			Node child = parent.getNode( i );
			String name = child.getName();
			String key = child.getAttributeValue( "key" );

			if ( name.equals( "ENTRY" ) )
			{
				String value = child.getValue();
				map.put( key, value );
			}
			else if ( name.equals( "LIST" ) )
			{
				StateList list = new StateList();
				for ( int x = 0; x < child.getNNodes(); ++x )
				{
					Node listItem = child.getNode( x );
					StateMap childMap = new StateMap();
					childMap.readXML( listItem );
					list.add( childMap );
				}
				map.put( key, list );
			}
			else
			{
				StateMap childMap = new StateMap();
				childMap.readXML( child );
				map.put( key, childMap );
			}
		}
	}
}
