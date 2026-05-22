package com.watermarkstudio



import android.os.Bundle

import androidx.activity.ComponentActivity

import androidx.activity.compose.setContent

import androidx.activity.enableEdgeToEdge

import androidx.compose.runtime.Composable

import androidx.compose.ui.tooling.preview.Preview

import androidx.lifecycle.lifecycleScope

import com.watermarkstudio.ui.navigation.AppNavigation

import com.watermarkstudio.ui.theme.MyApplicationTheme

import com.watermarkstudio.util.AdMobHelper

import com.watermarkstudio.util.InterstitialAdLoader

import com.watermarkstudio.util.UmpConsentHelper

import kotlinx.coroutines.delay

import kotlinx.coroutines.launch



class MainActivity : ComponentActivity() {



  override fun onCreate(savedInstanceState: Bundle?) {

    super.onCreate(savedInstanceState)

    enableEdgeToEdge()



    setContent {

      MyApplicationTheme {

        AppNavigation()

      }

    }



    scheduleDeferredStartupWork()

  }



  private fun scheduleDeferredStartupWork() {

    window.decorView.post {

      lifecycleScope.launch {

        delay(STARTUP_DEFER_MS)

        UmpConsentHelper.requestConsentAndRun(this@MainActivity) {

          if (UmpConsentHelper.canRequestAds(this@MainActivity)) {

            AdMobHelper.initialize(applicationContext) {

              InterstitialAdLoader.loadAd(applicationContext)

            }

          }

        }

      }

    }

  }



  private companion object {

    const val STARTUP_DEFER_MS = 400L

  }

}



@Preview(showBackground = true)

@Composable

fun HomePreview() {

  MyApplicationTheme { AppNavigation() }

}

