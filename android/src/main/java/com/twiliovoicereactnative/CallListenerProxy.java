package com.twiliovoicereactnative;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.twilio.voice.Call;
import com.twilio.voice.CallException;

import static com.twiliovoicereactnative.CommonConstants.CallEventConnected;
import static com.twiliovoicereactnative.CommonConstants.CallEventDisconnected;
import static com.twiliovoicereactnative.CommonConstants.CallEventReconnected;
import static com.twiliovoicereactnative.CommonConstants.CallEventReconnecting;
import static com.twiliovoicereactnative.CommonConstants.CallEventRinging;
import static com.twiliovoicereactnative.CommonConstants.ScopeCall;
import static com.twiliovoicereactnative.AndroidEventEmitter.EVENT_KEY_CALL_FROM;
import static com.twiliovoicereactnative.AndroidEventEmitter.EVENT_KEY_CALL_INFO;
import static com.twiliovoicereactnative.AndroidEventEmitter.EVENT_KEY_CALL_SID;
import static com.twiliovoicereactnative.AndroidEventEmitter.EVENT_KEY_CALL_TO;
import static com.twiliovoicereactnative.AndroidEventEmitter.EVENT_KEY_CURRENT_CALL_QUALITY;
import static com.twiliovoicereactnative.AndroidEventEmitter.EVENT_KEY_ERROR;
import static com.twiliovoicereactnative.AndroidEventEmitter.EVENT_KEY_ERROR_CODE;
import static com.twiliovoicereactnative.AndroidEventEmitter.EVENT_KEY_ERROR_MESSAGE;
import static com.twiliovoicereactnative.AndroidEventEmitter.EVENT_KEY_PREVIOUS_CALL_QUALITY;
import static com.twiliovoicereactnative.AndroidEventEmitter.EVENT_KEY_TYPE;
import static com.twiliovoicereactnative.AndroidEventEmitter.EVENT_KEY_UUID;
import static com.twiliovoicereactnative.CommonConstants.CallEventConnectFailure;
import static com.twiliovoicereactnative.CommonConstants.CallEventQualityWarningsChanged;

import java.util.Set;

@RequiresApi(api = Build.VERSION_CODES.N)
class CallListenerProxy implements Call.Listener {
  static final String TAG = "CallListenerProxy";
  private final String uuid;

  private int notificationId;
  private final Context context;

  public CallListenerProxy(String uuid, Context context) {
    this.uuid = uuid;
    this.context = context;
  }

  @Override
  public void onConnectFailure(@NonNull Call call, @NonNull CallException callException) {
    Log.d(TAG, "onConnectFailure");

    MediaPlayerManager.getInstance(this.context).stopRinging();

    WritableMap params = Arguments.createMap();
    params.putString(EVENT_KEY_TYPE, CallEventConnectFailure);
    params.putMap(EVENT_KEY_CALL_INFO, TwilioVoiceReactNativeModule.getCallInfo(uuid, call));
    WritableMap error = Arguments.createMap();
    error.putInt(EVENT_KEY_ERROR_CODE, callException.getErrorCode());
    error.putString(EVENT_KEY_ERROR_MESSAGE, callException.getMessage());
    params.putMap(EVENT_KEY_ERROR, error);
    AndroidEventEmitter.getInstance().sendEvent(ScopeCall, params);

    cancelNotification();

    Storage.callMap.remove(uuid);
  }

  @Override
  public void onRinging(@NonNull Call call) {
    Log.d(TAG, "onRinging");

    this.notificationId = (int) System.currentTimeMillis();
    MediaPlayerManager.getInstance(this.context).playRinging();

    WritableMap params = Arguments.createMap();
    params.putString(EVENT_KEY_TYPE, CallEventRinging);
    params.putMap(EVENT_KEY_CALL_INFO, TwilioVoiceReactNativeModule.getCallInfo(uuid, call));
    AndroidEventEmitter.getInstance().sendEvent(ScopeCall, params);

    raiseNotification(call);
  }

  @Override
  public void onConnected(@NonNull Call call) {
    Log.d(TAG, "onConnected");

    AudioSwitchManager.getInstance(context).getAudioSwitch().activate();
    MediaPlayerManager.getInstance(this.context).stopRinging();

    WritableMap params = Arguments.createMap();
    params.putString(EVENT_KEY_TYPE, CallEventConnected);
    params.putMap(EVENT_KEY_CALL_INFO, TwilioVoiceReactNativeModule.getCallInfo(uuid, call));
    AndroidEventEmitter.getInstance().sendEvent(ScopeCall, params);
  }

  @Override
  public void onReconnecting(@NonNull Call call, @NonNull CallException callException) {
    Log.d(TAG, "onReconnecting");

    WritableMap params = Arguments.createMap();
    params.putString(EVENT_KEY_TYPE, CallEventReconnecting);
    params.putMap(EVENT_KEY_CALL_INFO, TwilioVoiceReactNativeModule.getCallInfo(uuid, call));
    WritableMap error = Arguments.createMap();
    error.putInt(EVENT_KEY_ERROR_CODE, callException.getErrorCode());
    error.putString(EVENT_KEY_ERROR_MESSAGE, callException.getMessage());
    params.putMap(EVENT_KEY_ERROR, error);
    AndroidEventEmitter.getInstance().sendEvent(ScopeCall, params);
  }

  @Override
  public void onReconnected(@NonNull Call call) {
    Log.d(TAG, "onReconnected");

    WritableMap params = Arguments.createMap();
    params.putString(EVENT_KEY_TYPE, CallEventReconnected);
    params.putMap(EVENT_KEY_CALL_INFO, TwilioVoiceReactNativeModule.getCallInfo(uuid, call));
    AndroidEventEmitter.getInstance().sendEvent(ScopeCall, params);
  }

  @Override
  public void onDisconnected(@NonNull Call call, @Nullable CallException callException) {
    Log.d(TAG, "onDisconnected");

    AudioSwitchManager.getInstance(context).getAudioSwitch().deactivate();
    MediaPlayerManager.getInstance(this.context).stopRinging();
    MediaPlayerManager.getInstance(this.context).playDisconnect();

    WritableMap params = Arguments.createMap();
    params.putString(EVENT_KEY_TYPE, CallEventDisconnected);
    params.putMap(EVENT_KEY_CALL_INFO, TwilioVoiceReactNativeModule.getCallInfo(uuid, call));
    if (callException != null) {
      WritableMap error = Arguments.createMap();
      error.putInt(EVENT_KEY_ERROR_CODE, callException.getErrorCode());
      error.putString(EVENT_KEY_ERROR_MESSAGE, callException.getMessage());
      params.putMap(EVENT_KEY_ERROR, error);
    }
    AndroidEventEmitter.getInstance().sendEvent(ScopeCall, params);

    cancelNotification();
    Storage.callMap.remove(uuid);
  }

  @Override
  public void onCallQualityWarningsChanged(@NonNull Call call,
                                           @NonNull Set<Call.CallQualityWarning> currentWarnings,
                                           @NonNull Set<Call.CallQualityWarning> previousWarnings) {
    Log.d(TAG, "onCallQualityWarningsChanged");

    WritableMap params = Arguments.createMap();
    params.putString(EVENT_KEY_TYPE, CallEventQualityWarningsChanged);
    params.putMap(EVENT_KEY_CALL_INFO, TwilioVoiceReactNativeModule.getCallInfo(uuid, call));

    WritableArray currentWarningsArray = Arguments.createArray();
    for (Call.CallQualityWarning warning : currentWarnings) {
      currentWarningsArray.pushString(warning.toString());
    }
    params.putArray(EVENT_KEY_CURRENT_CALL_QUALITY, currentWarningsArray);

    WritableArray previousWarningsArray = Arguments.createArray();
    for (Call.CallQualityWarning warning : previousWarnings) {
      previousWarningsArray.pushString(warning.toString());
    }
    params.putArray(EVENT_KEY_PREVIOUS_CALL_QUALITY, previousWarningsArray);
    AndroidEventEmitter.getInstance().sendEvent(ScopeCall, params);
  }

  private void cancelNotification() {
    Intent intent = new Intent(context, IncomingCallNotificationService.class);
    intent.setAction(Constants.ACTION_CANCEL_NOTIFICATION);
    intent.putExtra(Constants.UUID, this.uuid);
    intent.putExtra(Constants.CALL_SID_KEY, Storage.uuidNotificaionIdMap.get(this.uuid));
    intent.putExtra(Constants.NOTIFICATION_ID, this.notificationId);
    context.startService(intent);
  }

  private void raiseNotification(Call call) {
    Log.d(TAG, "Raising call in progress notification uuid:" + uuid + " notificationId: " + this.notificationId);
    Intent intent = new Intent(context, IncomingCallNotificationService.class);
    intent.setAction(Constants.ACTION_OUTGOING_CALL);
    intent.putExtra(Constants.UUID, this.uuid);
    intent.putExtra(Constants.NOTIFICATION_ID, notificationId);
    intent.putExtra(Constants.CALL_SID_KEY, call.getSid());
    Storage.uuidNotificaionIdMap.put(uuid, this.notificationId);

    context.startService(intent);
  }
}
