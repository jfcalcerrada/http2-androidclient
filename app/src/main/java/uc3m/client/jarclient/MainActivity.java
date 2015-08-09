package uc3m.client.jarclient;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.*;

public class MainActivity extends Activity {

    private Button button;
    private EditText input;
    private TextView log;
    private ProgressBar progress;

    private RequestsTask task;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        button = (Button)findViewById(R.id.button);
        input  = (EditText)findViewById(R.id.urlInput);
        log    = (TextView)findViewById(R.id.log);
        progress = (ProgressBar)findViewById(R.id.progress);

        log.setMovementMethod(new ScrollingMovementMethod());
    }

    public void onBackPressed() {
        if (task != null) {
            task.cancel(true);
        }
    }

    public void start(View x) {
        if ("WiFi".equals(getNetwork())) {
            AlertDialog alertDialog = new AlertDialog.Builder(this).create();
            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    }
            );
            alertDialog.setTitle("Warning");
            alertDialog.setMessage("Please, disable the WiFi");
            alertDialog.show();
            return;
        }

        button.setEnabled(false);
        input.setEnabled(false);

        progress.setProgress(0);
        progress.setIndeterminate(true);

        if (log.getText().length() > 0) {
            log.append("=========================\n");
        }

        task = new RequestsTask(this);
        task.execute(input.getText().toString());
    }

    private String getNetwork() {
        final ConnectivityManager connMgr = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);

        final android.net.NetworkInfo wifi   = connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        final android.net.NetworkInfo mobile = connMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

        if (wifi.isConnectedOrConnecting()) {
            return "WiFi";
        } else if (mobile.isConnectedOrConnecting()) {
            return "Mobile";
        }

        return "No Network";
    }


    public void finish() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                button.setEnabled(true);
                input.setEnabled(true);

                progress.setIndeterminate(false);
            }
        });
    }

    public void setValueProgress(int v) {
        final int value = v;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progress.setProgress(value);
            }
        });
    }

    public void setMaxProgress(int m) {
        final int max = m;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progress.setIndeterminate(false);
                progress.setMax(max);
            }
        });
    }

    public void log(String l) {
        final String line = l + "\n";
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                log.append(line);

                final int scrollAmount = log.getLayout().getLineTop(log.getLineCount()) - log.getHeight();
                // if there is no need to scroll, scrollAmount will be <=0
                if (scrollAmount > 0) {
                    log.scrollTo(0, scrollAmount);
                } else {
                    log.scrollTo(0, 0);
                }
            }
        });
    }

}
