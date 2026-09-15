package io.github.ashishkupadhyay.mantis.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.ext.list.withoutPackage
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Test

/** Source-level conventions (doc 02 §3, §9). Scoped to production code; tests are exempt where noted. */
class CodeConventionsTest {

    private val base = "io.github.ashishkupadhyay.mantis"
    private val scope = Konsist.scopeFromProject(sourceSetName = "main")

    @Test
    fun `no android util Log anywhere - use the Logger facade`() {
        scope.imports.assertFalse { it.name == "android.util.Log" }
    }

    @Test
    fun `feature code never imports data or database internals`() {
        scope.files.withPackage("$base.feature..").assertFalse { file ->
            file.imports.any { it.name.startsWith("$base.core.data.") || it.name.startsWith("$base.core.database.") }
        }
    }

    @Test
    fun `KeyVault is only referenced inside core llm`() {
        scope.files.withoutPackage("$base.core.llm..").assertFalse { file ->
            file.imports.any { it.name.endsWith(".KeyVault") } || file.text.contains("KeyVault(")
        }
    }

    @Test
    fun `appfunctions APIs are only used in the appfunctions module`() {
        scope.files.withoutPackage("$base.appfunctions..").assertFalse { file ->
            file.imports.any { it.name.startsWith("androidx.appfunctions") }
        }
    }

    @Test
    fun `ML Kit is only used in core receipts`() {
        scope.files.withoutPackage("$base.core.receipts..").assertFalse { file ->
            file.imports.any { it.name.startsWith("com.google.mlkit") || it.name.startsWith("com.google.android.gms.mlkit") }
        }
    }

    @Test
    fun `raw MaterialTheme and material3 components are used only through core designsystem and core ui`() {
        // Features must go through the design-system wrappers (doc 05 §3). MaterialTheme tokens are allowed everywhere.
        val wrapped = setOf(
            "FloatingActionButtonMenu", "HorizontalFloatingToolbar", "VerticalFloatingToolbar", "ButtonGroup",
            "LoadingIndicator", "ContainedLoadingIndicator", "LinearWavyProgressIndicator", "CircularWavyProgressIndicator",
            "SplitButtonLayout", "ToggleButton", "LargeFlexibleTopAppBar", "MediumFlexibleTopAppBar",
        )
        scope.files.withPackage("$base.feature..").assertFalse { file ->
            file.imports.any { imp -> wrapped.any { imp.name == "androidx.compose.material3.$it" } }
        }
    }

    @Test
    fun `no WebView in the app`() {
        scope.imports.assertFalse { it.name.startsWith("android.webkit.WebView") }
    }

    @Test
    fun `classes named UseCase live in core domain and expose invoke`() {
        scope.classes().filter { it.name.endsWith("UseCase") }.assertTrue { cls ->
            cls.resideInPackage("$base.core.domain..") && cls.hasFunction { it.name == "invoke" }
        }
    }
}
