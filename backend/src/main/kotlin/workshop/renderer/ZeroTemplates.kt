package workshop.renderer

import divkit.dsl.Container
import divkit.dsl.Div
import divkit.dsl.EdgeInsets
import divkit.dsl.Size
import divkit.dsl.Template
import divkit.dsl.border
import divkit.dsl.container
import divkit.dsl.edgeInsets
import divkit.dsl.extension
import divkit.dsl.fill
import divkit.dsl.fixedSize
import divkit.dsl.horizontal
import divkit.dsl.image
import divkit.dsl.matchParentSize
import divkit.dsl.scope.DivScope
import divkit.dsl.template
import divkit.dsl.url

/**
 * Process-lifetime singleton templates for the zero-state skeleton card's repeated rows (hourly
 * gallery cells, daily forecast rows). Built once via `by lazy`; [WeatherZeroRenderer] only
 * renders them per request, matching the Yandex mapi `Templates.kt` convention.
 */
object ZeroTemplates {

    object HourCellSkeleton {
        val template: Template<Div> by lazy {
            template(name = "hour_cell_skeleton") {
                shimmerBar(width = fixedSize(64), height = fixedSize(90))
            }
        }
    }

    object DailyRowSkeleton {
        val template: Template<Container> by lazy {
            template(name = "daily_row_skeleton") {
                container(
                    orientation = horizontal,
                    width = matchParentSize(),
                    paddings = edgeInsets(top = 10, bottom = 10, start = 8, end = 8),
                    items = listOf(shimmerBar(width = matchParentSize(), height = fixedSize(20))),
                )
            }
        }
    }
}

// DivKit treats the `empty://` scheme as "no image": the image is never loaded (so the shimmer
// extension, which early-returns once isImageLoaded/isImagePreview, keeps animating) AND it does
// not count as a load error, so no visual-error badge appears (unlike a real DNS-failing URL such
// as an .invalid TLD).
internal const val SKELETON_IMAGE_URL = "empty://"

/** A shimmering placeholder bar: an `image` div pointed at an image that never resolves, so
 *  [com.yandex.div.shimmer.DivShimmerExtensionHandler] never sees a loaded image and the
 *  shimmer animation never stops. */
internal fun DivScope.shimmerBar(
    width: Size,
    height: Size,
    cornerRadius: Int = 12,
    margins: EdgeInsets? = null,
): Div = image(
    imageUrl = url(SKELETON_IMAGE_URL),
    width = width,
    height = height,
    scale = fill,
    border = border(cornerRadius = cornerRadius),
    margins = margins,
    extensions = listOf(extension(id = "shimmer")),
)
