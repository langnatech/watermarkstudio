package com.watermarkstudio.util

import android.app.Activity
import android.util.Log
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

object UmpConsentHelper {
    private const val TAG = "UmpConsentHelper"

    fun requestConsentAndRun(
        activity: Activity,
        onComplete: () -> Unit,
    ) {
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form error: ${formError.message}")
                    }
                    onComplete()
                }
            },
            { requestError ->
                Log.w(TAG, "Consent info update failed: ${requestError.message}")
                onComplete()
            },
        )
    }

    fun canRequestAds(activity: Activity): Boolean {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        return info.canRequestAds() ||
            info.consentStatus == ConsentInformation.ConsentStatus.NOT_REQUIRED
    }
}
