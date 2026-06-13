package com.java.myapplication

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.java.myapplication.ui.PluginListScreen
import com.java.myapplication.ui.theme.MyApplicationTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        Log.d("MainActivity", "Shizuku permission result: requestCode=$requestCode, grantResult=$grantResult")
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                PluginListScreen()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MyApplicationTheme {
        PluginListScreen()
    }
}