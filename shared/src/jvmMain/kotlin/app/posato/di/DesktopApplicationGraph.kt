package app.posato.di

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph

@DependencyGraph
interface DesktopApplicationGraph : ApplicationGraph

fun createDesktopApplicationGraph(): DesktopApplicationGraph {
    return createGraph()
}
