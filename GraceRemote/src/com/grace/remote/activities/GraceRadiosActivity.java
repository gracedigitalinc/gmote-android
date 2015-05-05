package com.grace.remote.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.TextView;

import com.grace.remote.R;



// the only purpose of this activity is to insert a Grace FAQ at the front of the list
public class GraceRadiosActivity extends com.plugplayer.recivaremote.activities.RadiosActivity {
	
	OnItemClickListener superListener;

	@Override
	public void onCreate( Bundle savedInstanceState )
	{
		super.onCreate(savedInstanceState);
		superListener = getListView().getOnItemClickListener();
		getListView().setOnItemClickListener( new OnItemClickListener()
		{
			public void onItemClick( AdapterView<?> parent, View view, int position, long id )
			{
				if(position == 0)
				{
					Intent intent = new Intent( GraceRadiosActivity.this, GraceFAQActivity.class );
					startActivity( intent );
				}
				else
				{
				superListener.onItemClick(parent, view, position, id);	
				}
			}
		} );
	}

	@Override
	protected RadiosListAdapter getRadiosListAdapter()
	{
		return new GraceRadiosListAdapter( this );
	}
	
	private class GraceRadiosListAdapter extends com.plugplayer.recivaremote.activities.RadiosActivity.RadiosListAdapter
	{
		public GraceRadiosListAdapter(Context context)
		{
			super(context);
		}
		
		public void updateList()
		{
			super.updateList();
			insert("Grace Remote app FAQ's",0);
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent)
		{
			if (position == 0)
			{
				if ( convertView == null )
				{
					LayoutInflater vi = (LayoutInflater)getContext().getSystemService( Context.LAYOUT_INFLATER_SERVICE );
					convertView = vi.inflate( R.layout.radio_list_item, null );
				}

				TextView t = (TextView)convertView.findViewById( R.id.name );
				t.setText("Grace Remote app FAQ's");
				t.setTextColor( 0xFFFFFFFF );
				((ImageView)convertView.findViewById( R.id.check )).setVisibility(View.INVISIBLE);
			}
			else
			{
				convertView = super.getView(position, convertView, parent);
			}
			
			return convertView;
		}
	}

}
