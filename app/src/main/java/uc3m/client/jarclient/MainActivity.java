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
    private EditText campaign;
    private EditText worker;
    private EditText input;
    private ProgressBar progress;

    private EditText vcode;
    private TextView vcodeLabel;

    private RequestsTask task;

    private Boolean mobile = false;
    private Boolean wifi   = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        campaign    = (EditText)findViewById(R.id.campaignInput);
        worker      = (EditText)findViewById(R.id.workerInput);

        input       = (EditText)findViewById(R.id.urlInput);
        progress    = (ProgressBar)findViewById(R.id.progress);

        button      = (Button)findViewById(R.id.button);

        vcode       = (EditText)findViewById(R.id.vcodeOutput);
        vcodeLabel  = (TextView)findViewById(R.id.vcodeLabel);

        vcode.setEnabled(false);


        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }
        );
        alertDialog.setTitle("Information");
        alertDialog.setMessage("The test will last between 4-10 minutes.\n"
                + "Please, do not close the app until it is done.\n"
                + "For the first test, please disable the WiFi.");
        alertDialog.show();
    }

    public void onBackPressed() {
        if (task != null) {
            task.cancel(true);
        }
    }

    public void start(View x) {
        if (!"Mobile".equals(getNetwork())) {
            AlertDialog alertDialog = new AlertDialog.Builder(this).create();
            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    }
            );
            alertDialog.setTitle("Warning");
            alertDialog.setMessage("For the first test, please disable the WiFi");
            alertDialog.show();
            return;
        }

        String campaignId = campaign.getText().toString().trim();
        String workerId   = worker.getText().toString().trim();

        if (campaignId.length() < 1) {
            AlertDialog alertDialog = new AlertDialog.Builder(this).create();
            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    }
            );
            alertDialog.setTitle("Error!");
            alertDialog.setMessage("Please introduce the Campaign ID");
            alertDialog.show();
            return;
        } else if (workerId.length() < 1) {
            AlertDialog alertDialog = new AlertDialog.Builder(this).create();
            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    }
            );
            alertDialog.setTitle("Error!");
            alertDialog.setMessage("Please introduce the Worker ID");
            alertDialog.show();
            return;
        }


        campaign.setEnabled(false);
        worker.setEnabled(false);
        input.setEnabled(false);

        button.setEnabled(false);

        progress.setProgress(0);
        progress.setIndeterminate(true);

        task = new RequestsTask(this);

        String url = input.getText().toString().trim();
        if (url.endsWith("/") == false) {
            url += "/";
        }

        task.execute(url + campaignId + "/" + workerId);
    }

    private String getNetwork() {
        String network = RequestsTask.NETWORK_NONE;

        final ConnectivityManager connMgr = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);

        final android.net.NetworkInfo wifi = connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        final android.net.NetworkInfo mobile = connMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

        if (wifi.isConnectedOrConnecting()) {
            network = RequestsTask.NETWORK_WIFI;
        } else if (mobile.isConnectedOrConnecting()) {
            network = RequestsTask.NETWORK_MOBILE;
        }

        return network;
    }


    public void finish() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                campaign.setEnabled(true);
                worker.setEnabled(true);
                input.setEnabled(true);

                button.setEnabled(true);

                progress.setIndeterminate(false);
            }
        });
    }


    public void setVCode(final String code) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                vcode.setEnabled(true);
                vcode.setText(code);
                vcodeLabel.setText("Please, copy the VCODE:");
            }
        });
    }

    public void setValueProgress(final int value) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progress.setProgress(value);
            }
        });
    }

    public void setMaxProgress(final int max) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progress.setIndeterminate(false);
                progress.setMax(max);
            }
        });
    }

}
