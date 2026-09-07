package app.posato.feature.targets.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.posato.core.designsystem.PosatoTab
import app.posato.core.designsystem.PosatoTabBar

@Composable
internal fun TargetsCategoryTabs(
    category: TargetsCategory,
    websiteCount: Int,
    applicationCount: Int,
    onSelect: (TargetsCategory) -> Unit,
) {
    PosatoTabBar(Modifier.fillMaxWidth()) {
        PosatoTab(
            selected = category == TargetsCategory.WEBSITES,
            onClick = { onSelect(TargetsCategory.WEBSITES) },
            modifier = Modifier.weight(1f),
            countContent = { Text(websiteCount.toString()) },
        ) { Text("Websites") }
        PosatoTab(
            selected = category == TargetsCategory.APPLICATIONS,
            onClick = { onSelect(TargetsCategory.APPLICATIONS) },
            modifier = Modifier.weight(1f),
            countContent = { Text(applicationCount.toString()) },
        ) { Text("Apps") }
    }
}
