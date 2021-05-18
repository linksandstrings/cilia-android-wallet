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

package com.cilia.wallet.activity.modern;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.fragment.app.Fragment;

import com.cilia.wallet.AddressBookManager;
import com.cilia.wallet.AddressBookManager.Entry;
import com.cilia.wallet.MbwManager;
import com.cilia.wallet.R;
import com.cilia.wallet.Utils;
import com.cilia.wallet.activity.ScanActivity;
import com.cilia.wallet.activity.StringHandlerActivity;
import com.cilia.wallet.activity.modern.adapter.AddressBookAdapter;
import com.cilia.wallet.activity.modern.adapter.SelectAssetDialog;
import com.cilia.wallet.activity.receive.ReceiveCoinsActivity;
import com.cilia.wallet.activity.util.EnterAddressLabelUtil;
import com.cilia.wallet.activity.util.EnterAddressLabelUtil.AddressLabelChangedHandler;
import com.cilia.wallet.content.HandleConfigFactory;
import com.cilia.wallet.content.ResultType;
import com.cilia.wallet.content.StringHandleConfig;
import com.cilia.wallet.event.AddressBookChanged;
import com.cilia.wallet.event.AssetSelected;
import com.mycelium.wapi.wallet.Address;
import com.mycelium.wapi.wallet.WalletAccount;
import com.squareup.otto.Subscribe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.cilia.wallet.activity.util.IntentExtentionsKt.getAddress;
import static com.cilia.wallet.activity.util.IntentExtentionsKt.getAssetUri;


public class AddressBookFragment extends Fragment {
    private static final short SCAN_RESULT_CODE = 1;
    public static final String ADDRESS_RESULT_NAME = "address_result";
    public static final String ADDRESS_RESULT_ID = "address_result_id";
    public static final String OWN = "own";
    public static final String AVAILABLE_FOR_SENDING = "available_for_sending";
    public static final String SELECT_ONLY = "selectOnly";
    public static final String ADDRESS_RESULT_LABEL = "address_result_label";

    private Address mSelectedAddress;
    private MbwManager mbwManager;
    private Dialog addDialog;
    private ActionMode currentActionMode;
    private Boolean ownAddresses; // set to null on purpose
    private Boolean availableForSendingAddresses;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View ret = inflater.inflate(R.layout.address_book, container, false);
        ownAddresses = requireArguments().getBoolean(OWN);
        availableForSendingAddresses = requireArguments().getBoolean(AVAILABLE_FOR_SENDING);
        boolean isSelectOnly = requireArguments().getBoolean(SELECT_ONLY);
        setHasOptionsMenu(!isSelectOnly);
        ListView foreignList = ret.findViewById(R.id.lvForeignAddresses);
        if (isSelectOnly) {
            foreignList.setOnItemClickListener(new SelectItemListener());
        } else {
            foreignList.setOnItemClickListener(itemListClickListener);
        }
        return ret;
    }

    private View findViewById(int id) {
        return requireView().findViewById(id);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        mbwManager = MbwManager.getInstance(context);
        super.onAttach(context);
    }

    @Override
    public void onResume() {
        MbwManager.getEventBus().register(this);
        updateUi();
        super.onResume();
    }

    @Override
    public void onPause() {
        MbwManager.getEventBus().unregister(this);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (addDialog != null && addDialog.isShowing()) {
            addDialog.dismiss();
        }
        super.onDestroy();
    }

    private void updateUi() {
        if (!isAdded()) {
            return;
        }
        if (ownAddresses) {
            updateUiMine();
        } else {
            if(availableForSendingAddresses) {
                updateUiSending();
            } else {
                updateUiForeign();
            }
        }
    }

    private void updateUiMine() {
        List<Entry> entries = new ArrayList<>();
        List<WalletAccount<?>> activeAccounts = new ArrayList<>(mbwManager.getWalletManager(false).getAllActiveAccounts());
        WalletAccount selectedAccount = mbwManager.getSelectedAccount();
        for (WalletAccount account : Utils.sortAccounts(activeAccounts, mbwManager.getMetadataStorage())) {
            if (account.getReceiveAddress() != null &&
                    selectedAccount.getCoinType().getId().equals(account.getCoinType().getId())) {
                String name = mbwManager.getMetadataStorage().getLabelByAccount(account.getId());
                Drawable drawableForAccount = Utils.getDrawableForAccount(account, true, getResources());
                entries.add(new AddressBookManager.IconEntry(account.getReceiveAddress(), name, drawableForAccount, account.getId()));
            }
        }
        if (entries.isEmpty()) {
            findViewById(R.id.tvNoRecords).setVisibility(View.VISIBLE);
            findViewById(R.id.lvForeignAddresses).setVisibility(View.GONE);
        } else {
            findViewById(R.id.tvNoRecords).setVisibility(View.GONE);
            findViewById(R.id.lvForeignAddresses).setVisibility(View.VISIBLE);
            ListView list = (ListView) findViewById(R.id.lvForeignAddresses);
            list.setAdapter(new AddressBookAdapter(getActivity(), R.layout.address_book_my_address_row, entries));
        }
    }

    private void updateUiForeign() {
        Map<Address, String> rawEntries = mbwManager.getMetadataStorage().getAllAddressLabels();
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<Address, String> e : rawEntries.entrySet()) {
            entries.add(new Entry(e.getKey(), e.getValue()));
        }
        entries = Utils.sortAddressbookEntries(entries);
        if (entries.isEmpty()) {
            findViewById(R.id.tvNoRecords).setVisibility(View.VISIBLE);
            findViewById(R.id.lvForeignAddresses).setVisibility(View.GONE);
        } else {
            findViewById(R.id.tvNoRecords).setVisibility(View.GONE);
            findViewById(R.id.lvForeignAddresses).setVisibility(View.VISIBLE);
            ListView foreignList = (ListView) findViewById(R.id.lvForeignAddresses);
            foreignList.setAdapter(new AddressBookAdapter(getActivity(), R.layout.address_book_foreign_row, entries));
        }
    }

    private void updateUiSending() {
        Map<Address, String> rawEntries = mbwManager.getMetadataStorage().getAllAddressLabels();
        List<Entry> entries = new ArrayList<>();
        WalletAccount account = mbwManager.getSelectedAccount();
        for (Map.Entry<Address, String> e : rawEntries.entrySet()) {
            if (e.getKey().getCoinType().equals(account.getBasedOnCoinType())) {
                entries.add(new Entry(e.getKey(), e.getValue()));
            }
        }
        entries = Utils.sortAddressbookEntries(entries);
        if (entries.isEmpty()) {
            findViewById(R.id.tvNoRecords).setVisibility(View.VISIBLE);
            findViewById(R.id.lvForeignAddresses).setVisibility(View.GONE);
        } else {
            findViewById(R.id.tvNoRecords).setVisibility(View.GONE);
            findViewById(R.id.lvForeignAddresses).setVisibility(View.VISIBLE);
            ListView foreignList = (ListView) findViewById(R.id.lvForeignAddresses);
            foreignList.setAdapter(new AddressBookAdapter(getActivity(), R.layout.address_book_sending_row, entries));
        }
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (!isVisibleToUser) {
            finishActionMode();
        }
    }

    private void finishActionMode() {
        if (currentActionMode != null) {
            currentActionMode.finish();
        }
    }

    private OnItemClickListener itemListClickListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> listView, final View view, int position, long id) {
            mSelectedAddress = (Address) view.getTag();
            AppCompatActivity parent = (AppCompatActivity) requireActivity();
            currentActionMode = parent.startSupportActionMode(new ActionMode.Callback() {
                @Override
                public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
                    actionMode.getMenuInflater().inflate(R.menu.addressbook_context_menu, menu);
                    return true;
                }

                @Override
                public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
                    currentActionMode = actionMode;
                    view.setBackground(getResources().getDrawable(R.color.selectedrecord));
                    return true;
                }

                @Override
                public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
                    final int item = menuItem.getItemId();
                    if (item == R.id.miDeleteAddress) {
                        mbwManager.runPinProtectedFunction(getActivity(), pinProtectedDeleteEntry);
                        return true;
                    } else if (item == R.id.miEditAddress) {
                        mbwManager.runPinProtectedFunction(getActivity(), pinProtectedEditEntry);
                        return true;
                    } else if (item == R.id.miShowQrCode) {
                        doShowQrCode();
                        return true;
                    }
                    return false;
                }

                @Override
                public void onDestroyActionMode(ActionMode actionMode) {
                    view.setBackground(null);
                    currentActionMode = null;
                }

                private void doShowQrCode() {
                    if (!isAdded()) {
                        return;
                    }
                    if (mSelectedAddress == null) {
                        return;
                    }
                    boolean hasPrivateKey = mbwManager.getWalletManager(false).hasPrivateKey(mSelectedAddress);
                    UUID tempAccount = mbwManager.createOnTheFlyAccount(mSelectedAddress);
                    ReceiveCoinsActivity.callMe(requireActivity(), mbwManager.getWalletManager(true).getAccount(tempAccount),
                            hasPrivateKey, false, true);
                    finishActionMode();
                }
            });
        }
    };

    private final Runnable pinProtectedDeleteEntry = () -> new AlertDialog.Builder(getActivity())
            .setMessage(R.string.delete_address_confirmation)
            .setCancelable(false)
            .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                    mbwManager.getMetadataStorage().deleteAddressMetadata(mSelectedAddress);
                    finishActionMode();
                    MbwManager.getEventBus().post(new AddressBookChanged());
                }
            })
            .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    finishActionMode();
                }
            })
            .create()
            .show();

    private class AddDialog extends Dialog {
        AddDialog(final Activity activity) {
            super(activity);
            setContentView(R.layout.add_to_address_book_dialog);
            setTitle(R.string.add_to_address_book_dialog_title);

            findViewById(R.id.btScan).setOnClickListener(v -> {
                StringHandleConfig request = HandleConfigFactory.getAddressBookScanRequest();
                ScanActivity.callMe(AddressBookFragment.this, SCAN_RESULT_CODE, request);
                AddDialog.this.dismiss();
            });

            final List<Address> addresses = mbwManager.getWalletManager(false).parseAddress(Utils.getClipboardString(activity));
            findViewById(R.id.btClipboard).setEnabled(addresses.size() != 0);
            findViewById(R.id.btClipboard).setOnClickListener(v -> {
                if (!addresses.isEmpty()) {
                    if(addresses.size() == 1){
                        addFromAddress(addresses.get(0));
                    } else {
                        SelectAssetDialog.getInstance(addresses).show(requireFragmentManager(), "dialog");
                    }
                } else {
                    new Toaster(AddDialog.this.getContext()).toast(R.string.unrecognized_format, true);
                }
                AddDialog.this.dismiss();
            });
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.miAddAddress) {
            addDialog = new AddDialog(getActivity());
            addDialog.show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        if (requestCode != SCAN_RESULT_CODE) {
            super.onActivityResult(requestCode, resultCode, intent);
        }
        if (resultCode != Activity.RESULT_OK) {
            if (intent == null) {
                return; // user pressed back
            }
            String error = intent.getStringExtra(StringHandlerActivity.RESULT_ERROR);
            if (error != null) {
                new Toaster(getActivity()).toast(error, false);
            }
            return;
        }
        ResultType type = (ResultType) intent.getSerializableExtra(StringHandlerActivity.RESULT_TYPE_KEY);
        switch (type) {
            case PRIVATE_KEY:
                Utils.showSimpleMessageDialog(getActivity(), R.string.addressbook_cannot_add_private_key);
                break;
            case ASSET_URI:
                addFromAddress(getAssetUri(intent).getAddress());
                break;
            case ADDRESS:
                addFromAddress(getAddress(intent));
                break;
        }
    }

    private void addFromAddress(Address address) {
        EnterAddressLabelUtil.enterAddressLabel(requireContext(), mbwManager.getMetadataStorage(), address, "", addressLabelChanged);
    }

    private AddressLabelChangedHandler addressLabelChanged = (address, label) -> {
        finishActionMode();
        MbwManager.getEventBus().post(new AddressBookChanged());
    };
    private final Runnable pinProtectedEditEntry = () ->
            EnterAddressLabelUtil.enterAddressLabel(requireActivity(), mbwManager.getMetadataStorage(),
                    mSelectedAddress, "", addressLabelChanged);

    @Subscribe
    public void onAddressBookChanged(AddressBookChanged event) {
        updateUi();
    }

    @Subscribe
    public void newAddressCreating(AssetSelected event) {
        addFromAddress(event.address);
    }

    private class SelectItemListener implements OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Address address = (Address) view.getTag();
            Intent result = new Intent();
            result.putExtra(ADDRESS_RESULT_NAME, address);

            if( parent.getItemAtPosition(position) instanceof AddressBookManager.IconEntry) {
                AddressBookManager.IconEntry item  = (AddressBookManager.IconEntry)parent.getItemAtPosition(position);
                result.putExtra(ADDRESS_RESULT_ID, item.getId());
                result.putExtra(ADDRESS_RESULT_LABEL, item.getName());
            }
            requireActivity().setResult(Activity.RESULT_OK, result);
            requireActivity().finish();
        }
    }
}
