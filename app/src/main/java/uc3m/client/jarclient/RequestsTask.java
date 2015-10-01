package uc3m.client.jarclient;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.telephony.CellLocation;
import android.util.Log;
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
import java.net.DatagramPacket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import org.apache.http.conn.util.InetAddressUtils;

import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;

import java.net.DatagramSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;


/**
 * Created by José Fernando on 08/08/2015.
 */
public class RequestsTask extends AsyncTask<String, Void, JSONObject> {

    private MainActivity activity;

    private OkHttpClient httpClient;
    private OkHttpClient http2Client;

    private Exception error = null;
    private int progress    = 0;

    public static final String NETWORK_WIFI   = "WiFi";
    public static final String NETWORK_MOBILE = "Mobile";
    public static final String NETWORK_NONE   = "No Network";

    private int[][] results;

    private static final int RESULTS_HTTP1   = 0;
    private static final int RESULTS_UPGRADE = 1;
    private static final int RESULTS_HTTP2   = 2;
    private static final int RESULTS_TCP     = 3;
    private static final int RESULTS_UDP     = 4;

    private static final int RESULTS_SUCCESS = 0;
    private static final int RESULTS_TOTAL   = 1;


    public RequestsTask(MainActivity activity) {
        this.activity = activity;

        http2Client = new OkHttpClient();
        httpClient = http2Client.clone();

        httpClient.setProtocols(Arrays.asList(Protocol.HTTP_1_1));
        http2Client.setProtocols(Arrays.asList(Protocol.HTTP_2));

        results = new int[5][2];
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


    protected JSONObject allTests(URL entry, String network) throws Exception {
        JSONObject json;
        resetProgress();



        log("Getting urls to test from: " + entry.toString());

        Response response;
        if (getNetwork() == NETWORK_MOBILE) {
            response = request(httpClient, entry, getExtraInfo().toString());
        } else {
            response = request(httpClient, entry);
        }

        JSONObject endpoints = new JSONObject(response.body().string());

        String token   = endpoints.getString("token");
        JSONArray urls = endpoints.getJSONArray("urls");

        int length = urls.length();
        setMax(length);


        URI uri;
        URL url;
        JSONObject testBody = (new JSONObject()).put("network", getNetwork());

        for (int i = 0; i < length; ++i) {
            if (isCancelled())  {
                activity.finish();
                return null;
            }

            if (!network.equals(getNetwork())) {
                throw new Exception("WiFi <> Mobile network switch during the test");
            }

            uri = new URI(urls.getString(i));

            if ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) {
                url = new URL(uri.toString());

                response = request(httpClient, url, testBody.toString());
                results[RESULTS_HTTP1][RESULTS_TOTAL]++;
                if (response != null) {
                    results[RESULTS_HTTP1][RESULTS_SUCCESS]++;
                }

                if ("http".equals(url.getProtocol())) {
                    results[RESULTS_UPGRADE][RESULTS_TOTAL]++;

                    if (upgrade(new URL(url.toString() + "/upgrade/" + getNetwork()))) {
                        results[RESULTS_UPGRADE][RESULTS_SUCCESS]++;
                    }
                }

                response = request(http2Client, url, testBody.toString());
                results[RESULTS_HTTP2][RESULTS_TOTAL]++;
                if (response != null) {
                    results[RESULTS_HTTP2][RESULTS_SUCCESS]++;
                }

            } else if ("tcp".equals(uri.getScheme()) ) {
                results[RESULTS_TCP][RESULTS_TOTAL]++;
                if (tcpRequest(uri, token, getNetwork())) {
                    results[RESULTS_TCP][RESULTS_SUCCESS]++;
                }

            } else if ("udp".equals(uri.getScheme())) {
                results[RESULTS_UDP][RESULTS_TOTAL]++;
                if (udpRequest(uri, token, getNetwork())) {
                    results[RESULTS_UDP][RESULTS_SUCCESS]++;
                }

            }

            updateProgress();
        }

        URL finish = new URL(entry.getProtocol(), entry.getHost(),entry.getPort(),
                endpoints.getString("finish"));

        response = request(httpClient, finish);
        json = new JSONObject(response.body().string());

        return json;
    }


    protected JSONObject doInBackground(String... uri) {
        JSONObject json = null;

        this.error = null;
        resetProgress();

        try {
            log("Mobile start");
            String network = getNetwork();
            json = allTests(new URL(uri[0].replace("android", "android-" + network)), network);
            log("Mobile finished");

            WifiManager wifiManager = (WifiManager) activity.getSystemService(Context.WIFI_SERVICE);
            wifiManager.setWifiEnabled(true);
            log("Before sleeps");
            Thread.sleep(5000);
            log("After sleeps");

            ConnectivityManager cm = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo wifiNetwork = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

            log("Checking WiFi");
            if (wifiManager.isWifiEnabled() && wifiNetwork.isConnected()) {
                log("Internet start");
                network = getNetwork();
                json = allTests(new URL(uri[0].replace("android", "android-" + network)), network);
                log("Internet finished");
            }

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
                + "\nHTTP/1.1: "   + results[RESULTS_HTTP1][RESULTS_SUCCESS]
                             + "/" + results[RESULTS_HTTP1][RESULTS_TOTAL]
                + "\nUpgrade:   "  + results[RESULTS_UPGRADE][RESULTS_SUCCESS]
                             + "/" + results[RESULTS_UPGRADE][RESULTS_TOTAL]
                + "\nHTTP/2:     " + results[RESULTS_HTTP2][RESULTS_SUCCESS]
                             + "/" + results[RESULTS_HTTP2][RESULTS_TOTAL]
                + "\nTCP:     " + results[RESULTS_TCP][RESULTS_SUCCESS]
                             + "/" + results[RESULTS_TCP][RESULTS_TOTAL]
                + "\nUDP:     " + results[RESULTS_UDP][RESULTS_SUCCESS]
                             + "/" + results[RESULTS_UDP][RESULTS_TOTAL]
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
            alertDialog.setTitle("Error:");
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

    private boolean upgrade(URL url) {
        boolean upgraded = false;

        try {
            Socket socket = new Socket(url.getHost(), url.getPort());

            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            writer.print("GET " + url.getPath() + " HTTP/1.1\r\n");
            writer.print("Host: " + url.getHost() + "\r\n");
            writer.print("Connection: Upgrade, HTTP2-Settings\r\n");
            writer.print("Upgrade: h2c\r\n");
            writer.print("HTTP2-Settings: AAMAAABkAAQAAP__\r\n");
            writer.print("User-Agent: java-socket/1.0\r\n");
            writer.print("\r\n");
            writer.flush();


            String line = reader.readLine();
            log(line);

            if (line.contains("101")) {
                upgraded = true;

                for (int i = 0; i < 6; i++) {
                    log(reader.readLine());
                }

                writer.print("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n");
                dos.write(new byte[]{
                        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x04,
                        (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00,
                        (byte)0x00,
                });
                writer.flush();

                while ((line = reader.readLine()) != null) {
                    log(line);
                }

            } else {
                upgraded = false;

                while ((line = reader.readLine()) != null) {
                    log(line);
                }
            }


            socket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return upgraded;
    }


    private boolean tcpRequest(URI url, String token, String network) {
        boolean successful = false;

        try {
            Socket socket = new Socket(url.getHost(), url.getPort());

            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            //DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String message = token;
            if (!network.equals("")) {
                message += ":" + network;
            }

            writer.println(message);
            writer.flush();

            String line = reader.readLine();
            log(line);

            successful = (line.contains(token)) ? true : false;

            while ((line = reader.readLine()) != null) {
                log(line);
            }
            socket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return successful;
    }

    private boolean udpRequest(URI url, String token, String network) {
        boolean successful = false;

        DatagramSocket socket = null;
        try{
            String message = token;
            if (!network.equals("")) {
                message += ":" + network;
            }


            socket  = new DatagramSocket();
            byte[]m = message.getBytes();

            InetAddress host = InetAddress.getByName(url.getHost());
            DatagramPacket request = new DatagramPacket(m, message.length(), host, url.getPort());
            socket.send(request);

            //byte[] buffer = new byte[1000];
            //DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
            //socket.setSoTimeout(2000);
            //socket.receive(reply);

            //String data = reply.getData().toString();
            //successful = (data.contains(token)) ? true : false;
            successful = true;
            socket.close();

        } catch(SocketTimeoutException e){
            e.printStackTrace();
        } catch(Exception e){
            e.printStackTrace();
        } finally{
            if (socket != null) {
                socket.close();
            }
        }

        return successful;
    }


    private String getNetwork() {
        String network = NETWORK_NONE;

        final ConnectivityManager connMgr = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);

        final android.net.NetworkInfo wifi = connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        final android.net.NetworkInfo mobile = connMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

        if (wifi.isConnectedOrConnecting()) {
            network = NETWORK_WIFI;
        } else if (mobile.isConnectedOrConnecting()) {
            network = NETWORK_MOBILE;
        }

        return network;
    }

    private void log(String line) {
        //Log.d("app", line);
        //System.out.println(line);
    }

    private void resetProgress() {
        progress = 0;
        activity.setProgressBarIndeterminate(true);
    }

    private void updateProgress() {
        activity.setValueProgress(++progress);
    }

    private void setMax(int max) {
        activity.setMaxProgress(max);
    }

}
