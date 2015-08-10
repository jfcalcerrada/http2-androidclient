package uc3m.client.jarclient;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.widget.*;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Protocol;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URL;
import java.util.Arrays;

/**
 * Created by José Fernando on 08/08/2015.
 */
public class RequestsTask extends AsyncTask<String, Void, int[]> {

    private MainActivity activity;

    private OkHttpClient httpClient;
    private OkHttpClient http2Client;

    private Exception error = null;
    private int progress    = 0;

    public RequestsTask(MainActivity activity) {
        this.activity = activity;

        http2Client = new OkHttpClient();
        httpClient = http2Client.clone();

        httpClient.setProtocols(Arrays.asList(Protocol.HTTP_1_1));
        http2Client.setProtocols(Arrays.asList(Protocol.HTTP_2));
    }

    protected int[] doInBackground(String... uri) {
        int[] results = new int[3];

        this.error = null;
        resetProgress();

        int http  = 0;
        int h2    = 0;
        int total = 0;

        try {
            JSONObject testBody = new JSONObject();

            URL entry = new URL(uri[0]);
            Request request = new Request.Builder().url(entry.toString()).build();
            log("Getting urls to test from: " + entry.toString());

            Response response = http2Client.newCall(request).execute();
            JSONObject json = new JSONObject(response.body().string());
            JSONArray urls = json.getJSONArray("urls");

            total = urls.length();
            setMax(total * 2);

            for (int i = 0; i < urls.length(); ++i) {
                if (isCancelled())  {
                    activity.finish();
                    return null;
                }

                URL url = new URL(urls.getString(i));

                testBody.put("network", getNetwork());
                response = request(httpClient, url, testBody.toString());
                if (response != null) {
                    http++;
                }
                updateProgress();

                testBody.put("network", getNetwork());
                response = request(http2Client, url, testBody.toString());
                if (response != null) {
                    h2++;
                }

                updateProgress();
            }

            URL finish = new URL(
                    entry.getProtocol(),
                    entry.getHost(),
                    entry.getPort(),
                    json.getString("finish")
            );



            request(httpClient, finish, json.toString());

        } catch (Exception e) {
            log("Error: " + e.getMessage());
            this.error = e;
        }

        activity.finish();

        results[0] = http;
        results[1] = h2;
        results[2] = total;

        return results;
    }


    @Override
    protected void onPostExecute(int[] results) {
        AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            }
        );

        if (this.error == null) {
            alertDialog.setTitle("Completed!");
            alertDialog.setMessage(
                    "Successful requests: "
                            + "\nHTTP/1.1: "   + results[0] + "/" + results[2]
                            + "\nHTTP/2:     " + results[1] + "/" + results[2]
            );

        } else {
            alertDialog.setTitle("Error!");
            alertDialog.setMessage(this.error.getMessage());
        }


        alertDialog.show();
    }

    private Response request(OkHttpClient client, URL url) {
        return request(client, url, "");
    }

    private Response request(OkHttpClient client, URL url, String json) {
        if (isCancelled())  {
            return null;
        }

        Response response = null;

        try {
            log("Requesting: " + client.getProtocols().get(0) + " => " + url.toString());

            Request.Builder builder = new Request.Builder().url(url.toString());
            if (!"".equals(json)) {
                builder.post(RequestBody.create(MediaType.parse("application/json"), json));
            }
            Request request = builder.build();
            response = client.newCall(request).execute();

            log("Completed: " + response.code());

        } catch (ConnectException e) {
            log("Failed: " + e.getMessage());
        } catch (IOException e) {
            log("Failed: " + e.getMessage());
        };

        return response;
    }

    private String getNetwork() {
        final ConnectivityManager connMgr = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);

        final android.net.NetworkInfo wifi   = connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        final android.net.NetworkInfo mobile = connMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

        if (wifi.isConnectedOrConnecting()) {
            return "Wifi";
        } else if (mobile.isConnectedOrConnecting()) {
            return "Mobile";
        }
        return "No Network";
    }

    private void log(String line) {
        System.out.println(line);
        activity.log(line);
    }

    private void resetProgress() {
        progress = 0;
    }

    private void updateProgress() {
        activity.setValueProgress(++progress);
    }

    private void setMax(int max) {
        activity.setMaxProgress(max);
    }

}
