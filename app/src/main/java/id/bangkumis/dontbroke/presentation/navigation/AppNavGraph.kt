package id.bangkumis.dontbroke.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import id.bangkumis.dontbroke.presentation.addtransaction.AddTransactionScreen
import id.bangkumis.dontbroke.presentation.home.HomeScreen

private const val HOME = "home"
private const val ADD = "add"

@Composable
fun AppNavGraph() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = HOME) {
        composable(HOME) {
            HomeScreen(
                onAddTransaction = { nav.navigate(ADD) },
                onEditTransaction = { id -> nav.navigate("$ADD?id=$id") }
            )
        }
        // one route for both add and edit — "add" falls back to id = 0
        composable(
            route = "$ADD?id={id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L })
        ) { AddTransactionScreen(onBack = { nav.popBackStack() }) }
    }
}
