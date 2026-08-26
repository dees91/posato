package app.posato.di

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph

@DependencyGraph
interface IosApplicationGraph : ApplicationGraph

fun createIosApplicationGraph(): IosApplicationGraph {
    return createGraph()
}
