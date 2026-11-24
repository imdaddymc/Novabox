package com.novabox.tv.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.novabox.tv.data.AppGraph
import com.novabox.tv.data.Channel
import com.novabox.tv.data.EpgEntry
import com.novabox.tv.data.Genre
import com.novabox.tv.data.Profile
import com.novabox.tv.data.VodItem
import com.novabox.tv.data.VodKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun vm(): AppViewModel {
        AppGraph.init(ApplicationProvider.getApplicationContext())
        val p = Profile(name = "Living room", portalUrl = "http://portal.example:8080/c/", mac = "00:1A:79:4F:22:10")
        AppGraph.profiles.upsert(p); AppGraph.profiles.activeId = p.id
        AppGraph.favorites.switchProfile(p.id)
        return AppViewModel().apply {
            val names = listOf("BBC One HD", "ITV 1", "Channel 4", "Sky News", "Sky Sports Main Event", "Eurosport 1", "Discovery", "National Geographic", "Cartoon Network", "MTV Hits", "Film4", "Comedy Central")
            genres = listOf(Genre("1", "Entertainment"), Genre("2", "News"), Genre("3", "Sports"), Genre("4", "Documentary"), Genre("5", "Kids"), Genre("6", "Music"))
            channels = names.mapIndexed { i, n -> Channel("$i", n, 101 + i, "cmd", "", "${i % 6 + 1}", false) }
            val now = System.currentTimeMillis()
            epg["0"] = listOf(
                EpgEntry("The One Show", "Live magazine programme with stories from around the country.", now - 20 * 60000, now + 10 * 60000),
                EpgEntry("EastEnders", "", now + 10 * 60000, now + 40 * 60000),
                EpgEntry("BBC News at Ten", "", now + 40 * 60000, now + 70 * 60000),
            )
            AppGraph.favorites.toggle(channels[3])
            backStack.add(Route.Home)
        }
    }

    private fun save(name: String) {
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun live() {
        val vm = vm()
        rule.setContent { NovaTheme { ScreenBackground { HomeScreen(vm, {}, {}) } } }
        save("1-live")
    }

    @Test fun movies() {
        val vm = vm()
        vm.tab = HomeTab.MOVIES
        vm.categories[VodKind.MOVIE] = listOf(Genre("*", "All"), Genre("1", "Action"), Genre("2", "Comedy"), Genre("3", "Drama"), Genre("4", "Family"))
        vm.selectedCategory[VodKind.MOVIE] = "*"
        val l = vm.list(VodKind.MOVIE, "*")
        l.hasMore = false
        l.items.addAll((1..12).map { VodItem("$it", VodKind.MOVIE, "Movie title $it", "", "", "", "202${it % 5}", "7.${it % 9}", "", "", "", "", emptyList(), "*") })
        rule.setContent { NovaTheme { ScreenBackground { HomeScreen(vm, {}, {}) } } }
        save("2-movies")
    }

    @Test fun search() {
        val vm = vm()
        vm.tab = HomeTab.SEARCH
        vm.searchQuery = "sky"
        vm.searchChannels = vm.channels.filter { it.name.contains("Sky") }
        vm.searchMovies = (1..5).map { VodItem("$it", VodKind.MOVIE, "Skyfall $it", "", "", "", "2012", "", "", "", "", "", emptyList(), "*") }
        rule.setContent { NovaTheme { ScreenBackground { HomeScreen(vm, {}, {}) } } }
        save("3-search")
    }

    @Test fun settings() {
        val vm = vm()
        vm.tab = HomeTab.SETTINGS
        rule.setContent { NovaTheme { ScreenBackground { HomeScreen(vm, {}, {}) } } }
        save("4-settings")
    }

    @Test fun profile() {
        val vm = vm()
        rule.setContent { NovaTheme { ScreenBackground { ProfileEditScreen(vm, null, true) } } }
        save("5-profile")
    }

    @Test fun details() {
        val vm = vm()
        val item = VodItem("9", VodKind.SERIES, "The Long Road", "", "", "Two brothers cross the country in a borrowed van, chasing a story nobody else believes.", "2024", "8.2", "Drama", "A. Director", "Actor One, Actor Two", "", emptyList(), "*")
        vm.seasons = listOf(com.novabox.tv.data.Season("1", "Season 1", "c", (1..8).toList()), com.novabox.tv.data.Season("2", "Season 2", "c", (1..6).toList()))
        rule.setContent { NovaTheme { ScreenBackground { DetailsScreen(vm, item) {} } } }
        save("6-details")
    }
}
