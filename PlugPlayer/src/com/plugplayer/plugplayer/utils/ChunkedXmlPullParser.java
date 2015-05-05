package com.plugplayer.plugplayer.utils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Iterator;

import org.cybergarage.xml.Node;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

public class ChunkedXmlPullParser
{
	private static class ChildIterator implements Iterable<Node>, Iterator<Node>
	{
		final XmlPullParser xpp;

		int eventType;
		Node rootNode = null;
		final Filter filter;

		String filterNode = null;

		public ChildIterator( XmlPullParser xpp, Filter filter )
		{
			this.xpp = xpp;
			this.filter = filter;

			try
			{
				this.eventType = xpp.getEventType();
			}
			catch ( Exception e )
			{
				e.printStackTrace();
				this.eventType = XmlPullParser.END_DOCUMENT;
			}
		}

		public Iterator<Node> iterator()
		{
			return this;
		}

		public boolean hasNext()
		{
			if ( eventType == XmlPullParser.END_DOCUMENT )
				return false;

			return true;
		}

		public Node next()
		{
			Node rval = null;

			try
			{
				Node currNode = null;
				while ( eventType != XmlPullParser.END_DOCUMENT )
				{
					switch ( eventType )
					{
						case XmlPullParser.START_TAG:
						{
							String namePrefix = xpp.getPrefix();
							String name = xpp.getName();

							if ( filterNode != null )
							{
								// Ignore this node...
							}
							else if ( filter != null && filter.ignoreNode( namePrefix, name ) )
							{
								filterNode = name;
							}
							else
							{
								Node node = new Node();
								StringBuffer nodeName = new StringBuffer();
								if ( namePrefix != null && 0 < namePrefix.length() )
								{
									nodeName.append( namePrefix );
									nodeName.append( ":" );
								}
								if ( name != null && 0 < name.length() )
									nodeName.append( name );
								node.setName( nodeName.toString() );

								// XXX HAA
								String namespace = xpp.getNamespace();
								if ( namespace != null )
								{
									if ( namePrefix == null )
										node.setNamespace( " xmlns=\"" + namespace + "\"" );
									else
										node.setNamespace( " xmlns:" + namePrefix + "=\"" + namespace + "\"" );
								}

								int attrsLen = xpp.getAttributeCount();
								for ( int n = 0; n < attrsLen; n++ )
								{
									String attrName = xpp.getAttributeName( n );
									String attrValue = xpp.getAttributeValue( n );
									node.setAttribute( attrName, attrValue );
								}

								if ( currNode != null && currNode != rootNode )
									currNode.addNode( node );

								currNode = node;

								if ( rootNode == null )
									rootNode = node;
								else if ( rval == null )
									rval = node;
							}

							break;
						}

						case XmlPullParser.TEXT:
						{
							if ( filterNode == null )
							{
								String value = xpp.getText();
								if ( value != null && currNode != null )
									currNode.setValue( value );
							}

							break;
						}

						case XmlPullParser.END_TAG:
						{
							if ( filterNode == null )
							{
								Node lastNode = currNode;

								currNode = currNode.getParentNode();

								if ( lastNode == rval )
								{
									eventType = xpp.next();

									// Ignore text nodes that follow end tag
									while ( eventType == XmlPullParser.TEXT )
										eventType = xpp.next();

									if ( currNode == null && eventType == XmlPullParser.END_TAG )
										eventType = XmlPullParser.END_DOCUMENT;

									return rval;
								}
							}
							else
							{
								if ( filterNode.equals( xpp.getName() ) )
									filterNode = null;
							}

							break;
						}
					}

					eventType = xpp.next();
				}
			}
			catch ( Exception e )
			{
				e.printStackTrace();
				this.eventType = XmlPullParser.END_DOCUMENT;
			}

			return rval;
		}

		public void remove()
		{
			throw new UnsupportedOperationException();
		}
	}

	public static interface Filter
	{
		boolean ignoreNode( String nodePrefix, String nodeName );
	}

	public static Iterable<Node> parse( String result )
	{
		return parse( result, null );
	}

	public static Iterable<Node> parse( String result, Filter filter )
	{
		InputStream inputStream = new ByteArrayInputStream( result.getBytes() );

		org.xmlpull.v1.XmlPullParser xpp = null;

		try
		{
			XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
			factory.setNamespaceAware( true );
			xpp = factory.newPullParser();
			xpp.setInput( inputStream, null );
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}

		return new ChildIterator( xpp, filter );
	}

}
