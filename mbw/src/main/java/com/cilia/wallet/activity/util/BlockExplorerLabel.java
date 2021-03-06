/*
 * Copyright 2013, 2014 Megion Research and Development GmbH
 *
 * Licensed under the Microsoft Reference Source License (MS-RSL)
 *
 * This license governs use of the accompanying software. If you use the software, you accept this license.
 * If you do not accept the license, do not use the software.
 *
 * 1. Definitions
 * The terms "reproduce," "reproduction," and "distribution" have the same meaning here as under U.S. copyright law.
 * "You" means the licensee of the software.
 * "Your company" means the company you worked for when you downloaded the software.
 * "Reference use" means use of the software within your company as a reference, in read only form, for the sole purposes
 * of debugging your products, maintaining your products, or enhancing the interoperability of your products with the
 * software, and specifically excludes the right to distribute the software outside of your company.
 * "Licensed patents" means any Licensor patent claims which read directly on the software as distributed by the Licensor
 * under this license.
 *
 * 2. Grant of Rights
 * (A) Copyright Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free copyright license to reproduce the software for reference use.
 * (B) Patent Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free patent license under licensed patents for reference use.
 *
 * 3. Limitations
 * (A) No Trademark License- This license does not grant you any rights to use the Licensor’s name, logo, or trademarks.
 * (B) If you begin patent litigation against the Licensor over patents that you think may apply to the software
 * (including a cross-claim or counterclaim in a lawsuit), your license to the software ends automatically.
 * (C) The software is licensed "as-is." You bear the risk of using it. The Licensor gives no express warranties,
 * guarantees or conditions. You may have additional consumer rights under your local laws which this license cannot
 * change. To the extent permitted under your local laws, the Licensor excludes the implied warranties of merchantability,
 * fitness for a particular purpose and non-infringement.
 */

package com.cilia.wallet.activity.util;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import androidx.appcompat.widget.AppCompatTextView;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.common.base.Strings;
import com.cilia.wallet.R;
import com.cilia.wallet.Utils;
import com.cilia.wallet.activity.modern.Toaster;

public abstract class BlockExplorerLabel extends AppCompatTextView {
   private void init() {
      this.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
      this.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
      this.setTypeface(Typeface.MONOSPACE);
   }

   public BlockExplorerLabel(Context context) {
      super(context);
      init();
   }

   public BlockExplorerLabel(Context context, AttributeSet attrs) {
      super(context, attrs);
      init();
   }

   public BlockExplorerLabel(Context context, AttributeSet attrs, int defStyleAttr) {
      super(context, attrs, defStyleAttr);
      init();
   }

   abstract protected String getLinkText();

   abstract protected String getFormattedLinkText();

   abstract protected String getLinkURL(BlockExplorer blockExplorer);

   void update_ui() {
      if (Strings.isNullOrEmpty(getLinkText())) {
         super.setText("");
      } else {
         SpannableString link = new SpannableString(getFormattedLinkText());
         link.setSpan(new UnderlineSpan(), 0, link.length(), 0);
         this.setText(link);
         this.setTextColor(getResources().getColor(R.color.brightblue));
      }
   }

   protected void setHandler(final BlockExplorer blockExplorer) {
      if (!Strings.isNullOrEmpty(getLinkText())) {
         setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
               Utils.setClipboardString(getLinkText(), BlockExplorerLabel.this.getContext());
               new Toaster(BlockExplorerLabel.this.getContext()).toast(R.string.copied_to_clipboard, true);
               return true;
            }
         });

         setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
               Intent intent = new Intent(Intent.ACTION_VIEW);
               intent.setData(Uri.parse(getLinkURL(blockExplorer)));
               BlockExplorerLabel.this.getContext().startActivity(intent);
               new Toaster(BlockExplorerLabel.this.getContext()).toast(R.string.redirecting_to_block_explorer, true);
            }
         });
      }
   }
}
