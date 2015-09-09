package uc3m.client.jarclient;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.telephony.CellLocation;
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

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import org.apache.http.conn.util.InetAddressUtils;

import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.telephony.cdma.CdmaCellLocation;


/**
 * Created by José Fernando on 08/08/2015.
 */
public class RequestsTask extends AsyncTask<String, Void, JSONObject> {

    private MainActivity activity;

    private OkHttpClient httpClient;
    private OkHttpClient http2Client;

    private Exception error = null;
    private int progress    = 0;

    private int[] results;

    public RequestsTask(MainActivity activity) {
        this.activity = activity;

        http2Client = new OkHttpClient();
        httpClient = http2Client.clone();

        httpClient.setProtocols(Arrays.asList(Protocol.HTTP_1_1));
        http2Client.setProtocols(Arrays.asList(Protocol.HTTP_2));
    }


    protected JSONObject getExtraInfo()
    {
        JSONObject info = new JSONObject();

        try {
            TelephonyManager manager = (TelephonyManager)activity.getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);

            info.put("ip", getMobileIP());
            info.put("NetworkType", getNetworkTypeString(manager.getNetworkType()));
            info.put("NetworkSubType", getNetworkSubTypeString(manager.getNetworkType()));

            GsmCellLocation gsmloc = (GsmCellLocation)manager.getCellLocation();
            if (gsmloc.getPsc() != -1) {
                info.put("CellID", gsmloc.getCid());
                info.put("CellLac", gsmloc.getLac());
            } else {
                info.put("CellID", gsmloc.getCid() & 0xffff);
                info.put("CellLac", gsmloc.getLac() & 0xffff);
            }

            String operator = manager.getNetworkOperator();
            info.put("NET-Operator", manager.getNetworkOperatorName());
            info.put("NET-MCC", operator.substring(0, 3));
            info.put("NET-MNC", operator.substring(3));

            operator = manager.getSimOperator();
            info.put("SIM-Operator", manager.getSimOperatorName());
            info.put("SIM-MCC", operator.substring(0, 3));
            info.put("SIM-MNC", operator.substring(3));


        } catch (Exception e) { }

        return info;
    }

    public String getNetworkTypeString(int networkType) {
        switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
            case TelephonyManager.NETWORK_TYPE_IDEN:
                return "2G";
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                return "3G";
            case TelephonyManager.NETWORK_TYPE_LTE:
                return "4G";
            default:
                return "Unknown";
        }
    }

    public String getNetworkSubTypeString(int networkType) {
        switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_CDMA:
                return "CDMA";
            case TelephonyManager.NETWORK_TYPE_EDGE:
                return "EDGE";
            case TelephonyManager.NETWORK_TYPE_GPRS:
                return "GPRS";
            case TelephonyManager.NETWORK_TYPE_HSDPA:
                return "HSDPA";
            case TelephonyManager.NETWORK_TYPE_HSPA:
                return "HSPA";
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                return "HSPA+";
            case TelephonyManager.NETWORK_TYPE_LTE:
                return "LTE";
            case TelephonyManager.NETWORK_TYPE_UMTS:
                return "UMTS";
            case TelephonyManager.NETWORK_TYPE_UNKNOWN:
            default:
                return "Unknown";
        }
    }

    /** Get IP For mobile */
    public String getMobileIP() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = (NetworkInterface) en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()&& InetAddressUtils.isIPv4Address(inetAddress.getHostAddress())) {
                        String ipaddress = inetAddress .getHostAddress().toString();
                        return ipaddress;
                    }
                }
            }
        } catch (SocketException ex) {
            log("Error getting the MobileIP" + ex.getMessage());
        }

        return null;
    }


    protected JSONObject doInBackground(String... uri) {
        JSONObject json = null;

        this.error = null;
        resetProgress();

        int http  = 0;
        int h2    = 0;
        int total = 0;

        try {
            JSONObject testBody = new JSONObject();

            URL entry = new URL(uri[0]);
            //Request request = new Request.Builder().url(entry.toString()).build();

            log("Getting urls to test from: " + entry.toString());
            Response response = request(httpClient, entry, getExtraInfo().toString());

            //Response response = http2Client.newCall(request).execute();
            JSONObject endpoints = new JSONObject(response.body().string());
            JSONArray urls = endpoints.getJSONArray("urls");

            total = urls.length();
            setMax(total * 2);

            for (int i = 0; i < total; ++i) {
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

            URL finish = new URL(entry.getProtocol(), entry.getHost(),entry.getPort(),
                    endpoints.getString("finish"));

            response = request(httpClient, finish);
            json = new JSONObject(response.body().string());

            results = new int[3];
            results[0] = http;
            results[1] = h2;
            results[2] = total;

        } catch (Exception e) {
            log("Error: " + e.getMessage());
            this.error = e;
        }

        activity.finish();
        return json;
    }


    @Override
    protected void onPostExecute(JSONObject json) {
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
            alertDialog.setMessage("Successful requests: "
                            + "\nHTTP/1.1: " + results[0] + "/" + results[2]
                            + "\nHTTP/2:     " + results[1] + "/" + results[2]
            );

            try {
                String code = json.getString("vcode");
                if (code.length() > 0) {
                    activity.setVCode(code);
                }

            } catch (Exception e) {
                // TODO
            }

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
        final ConnectivityManager connMgr = (ConnectivityManager)activity.getSystemService(Context.CONNECTIVITY_SERVICE);

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
        //System.out.println(line);
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
