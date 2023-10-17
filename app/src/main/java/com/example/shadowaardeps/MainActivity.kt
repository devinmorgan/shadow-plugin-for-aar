package com.example.shadowaardeps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.shadowaardeps.ui.theme.ShadowAARDepsTheme


import androidx.compose.foundation.layout.*

// Import Composable components for Button
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

val clientToken = "fake_client_token"

class MainActivity : ComponentActivity() {
    private var _loggerV1: com.datadog.android.log.Logger? = null;

    private fun _initLoggerV1() {
        val configuration = com.datadog.android.core.configuration.Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = false
        ).build()
        val credentials = com.datadog.android.core.configuration.Credentials(
            clientToken = clientToken,
            envName = "sandbox",
            variant = "sandbox",
            rumApplicationId = null,
            serviceName = "android-sdk"
        )
        com.datadog.android.Datadog.initialize(this, credentials, configuration, com.datadog.android.privacy.TrackingConsent.GRANTED)
        _loggerV1 = com.datadog.android.log.Logger.Builder()
            .setNetworkInfoEnabled(true)
            .setLogcatLogsEnabled(false)
            .setDatadogLogsEnabled(true)
            .setBundleWithTraceEnabled(true)
            .setLoggerName("ForageSDK")
            .build()
    }

    private var _loggerV2: com.example.shadowaardeps.datadog.android.log.Logger? = null
    private fun _initLoggerV2() {
        val configuration = com.example.shadowaardeps.datadog.android.core.configuration.Configuration.Builder(
            clientToken = clientToken,
            env = "dev",
            variant = "dev"
        ).build()
        com.example.shadowaardeps.datadog.android.Datadog.initialize(this, configuration, com.example.shadowaardeps.datadog.android.privacy.TrackingConsent.GRANTED)
        val logsConfig = com.example.shadowaardeps.datadog.android.log.LogsConfiguration.Builder().build()
        com.example.shadowaardeps.datadog.android.log.Logs.enable(logsConfig)

        _loggerV2 = com.example.shadowaardeps.datadog.android.log.Logger.Builder()
            .setNetworkInfoEnabled(true)
            .setLogcatLogsEnabled(false)
            .setBundleWithTraceEnabled(true)
            .setName("ForageSDK")
            .setService("android-sdk")
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ShadowAARDepsTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Use a Column to organize the content vertically
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Greeting("Android")
                        Spacer(modifier = Modifier.height(16.dp))
                        HelloWorldButton()
                    }
                }
            }
        }

        _initLoggerV1()
        _initLoggerV2()
        _loggerV2?.i("This is a test log from dd-sdk-android-logs:2.2.0!!")
        _loggerV1?.i("This is a test log from dd-sdk-android:1.19.2")
    }

    private var count: Int = 0;

    @Composable
    fun HelloWorldButton() {
        Button(
            onClick = {
                // This block executes when the button is clicked
                count += 1
                println("hello world $count")
                if (count % 2 == 0) {
                    _loggerV1?.i("[dd-sdk-android:1.19.2] click count: $count")
                } else {
                    _loggerV2?.i("[dd-sdk-android-logs:2.2.0] click count $count")
                }
            },
            colors = ButtonDefaults.buttonColors()
        ) {
            Text("Click Me! ($count times)")
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ShadowAARDepsTheme {
        Greeting("Android")
    }
}