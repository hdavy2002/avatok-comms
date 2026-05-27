/*
 * MainActivity.kt — entry-point Activity. Hosts the Compose UI.
 *
 * Phase 2 commit 1 (this commit): minimal Compose scaffold — confirms
 * the project compiles, the Compose dependency graph resolves, and
 * the app launches to a recognisable AvaTok placeholder screen.
 *
 * Phase 2 commit 2: replaces the placeholder with a real contact-list
 * screen bound to AccountService / ContactService flows from
 * libjamiclient.
 *
 * Copyright (C) AvaTok Comms contributors.
 * Distributed under GPL-3.0-or-later. See ../../../LICENSE.
 */
package com.avatok.comms

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AvaTokScaffold()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvaTokScaffold() {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("AvaTok") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "AvaTok Comms — Phase 2 scaffolding",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Daemon + identity wiring lands in the next commit.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AvaTokScaffoldPreview() {
    MaterialTheme {
        AvaTokScaffold()
    }
}
