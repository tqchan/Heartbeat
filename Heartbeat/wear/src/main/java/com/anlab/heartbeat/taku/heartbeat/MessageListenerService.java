package com.anlab.heartbeat.taku.heartbeat;

import android.content.Intent;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;
import com.anlab.heartbeat.taku.heartbeat.MainActivity;

/**
 * Created by taku on 2016/03/22.
 */
public class MessageListenerService extends WearableListenerService {

    private static final String TAG = MessageListenerService.class.getSimpleName();
    private final MessageListenerService self = this;

    private static final String startWearableActivityPath = "/start/wearable/activity/path";
    private static final String finishWearableActivityPath = "/finish/wearable/activity/path";

    private GoogleApiClient googleApiClient;

    @Override
    public void onCreate() {
        super.onCreate();
        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();
        googleApiClient.connect();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        switch (messageEvent.getPath()) {
            case startWearableActivityPath:
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                break;
            case finishWearableActivityPath:
                MainActivity.pressedFinishButton = true;
        }
    }
}
