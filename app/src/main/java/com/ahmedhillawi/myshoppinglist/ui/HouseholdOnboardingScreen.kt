package com.ahmedhillawi.myshoppinglist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ahmedhillawi.myshoppinglist.R
import com.ahmedhillawi.myshoppinglist.viewmodel.HouseholdError
import com.ahmedhillawi.myshoppinglist.viewmodel.HouseholdViewModel

@Composable
fun HouseholdOnboardingScreen(viewModel: HouseholdViewModel) {
    var householdName by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var isJoining by remember { mutableStateOf(false) }
    val error by viewModel.error.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.household_onboarding_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.household_onboarding_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))

        if (isJoining) {
            OutlinedTextField(
                value = inviteCode,
                onValueChange = { inviteCode = it },
                label = { Text(stringResource(R.string.invite_code_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.joinHousehold(inviteCode) },
                enabled = inviteCode.isNotBlank() && !isSubmitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.join_household_button))
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = {
                isJoining = false
                viewModel.clearError()
            }) {
                Text(stringResource(R.string.create_household_instead))
            }
        } else {
            OutlinedTextField(
                value = householdName,
                onValueChange = { householdName = it },
                label = { Text(stringResource(R.string.household_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.createHousehold(householdName) },
                enabled = householdName.isNotBlank() && !isSubmitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.create_household_button))
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = {
                isJoining = true
                viewModel.clearError()
            }) {
                Text(stringResource(R.string.join_household_instead))
            }
        }

        error?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = when (it) {
                    HouseholdError.INVALID_CODE -> stringResource(R.string.invalid_invite_code_error)
                    HouseholdError.GENERIC -> stringResource(R.string.household_generic_error)
                },
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
