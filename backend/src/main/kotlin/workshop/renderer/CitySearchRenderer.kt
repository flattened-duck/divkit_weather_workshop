package workshop.renderer

import divkit.dsl.Patch
import divkit.dsl.action
import divkit.dsl.border
import divkit.dsl.center
import divkit.dsl.color
import divkit.dsl.container
import divkit.dsl.divanPatch
import divkit.dsl.edgeInsets
import divkit.dsl.evaluate
import divkit.dsl.expression.divanExpression
import divkit.dsl.matchParentSize
import divkit.dsl.patch
import divkit.dsl.patchChange
import divkit.dsl.solidBackground
import divkit.dsl.text
import divkit.dsl.url
import divkit.dsl.vertical
import workshop.renderer.data.CitySearchViewModel

class CitySearchRenderer(private val vm: CitySearchViewModel) {

    fun render(): Patch = divanPatch {
        val items = if (vm.rows.isEmpty()) {
            listOf(
                text(
                    text = vm.emptyLabel,
                    width = matchParentSize(),
                    fontSize = 15,
                    textColor = color(Colors.GRAY),
                    textAlignmentHorizontal = center,
                    paddings = edgeInsets(start = 14, top = 12, end = 14, bottom = 12),
                ),
            )
        } else {
            vm.rows.map { row ->
                text(
                    text = row.label,
                    width = matchParentSize(),
                    fontSize = 16,
                    textColor = color(Colors.BLUE),
                    paddings = edgeInsets(start = 14, top = 12, end = 14, bottom = 12),
                    margins = edgeInsets(top = 6),
                    border = border(cornerRadius = 10),
                    background = listOf(
                        solidBackground().evaluate(color = Theme.SURFACE.divanExpression()),
                    ),
                    action = action(
                        logId = "set_city",
                        url = url(row.actionUrl),
                    ),
                )
            }
        }
        // A DivPatch change REPLACES the matched div with `items` (Patch.Change.id = "id of the
        // element to be replaced"). Replacing the results container with the bare rows would
        // discard the container AND its id, so re-target/find would break. Wrap the rows in a
        // fresh container carrying the same id so `city_search_results` survives each patch.
        patch(
            changes = listOf(
                patchChange(
                    id = "city_search_results",
                    items = listOf(
                        container(
                            id = "city_search_results",
                            orientation = vertical,
                            width = matchParentSize(),
                            margins = edgeInsets(top = 8),
                            items = items,
                        ),
                    ),
                ),
            ),
        )
    }.patch
}
