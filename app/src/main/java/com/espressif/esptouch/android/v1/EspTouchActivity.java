package com.espressif.esptouch.android.v1;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;

import com.espressif.esptouch.android.EspTouchActivityAbs;
import com.espressif.iot.esptouch.EsptouchTask;
import com.espressif.iot.esptouch.IEsptouchResult;
import com.espressif.iot.esptouch.IEsptouchTask;
import com.espressif.iot.esptouch.util.ByteUtil;
import com.espressif.iot.esptouch.util.TouchNetUtil;
import com.espressif.iot_esptouch_demo.EspTouchApp;
import com.espressif.iot_esptouch_demo.R;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class EspTouchActivity extends EspTouchActivityAbs {
    private static final String TAG = EspTouchActivity.class.getSimpleName();

    private static final int REQUEST_PERMISSION = 0x01;

    private EspTouchViewModel mViewModel;

    private EsptouchAsyncTask4 mTask;
    private TextView msgrecv;
    public static Context context ;
    private MyHandler myHandler;
    private MyBroadcastReceiver myBroadcastReceiver ;
    private Button cancelBtn,ConfirmBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_esptouch);
        mViewModel = new EspTouchViewModel();
        myHandler =   new MyHandler(this);
        myBroadcastReceiver = new MyBroadcastReceiver();
        context = this;
        mViewModel.apSsidTV = findViewById(R.id.apssidEdit);
//        mViewModel.apBssidTV = findViewById(R.id.apBssidText);
        mViewModel.apPasswordEdit = findViewById(R.id.apPasswordEdit);
        mViewModel.deviceCountEdit = findViewById(R.id.deviceCountEdit);
        mViewModel.messageView = findViewById(R.id.messageView);
        mViewModel.confirmBtn = findViewById(R.id.confirmBtn);
        mViewModel.serverIpEdit = findViewById(R.id.serverIpEdit);
        mViewModel.confirmBtn.setOnClickListener(v -> executeEsptouch());
        ConfirmBtn = mViewModel.confirmBtn;

        msgrecv = findViewById(R.id.txt_Rcv);
        msgrecv.setMovementMethod(ScrollingMovementMethod.getInstance());
        cancelBtn = findViewById(R.id.cancelBtn);
        cancelBtn.setOnClickListener(v -> executeCancelConfig());
        BindReceiver();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION};
            requestPermissions(permissions, REQUEST_PERMISSION);
        }

        EspTouchApp.getInstance().observeBroadcast(this, broadcast -> {
            Log.d(TAG, "onCreate: Broadcast=" + broadcast);
            onWifiChanged();
        });

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onWifiChanged();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.esptouch1_location_permission_title)
                        .setMessage(R.string.esptouch1_location_permission_message)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                        .show();
            }

            return;
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected String getEspTouchVersion() {
        return getString(R.string.esptouch1_about_version, IEsptouchTask.ESPTOUCH_VERSION);
    }

    private StateResult check() {
        StateResult result = checkPermission();
        if (!result.permissionGranted) {
            return result;
        }
        result = checkLocation();
        result.permissionGranted = true;
        if (result.locationRequirement) {
            return result;
        }
        result = checkWifi();
        result.permissionGranted = true;
        result.locationRequirement = false;
        return result;
    }

    private void onWifiChanged() {
        StateResult stateResult = check();
        mViewModel.message = stateResult.message;
        mViewModel.ssid = stateResult.ssid;
        mViewModel.ssidBytes = stateResult.ssidBytes;
        mViewModel.bssid = stateResult.bssid;
        mViewModel.confirmEnable = true;
        if (stateResult.wifiConnected) {
            if (stateResult.is5G) {
                mViewModel.message = getString(R.string.esptouch1_wifi_5g_message);
            }
        } else {
            if (mTask != null) {
                mTask.cancelEsptouch();
                mTask = null;
                new AlertDialog.Builder(EspTouchActivity.this)
                        .setMessage(R.string.esptouch1_configure_wifi_change_message)
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        }
        mViewModel.invalidateAll();
    }

    private void executeEsptouch() {
        EspTouchViewModel viewModel = mViewModel;
        CharSequence ssidStr = mViewModel.apSsidTV.getText();
        String ssid = ssidStr == null ? null : ssidStr.toString();
        CharSequence pwdStr = mViewModel.apPasswordEdit.getText();
        String password = pwdStr == null ? null : pwdStr.toString();
        CharSequence devCountStr = mViewModel.deviceCountEdit.getText();
        String deviceCount = devCountStr == null ? null: devCountStr.toString();
        CharSequence serIPStr = mViewModel.serverIpEdit.getText();
        String serverIp = serIPStr == null ? null : serIPStr.toString();
        if (mTask != null) {
            mTask.cancelEsptouch();
        }
        msgrecv.setText("");
        mViewModel.messageView.setText("");
        if(isApOn()){

        }else{
            msgrecv.setText("请确认您已开启AP热点，并设置频段为2.4G，SSID为ESPConfig 密码为12345678\r\n如果无法成功配置，请点击取消，确认热点已经按要求配置后再重新开始\r\n");
        }
        mTask = new EsptouchAsyncTask4(this,this.ConfirmBtn);
        mTask.execute(ssid,password,serverIp, deviceCount);
        viewModel.confirmBtn.setEnabled(false);

    }

    public  boolean isApOn() {
        WifiManager wifimanager = (WifiManager) context.getSystemService(context.WIFI_SERVICE);
        try {
            Method method = wifimanager.getClass().getDeclaredMethod("isWifiApEnabled");
            method.setAccessible(true);
            return (Boolean) method.invoke(wifimanager);
        }
        catch (Throwable ignored) {
            Log.i("SocketInfo", "ap is close");
        }
        return false;
    }

//    public  boolean isWifiApOpen() {
//        try {
//            WifiManager manager = (WifiManager) context.getSystemService(context.WIFI_SERVICE);
//
//            //通过放射获取 getWifiApState()方法
//            Method method = manager.getClass().getDeclaredMethod("getWifiApState");
//            //调用getWifiApState() ，获取返回值
//            int state = (int) method.invoke(manager);
//            //通过放射获取 WIFI_AP的开启状态属性
//            Field field = manager.getClass().getDeclaredField("WIFI_AP_STATE_ENABLED");
//            //获取属性值
//            int value = (int) field.get(manager);
//            //判断是否开启
//            if (state == value) {
//                Log.i("SocketInfo", "open ap");
//                return true;
//            } else {
//                Log.i("SocketInfo", "ap is close");
//
//                return false;
//            }
//
//
//
//        } catch (NoSuchMethodException e) {
//            Log.i("SocketInfo", "ap is clNoSuchMethodException");
//            e.printStackTrace();
//        } catch (IllegalAccessException e) {
//            Log.i("SocketInfo", "ap is IllegalAccessException");
//            e.printStackTrace();
//        } catch (InvocationTargetException e) {
//            Log.i("SocketInfo", "ap is InvocationTargetException");
//            e.printStackTrace();
//        } catch (NoSuchFieldException e) {
//            Log.i("SocketInfo", "ap is NoSuchFieldException");
//            e.printStackTrace();
//        }
//        return false;
//    }

    private void executeCancelConfig() {

        mViewModel.confirmBtn.setEnabled(true);
        msgrecv.setText("");
        if(mTask != null) {
            mTask.cancelEsptouch();
            mTask = null;
        }
    }
    private void BindReceiver(){
        IntentFilter intentFilter = new IntentFilter("udpReceiver");
        registerReceiver(myBroadcastReceiver,intentFilter);
    }

    private class MyHandler extends Handler {
        private final WeakReference<EspTouchActivity> mActivity;
        public MyHandler(EspTouchActivity activity) {
            mActivity = new WeakReference<EspTouchActivity>(activity);
        }
        @Override
        public void handleMessage(Message msg) {
            EspTouchActivity activity = mActivity.get();
            if (null != activity){
                switch (msg.what){
                    case 1:
                        String str = msg.obj.toString();
                        msgrecv.append(str);
                        break;
                }
            }
        }
    }


    private class MyBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String mAction = intent.getAction();
            switch (mAction){
                case "udpReceiver":
                    String msg = intent.getStringExtra("udpReceiver");
                    Message message = new Message();
                    message.what = 1;
                    message.obj = msg;
                    myHandler.sendMessage(message);
                    break;
            }
        }
    }


    private static class EsptouchAsyncTask4 extends AsyncTask<String, String, String> {
        private WeakReference<EspTouchActivity> mActivity;

        private final Object mLock = new Object();
        private ProgressDialog mProgressDialog;
        private AlertDialog mResultDialog;
        private udpserver   mEsptouchTask;
        private String mServerIp;
        private Button ConfirmBtn;

        EsptouchAsyncTask4(EspTouchActivity activity,Button Btn) {
            mActivity = new WeakReference<>(activity);
            ConfirmBtn = Btn;

        }

        void cancelEsptouch() {
            cancel(true);
            if(mEsptouchTask != null){
                mEsptouchTask.setUdpLife(false);
            }
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
            }
            if (mResultDialog != null) {
                mResultDialog.dismiss();
            }
            if (mEsptouchTask != null) {
                mEsptouchTask.interrupt();
            }
        }

        @Override
        protected void onPreExecute() {
//            Activity activity = mActivity.get();
//            mProgressDialog = new ProgressDialog(activity);
//            mProgressDialog.setMessage(activity.getString(R.string.esptouch1_configuring_message));
//            mProgressDialog.setCanceledOnTouchOutside(false);
//            mProgressDialog.setOnCancelListener(dialog -> {
//                synchronized (mLock) {
//                    if (mEsptouchTask != null) {
//                        mEsptouchTask.interrupt();
//                    }
//                }
//            });
//            mProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, activity.getText(android.R.string.cancel),
//                    (dialog, which) -> {
//                        synchronized (mLock) {
//                            if (mEsptouchTask != null) {
//                                mEsptouchTask.interrupt();
//                            }
//                        }
//                    });
//            mProgressDialog.show();
        }

        @Override
        protected void onProgressUpdate(String... values) {
            Context context = mActivity.get();
            if (context != null) {
                String result = values[0];
                Log.i(TAG, "EspTouchResult: " + result);
                String text = result ;
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        protected String doInBackground(String... params) {
            EspTouchActivity activity = mActivity.get();
            int taskResultCount;
            synchronized (mLock) {
                String ssid = params[0];
                String pass = params[1];
                String serverip = params[2];
                String devCount = params[3];
                mEsptouchTask = new udpserver(ssid,pass,serverip,devCount);
                Thread thread = new Thread(mEsptouchTask);
                thread.start();
            }
            return mEsptouchTask.getresult();
        }

        @Override
        protected void onPostExecute(String result) {
            EspTouchActivity activity = mActivity.get();
            activity.mTask = null;
            ConfirmBtn.setEnabled(true);
            mProgressDialog = new ProgressDialog(activity);
            mProgressDialog.dismiss();
            if (result == null) {
                mResultDialog = new AlertDialog.Builder(activity)
                        .setMessage(R.string.esptouch1_configure_result_failed_port)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                mResultDialog.setCanceledOnTouchOutside(false);
                return;
            }

            // check whether the task is cancelled and no results received
            if (result.contains("cancel")) {
                return;
            }
            // the task received some results including cancelled while
            // executing before receiving enough results

            if (result.contains("fail")) {
                mResultDialog = new AlertDialog.Builder(activity)
                        .setMessage(R.string.esptouch1_configure_result_failed)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                mResultDialog.setCanceledOnTouchOutside(false);
                return;
            }

            if (result.contains("ok")) {
                mResultDialog = new AlertDialog.Builder(activity)
                        .setMessage(R.string.esptouch1_configure_result_success)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                mResultDialog.setCanceledOnTouchOutside(false);
                return;
            }

        }
    }
}
