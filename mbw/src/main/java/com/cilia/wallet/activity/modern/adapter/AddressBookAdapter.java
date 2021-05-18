package com.cilia.wallet.activity.modern.adapter;

import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.annotation.LayoutRes;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.common.base.Preconditions;
import com.cilia.wallet.AddressBookManager;
import com.cilia.wallet.R;
import com.mycelium.wapi.wallet.AddressUtils;

import java.util.List;

/**
 * Created by elvis on 11.09.17.
 */

public class AddressBookAdapter extends ArrayAdapter<AddressBookManager.Entry> {

    private int resource;

    public AddressBookAdapter(Context context, @LayoutRes int resource, List<AddressBookManager.Entry> entries) {
        super(context, resource, entries);
        this.resource = resource;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;

        if (v == null) {
            LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = Preconditions.checkNotNull(vi.inflate(resource, null));
        }
        TextView tvName = (TextView) v.findViewById(R.id.tvName);
        TextView tvAddress = (TextView) v.findViewById(R.id.tvAddress);
        AddressBookManager.Entry e = getItem(position);
        tvName.setText(e.getName());
        String text = AddressUtils.toMultiLineString(e.getAddress().toString());
        tvAddress.setText(text);
        v.setTag(e.getAddress());

        ImageView ivIcon = (ImageView) v.findViewById(R.id.ivIcon);
        if (e instanceof AddressBookManager.IconEntry) {
            Drawable icon = ((AddressBookManager.IconEntry) e).getIcon();
            if (icon == null) {
                ivIcon.setVisibility(View.INVISIBLE);
            } else {
                ivIcon.setImageDrawable(icon);
                ivIcon.setVisibility(View.VISIBLE);
            }
        }

        return v;
    }
}