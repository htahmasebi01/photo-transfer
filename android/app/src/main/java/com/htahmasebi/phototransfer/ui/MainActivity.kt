package com.htahmasebi.phototransfer.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.htahmasebi.phototransfer.PhotoTransferApplication

class MainActivity : ComponentActivity() {

    private val viewModel: TransferViewModel by viewModels {
        TransferViewModel.factory((application as PhotoTransferApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    TransferScreen(viewModel)
                }
            }
        }
    }
}
