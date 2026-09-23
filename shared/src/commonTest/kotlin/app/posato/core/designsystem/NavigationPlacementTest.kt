package app.posato.core.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationPlacementTest {
    @Test
    fun `given a Mac when placed then the sidebar is used in either orientation`() {
        assertEquals(PosatoNavigationPlacement.Sidebar, navigationPlacement(PosatoDevice.Mac, landscape = true))
        assertEquals(PosatoNavigationPlacement.Sidebar, navigationPlacement(PosatoDevice.Mac, landscape = false))
    }

    @Test
    fun `given an iPad when placed then only landscape uses the sidebar`() {
        assertEquals(PosatoNavigationPlacement.Sidebar, navigationPlacement(PosatoDevice.IPad, landscape = true))
        assertEquals(PosatoNavigationPlacement.Bottom, navigationPlacement(PosatoDevice.IPad, landscape = false))
    }

    @Test
    fun `given an iPhone when placed then the bottom navigation is kept in landscape`() {
        assertEquals(PosatoNavigationPlacement.Bottom, navigationPlacement(PosatoDevice.IPhone, landscape = true))
        assertEquals(PosatoNavigationPlacement.Bottom, navigationPlacement(PosatoDevice.IPhone, landscape = false))
    }
}
