package com.reactnativelocationenabler

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import androidx.annotation.Nullable
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationRequest.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.*

class LocationEnablerModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

  private val TAG = "LocationEnabler"
  private val REQUEST_TURN_DEVICE_LOCATION_ON = 29
  private val REQUEST_ENSURE_LOCATION_SETTINGS = 0x564C
  private val EVENT_NAME = "onChangeLocationSettings"
  private val context: ReactApplicationContext = reactContext
  private var priority: Int = PRIORITY_BALANCED_POWER_ACCURACY
  private var alwaysShow: Boolean = false
  private var needBle: Boolean = false

  private data class LocationSettingsCheck(
    val request: LocationSettingsRequest,
    val promise: Promise,
  )

  // Accessed on the main thread by the settings task and the activity result.
  private var pendingSettingsCheck: LocationSettingsCheck? = null

  private val activityEventListener = object : BaseActivityEventListener() {
    override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, @Nullable intent: Intent?) {
      when (requestCode) {
        REQUEST_ENSURE_LOCATION_SETTINGS -> {
          val check = pendingSettingsCheck ?: return
          if (resultCode == Activity.RESULT_OK) {
            checkSettings(activity, check, allowResolution = false)
          } else {
            finishSettingsCheck(check, false)
          }
        }
        REQUEST_TURN_DEVICE_LOCATION_ON -> {
          val result = Arguments.createMap()
          result.putBoolean("locationEnabled", resultCode == Activity.RESULT_OK)
          sendEvent(EVENT_NAME, result)
        }
      }
    }
  }

  init {
    reactContext.addActivityEventListener(activityEventListener)
  }

  override fun invalidate() {
    context.removeActivityEventListener(activityEventListener)
    UiThreadUtil.runOnUiThread {
      pendingSettingsCheck?.let { finishSettingsCheck(it, false) }
    }
    super.invalidate()
  }

  override fun getName(): String = TAG

  override fun getConstants(): Map<String, Int>? {
    val constants: MutableMap<String, Int> = HashMap()
    constants["HIGH_ACCURACY"] = PRIORITY_HIGH_ACCURACY
    constants["BALANCED_POWER_ACCURACY"] = PRIORITY_BALANCED_POWER_ACCURACY
    constants["LOW_POWER"] = PRIORITY_LOW_POWER
    constants["NO_POWER"] = PRIORITY_NO_POWER
    return constants
  }

  @ReactMethod
  fun checkSettings(config: ReadableMap) {
    extractArgs(config)
    checkDeviceLocationSettings(priority, alwaysShow, needBle)
  }

  @ReactMethod
  fun requestResolutionSettings(config: ReadableMap) {
    extractArgs(config)
    requestDeviceResolutionLocationSettings(priority, alwaysShow, needBle)
  }

  @ReactMethod
  fun ensureLocationSettings(config: ReadableMap, promise: Promise) {
    UiThreadUtil.runOnUiThread {
      val activity = context.currentActivity
      if (activity == null || activity.isFinishing || activity.isDestroyed || pendingSettingsCheck != null) {
        promise.resolve(false)
        return@runOnUiThread
      }

      val request = createLocationSettingsRequest(
        if (config.hasKey("priority")) config.getInt("priority") else PRIORITY_BALANCED_POWER_ACCURACY,
        config.hasKey("alwaysShow") && config.getBoolean("alwaysShow"),
        config.hasKey("needBle") && config.getBoolean("needBle"),
      )
      val check = LocationSettingsCheck(request, promise)
      pendingSettingsCheck = check
      checkSettings(activity, check, allowResolution = true)
    }
  }

  private fun checkSettings(activity: Activity, check: LocationSettingsCheck, allowResolution: Boolean) {
    try {
      LocationServices.getSettingsClient(activity).checkLocationSettings(check.request)
        .addOnCompleteListener { task ->
          if (pendingSettingsCheck !== check) return@addOnCompleteListener
          val exception = task.exception
          when {
            task.isSuccessful -> finishSettingsCheck(check, true)
            allowResolution && exception is ResolvableApiException &&
              context.currentActivity === activity && !activity.isFinishing && !activity.isDestroyed -> {
              try {
                exception.startResolutionForResult(activity, REQUEST_ENSURE_LOCATION_SETTINGS)
              } catch (error: IntentSender.SendIntentException) {
                finishSettingsCheck(check, false)
              } catch (error: RuntimeException) {
                finishSettingsCheck(check, false)
              }
            }
            else -> finishSettingsCheck(check, false)
          }
        }
    } catch (error: RuntimeException) {
      finishSettingsCheck(check, false)
    }
  }

  private fun finishSettingsCheck(check: LocationSettingsCheck, enabled: Boolean) {
    if (pendingSettingsCheck !== check) return
    pendingSettingsCheck = null
    check.promise.resolve(enabled)
  }

  private fun extractArgs(config: ReadableMap) = runBlocking(Dispatchers.Default) {
    if (config.hasKey("priority")) priority = config.getInt("priority")
    if (config.hasKey("alwaysShow")) alwaysShow = config.getBoolean("alwaysShow")
    if (config.hasKey("needBle")) needBle = config.getBoolean("needBle")
  }

  private fun createLocationSettingsRequest(priority: Int, alwaysShow: Boolean, needBle: Boolean): LocationSettingsRequest {
    val locationRequest = LocationRequest.create().setPriority(priority)
    return LocationSettingsRequest.Builder()
      .setAlwaysShow(alwaysShow)
      .setNeedBle(needBle)
      .addLocationRequest(locationRequest).build()
  }

  private fun checkDeviceLocationSettings(priority: Int, alwaysShow: Boolean, needBle: Boolean) = runBlocking(Dispatchers.Default) {
    context.currentActivity?.let { activity ->
      val request = createLocationSettingsRequest(priority, alwaysShow, needBle)
      val locationSettingsResponseTask = LocationServices.getSettingsClient(activity).checkLocationSettings(request)
      val res = Arguments.createMap()
      locationSettingsResponseTask.addOnFailureListener {
        res.putBoolean("locationEnabled", false)
        sendEvent(EVENT_NAME, res)
      }
      locationSettingsResponseTask.addOnSuccessListener {
        res.putBoolean("locationEnabled", true)
        sendEvent(EVENT_NAME, res)
      }
    }
  }

  private fun requestDeviceResolutionLocationSettings(priority: Int, alwaysShow: Boolean, needBle: Boolean) = runBlocking(Dispatchers.Default) {
    context.currentActivity?.let { activity ->
      val request = createLocationSettingsRequest(priority, alwaysShow, needBle)
      val locationSettingsResponseTask = LocationServices.getSettingsClient(activity).checkLocationSettings(request)
      locationSettingsResponseTask.addOnFailureListener { exception ->
        if (exception is ResolvableApiException) try {
          exception.startResolutionForResult(activity, REQUEST_TURN_DEVICE_LOCATION_ON)
        } catch (sendEx: IntentSender.SendIntentException) {
          println("requestDeviceResolutionLocationSettings.addOnFailureListener > catch error > " + sendEx.localizedMessage)
        }
      }
    }
  }

  private fun sendEvent(eventName: String, @Nullable params: WritableMap) = runBlocking(Dispatchers.Default) {
    context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java).emit(eventName, params)
  }
}
