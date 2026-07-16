package workshop.renderer.data

data class CityRowVm(val label: String, val actionUrl: String)

data class CitySearchViewModel(val rows: List<CityRowVm>, val emptyLabel: String)
