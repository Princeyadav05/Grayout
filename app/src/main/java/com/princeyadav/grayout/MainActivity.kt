package com.princeyadav.grayout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.princeyadav.grayout.ui.theme.GrayoutTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GrayoutTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = GrayoutTheme.colors.bg,
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Grayout",
                            style = GrayoutTheme.typography.headingLarge,
                            color = GrayoutTheme.colors.text,
                        )
                    }
                }
            }
        }
    }
}
