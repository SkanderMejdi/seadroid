package com.seafile.seadroid2.framework.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.blankj.utilcode.util.NetworkUtils;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.account.SupportAccountManager;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NetworkMonitor {

    private static final NetworkMonitor INSTANCE = new NetworkMonitor();

    private volatile boolean serverReachable = true;

    private final MutableLiveData<Boolean> connectivityLiveData = new MutableLiveData<Boolean>(true) {
        @Override
        protected void onActive() {
            super.onActive();
            postValue(isOnline());
        }
    };

    private NetworkMonitor() {
    }

    public static NetworkMonitor getInstance() {
        return INSTANCE;
    }

    public void register(@NonNull Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return;
        }

        // Initial check including server ping
        checkConnectivity();

        // Use callback only as a trigger to re-check
        cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                checkConnectivity();
            }

            @Override
            public void onLost(@NonNull Network network) {
                serverReachable = true;
                connectivityLiveData.postValue(false);
            }
        });
    }

    public LiveData<Boolean> getConnectivityLiveData() {
        return connectivityLiveData;
    }

    /**
     * Force a re-check of connectivity and notify LiveData observers if state changed.
     */
    public void refreshConnectivity() {
        checkConnectivity();
    }

    public boolean isOnline() {
        return NetworkUtils.isConnected() && serverReachable;
    }

    private void checkConnectivity() {
        if (!NetworkUtils.isConnected()) {
            serverReachable = true;
            connectivityLiveData.postValue(false);
            return;
        }
        pingServer();
    }

    private void pingServer() {
        Account account = SupportAccountManager.getInstance().getCurrentAccount();
        if (account == null) {
            serverReachable = true;
            connectivityLiveData.postValue(true);
            return;
        }
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(3, TimeUnit.SECONDS)
                        .readTimeout(3, TimeUnit.SECONDS)
                        .build();
                Request request = new Request.Builder()
                        .url(account.getServer() + "api2/ping/")
                        .build();
                Response response = client.newCall(request).execute();
                serverReachable = response.isSuccessful();
                response.close();
            } catch (Exception e) {
                serverReachable = false;
            }
            connectivityLiveData.postValue(isOnline());
        }).start();
    }
}
