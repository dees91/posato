package app.posato.feature.targets.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.posato.core.designsystem.PosatoTab
import app.posato.core.designsystem.PosatoTabBar
import app.posato.generated.resources.Res
import app.posato.generated.resources.targets_category_apps
import app.posato.generated.resources.websites_title
import org.jetbrains.compose.resources.stringResource

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
            controlId = "websites_title",
        ) { Text(stringResource(Res.string.websites_title)) }
        PosatoTab(
            selected = category == TargetsCategory.APPLICATIONS,
            onClick = { onSelect(TargetsCategory.APPLICATIONS) },
            modifier = Modifier.weight(1f),
            countContent = { Text(applicationCount.toString()) },
            controlId = "targets_category_apps",
        ) { Text(stringResource(Res.string.targets_category_apps)) }
    }
}
