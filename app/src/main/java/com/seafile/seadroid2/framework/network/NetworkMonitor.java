package com.seafile.seadroid2.framework.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.blankj.utilcode.util.NetworkUtils;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.account.SupportAccountManager;
import com.seafile.seadroid2.framework.util.SLogs;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NetworkMonitor {

    private static final String TAG = "NetworkMonitor";

    private final AtomicBoolean serverReachable = new AtomicBoolean(true);

    private final OkHttpClient pingClient = new OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build();

    private final MutableLiveData<Boolean> _connectivityLiveData = new MutableLiveData<Boolean>(true) {
        @Override
        protected void onActive() {
            super.onActive();
            postValue(isOnline());
        }
    };

    private NetworkMonitor() {
    }

    public static NetworkMonitor getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        private static final NetworkMonitor INSTANCE = new NetworkMonitor();
    }

    public void register(@NonNull Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return;
        }

        checkConnectivity();

        cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                checkConnectivity();
            }

            @Override
            public void onLost(@NonNull Network network) {
                serverReachable.set(true);
                _connectivityLiveData.postValue(false);
            }
        });
    }

    public MutableLiveData<Boolean> getConnectivityLiveData() {
        return _connectivityLiveData;
    }

    public void refreshConnectivity() {
        checkConnectivity();
    }

    public boolean isOnline() {
        return NetworkUtils.isConnected() && serverReachable.get();
    }

    private void checkConnectivity() {
        if (!NetworkUtils.isConnected()) {
            serverReachable.set(true);
            _connectivityLiveData.postValue(false);
            return;
        }
        pingServer();
    }

    private void pingServer() {
        Account account = SupportAccountManager.getInstance().getCurrentAccount();
        if (account == null) {
            serverReachable.set(true);
            _connectivityLiveData.postValue(true);
            return;
        }

        pingClient.dispatcher().executorService().execute(() -> {
            try {
                Request request = new Request.Builder()
                        .url(account.getServer() + "api2/ping/")
                        .build();
                try (Response response = pingClient.newCall(request).execute()) {
                    serverReachable.set(response.isSuccessful());
                }
            } catch (Exception e) {
                SLogs.d(TAG, "pingServer()", "Server unreachable: " + e.getMessage());
                serverReachable.set(false);
            }
            _connectivityLiveData.postValue(isOnline());
        });
    }
}
