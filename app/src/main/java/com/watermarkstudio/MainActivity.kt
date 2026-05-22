package com.watermarkstudio

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.watermarkstudio.ui.navigation.AppNavigation
import com.watermarkstudio.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

  private val requestPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) {
    // Core features rely on the system photo picker and MediaStore APIs,
    // so permission denial does not block essential functionality.
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Request media permissions on Android 13+ for library browsing features
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      requestPermissionLauncher.launch(
        arrayOf(
          Manifest.permission.READ_MEDIA_IMAGES,
          Manifest.permission.READ_MEDIA_VIDEO
        )
      )
    }

    // Initialize AdMob SDK and preload interstitial ads
    com.watermarkstudio.util.AdMobHelper.initialize(this)
    com.watermarkstudio.util.InterstitialAdLoader.loadAd(this)

    setContent {
      MyApplicationTheme {
        AppNavigation()
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
fun HomePreview() {
  MyApplicationTheme { AppNavigation() }
}
