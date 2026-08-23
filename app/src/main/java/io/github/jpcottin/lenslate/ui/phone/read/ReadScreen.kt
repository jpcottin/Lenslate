package io.github.jpcottin.lenslate.ui.phone.read

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.jpcottin.lenslate.R
import io.github.jpcottin.lenslate.ui.theme.LenslateTheme

/** Stateless Read screen: viewfinder (when the camera is up), hint, errors, shutter. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadScreen(
    surfaceRequest: SurfaceRequest?,
    isReading: Boolean,
    error: String?,
    onCapture: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.read_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            val label = stringResource(R.string.read_capture)
            FloatingActionButton(
                onClick = { if (!isReading) onCapture() },
                modifier = Modifier.semantics { contentDescription = label },
            ) {
                if (isReading) CircularProgressIndicator(Modifier.size(24.dp))
                else Icon(Icons.Rounded.PhotoCamera, contentDescription = null)
            }
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black),
        ) {
            if (surfaceRequest != null) {
                CameraXViewfinder(surfaceRequest = surfaceRequest, modifier = Modifier.fillMaxSize())
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(if (isReading) R.string.reading else R.string.read_hint),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReadScreenPreview() {
    LenslateTheme(dynamicColor = false) {
        ReadScreen(surfaceRequest = null, isReading = false, error = null, onCapture = {}, onBack = {})
    }
}
