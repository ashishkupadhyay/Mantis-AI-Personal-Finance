package io.github.ashishkupadhyay.mantis.core.testing.screenshot

import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.ThresholdValidator

/**
 * Shared Roborazzi settings (doc 05 §10). Baselines may be recorded on a developer machine and verified on Linux
 * CI; a 0.2 % changed-pixel tolerance absorbs sub-pixel text rasterisation differences between hosts while a
 * moved button, changed colour or clipped label still fails the build.
 */
object MantisScreenshots {
    private const val CHANGE_THRESHOLD = 0.002f

    val options: RoborazziOptions = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(resultValidator = ThresholdValidator(CHANGE_THRESHOLD)),
    )
}
