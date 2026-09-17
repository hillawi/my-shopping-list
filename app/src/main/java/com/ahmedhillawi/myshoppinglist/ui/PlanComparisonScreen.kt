package com.ahmedhillawi.myshoppinglist.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ahmedhillawi.myshoppinglist.R

// Extracted out of AccountScreen to keep that composable's own cognitive complexity down -- a
// leaf drill-down from AccountScreen's Plan row (self-contained sub-navigation, same
// screen-swap-on-a-boolean pattern AccountScreen itself is reached by), not a sibling screen
// ShoppingListScreen needs to know about. Feature lists here must stay in sync with PLANS.md.
//
// No payment processor is integrated (see supabase/upgrade_household_plan.sql's own comment --
// upgrades happen manually today after the household's owner pays out-of-band) -- the upgrade
// button is deliberately disabled with a "Coming Soon" label rather than wired to a fake action.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanComparisonScreen(currentPlan: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plans_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_button))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PlanCard(
                title = stringResource(R.string.plan_free),
                isCurrent = currentPlan != "paid",
                features = listOf(
                    stringResource(R.string.plan_feature_one_household),
                    stringResource(R.string.plan_feature_two_members),
                    stringResource(R.string.plan_feature_realtime_list),
                    stringResource(R.string.plan_feature_30_day_history)
                )
            )
            PlanCard(
                title = stringResource(R.string.plan_paid),
                isCurrent = currentPlan == "paid",
                features = listOf(
                    stringResource(R.string.plan_feature_ten_members),
                    stringResource(R.string.plan_feature_unlimited_history),
                    stringResource(R.string.plan_feature_archive)
                ),
                showUpgradeButton = currentPlan != "paid"
            )
        }
    }
}

@Composable
private fun PlanCard(
    title: String,
    isCurrent: Boolean,
    features: List<String>,
    showUpgradeButton: Boolean = false
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(
            width = if (isCurrent) 2.dp else 1.dp,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (isCurrent) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = stringResource(R.string.current_plan_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            features.forEach { feature ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(feature, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (showUpgradeButton) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(stringResource(R.string.upgrade_coming_soon_button))
                }
            }
        }
    }
}
