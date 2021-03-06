/*
 *  Imported from https://github.com/guardianproject/NetCipher - will be replaced by full library later on,
 *  when we fully support MultiDex
 *
 */

package info.guardianproject.onionkit.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.widget.Toast;

import com.cilia.wallet.R;
import com.cilia.wallet.Utils;
import com.cilia.wallet.activity.modern.Toaster;

import java.util.List;

public class OrbotHelper {

    private final static int REQUEST_CODE_STATUS = 100;

    public final static String ORBOT_PACKAGE_NAME = "org.torproject.android";
    public final static String URI_ORBOT = "org.torproject.android";

    public final static String ACTION_START_TOR = "org.torproject.android.START_TOR";
    public final static String ACTION_REQUEST_HS = "org.torproject.android.REQUEST_HS_PORT";
    public final static int HS_REQUEST_CODE = 9999;

    private Context mContext = null;

    public OrbotHelper(Context context)
    {
        mContext = context;
    }

    public boolean isOrbotRunning(Context context)
    {
        int procId = TorServiceUtils.findProcessId(context);

        return (procId != -1);
    }

    public boolean isOrbotInstalled()
    {
        return Utils.isAppInstalled(mContext, URI_ORBOT);
    }

    public void promptToInstall(Activity activity)
    {
        String uriMarket = activity.getString(R.string.market_orbot);
        // show dialog - install from market, f-droid or direct APK
        showDownloadDialog(activity, activity.getString(R.string.install_orbot_),
                activity.getString(R.string.you_must_have_orbot),
                activity.getString(R.string.yes), activity.getString(R.string.no), uriMarket);
    }

    private static AlertDialog showDownloadDialog(final Activity activity,
            CharSequence stringTitle, CharSequence stringMessage, CharSequence stringButtonYes,
            CharSequence stringButtonNo, final String uriString) {
        AlertDialog.Builder downloadDialog = new AlertDialog.Builder(activity);
        downloadDialog.setTitle(stringTitle);
        downloadDialog.setMessage(stringMessage);
        downloadDialog.setPositiveButton(stringButtonYes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
                Uri uri = Uri.parse(uriString);
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                PackageManager packageManager = activity.getPackageManager();
                List<ResolveInfo> activities = packageManager.queryIntentActivities(intent,
                        PackageManager.MATCH_DEFAULT_ONLY);
                boolean isIntentSafe = !activities.isEmpty();
                if (isIntentSafe) {
                    activity.startActivity(intent);
                } else {
                    new Toaster(activity).toast(R.string.no_google_play_installed, false);
                }
            }
        });
        downloadDialog.setNegativeButton(stringButtonNo, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
            }
        });
        return downloadDialog.show();
    }

    public void requestOrbotStart(final Activity activity)
    {

        AlertDialog.Builder downloadDialog = new AlertDialog.Builder(activity);
        downloadDialog.setTitle(R.string.start_orbot_);
        downloadDialog
                .setMessage(R.string.orbot_doesn_t_appear_to_be_running_would_you_like_to_start_it_up_and_connect_to_tor_);
        downloadDialog.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
                Intent intent = new Intent(URI_ORBOT);
                intent.setAction(ACTION_START_TOR);
                PackageManager packageManager = activity.getPackageManager();
                List<ResolveInfo> activities = packageManager.queryIntentActivities(intent,
                        PackageManager.MATCH_DEFAULT_ONLY);
                boolean isIntentSafe = !activities.isEmpty();
                if (isIntentSafe) {
                    activity.startActivityForResult(intent, 1);
                } else {
                    new Toaster(activity).toast(R.string.no_orbot, false);
                }
            }
        });
        downloadDialog.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
            }
        });
        downloadDialog.show();

    }

    public void requestHiddenServiceOnPort(Activity activity, int port)
    {
        Intent intent = new Intent(URI_ORBOT);
        intent.setAction(ACTION_REQUEST_HS);
        intent.putExtra("hs_port", port);

        activity.startActivityForResult(intent, HS_REQUEST_CODE);
    }
}
