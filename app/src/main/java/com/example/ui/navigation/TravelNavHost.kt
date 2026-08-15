package com.example.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.AddMomentScreen
import com.example.ui.screens.CollectionScreen
import com.example.ui.screens.CreateTripScreen
import com.example.ui.screens.FinishTripScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TravelStampScreen
import com.example.ui.screens.TripCardScreen
import com.example.ui.viewmodel.TravelViewModel

object Destinations {
    const val HOME = "home"
    const val CREATE_TRIP = "create_trip"
    const val TRIP_CARD = "trip_card/{tripId}"
    const val ADD_MOMENT = "add_moment/{tripId}"
    const val FINISH_TRIP = "finish_trip/{tripId}"
    const val TRAVEL_STAMP = "travel_stamp/{tripId}"
    const val COLLECTION = "collection"
    const val SETTINGS = "settings"

    fun tripCard(tripId: Long) = "trip_card/$tripId"
    fun addMoment(tripId: Long) = "add_moment/$tripId"
    fun finishTrip(tripId: Long) = "finish_trip/$tripId"
    fun travelStamp(tripId: Long) = "travel_stamp/$tripId"
}

@Composable
fun TravelNavHost(
    viewModel: TravelViewModel,
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Destinations.HOME,
        modifier = modifier.fillMaxSize(),
        enterTransition = { fadeIn(animationSpec = tween(220)) },
        exitTransition = { fadeOut(animationSpec = tween(220)) },
        popEnterTransition = { fadeIn(animationSpec = tween(220)) },
        popExitTransition = { fadeOut(animationSpec = tween(220)) }
    ) {
        composable(Destinations.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onCreateTripClick = {
                    navController.navigate(Destinations.CREATE_TRIP)
                },
                onTripClick = { tripId ->
                    viewModel.selectTrip(tripId)
                    navController.navigate(Destinations.tripCard(tripId))
                },
                onCollectionClick = {
                    navController.navigate(Destinations.COLLECTION)
                },
                onSettingsClick = {
                    navController.navigate(Destinations.SETTINGS)
                },
                onStampClick = { tripId ->
                    viewModel.selectTrip(tripId)
                    navController.navigate(Destinations.travelStamp(tripId))
                }
            )
        }

        composable(Destinations.CREATE_TRIP) {
            CreateTripScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onTripCreated = { tripId ->
                    viewModel.selectTrip(tripId)
                    navController.navigate(Destinations.tripCard(tripId)) {
                        popUpTo(Destinations.HOME)
                    }
                }
            )
        }

        composable(
            route = Destinations.TRIP_CARD,
            arguments = listOf(navArgument("tripId") { type = NavType.LongType })
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getLong("tripId") ?: return@composable
            viewModel.selectTrip(tripId)

            TripCardScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onAddMomentClick = { id ->
                    navController.navigate(Destinations.addMoment(id))
                },
                onFinishTripClick = { id ->
                    navController.navigate(Destinations.finishTrip(id))
                },
                onViewStampClick = { id ->
                    navController.navigate(Destinations.travelStamp(id))
                }
            )
        }

        composable(
            route = Destinations.ADD_MOMENT,
            arguments = listOf(navArgument("tripId") { type = NavType.LongType })
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getLong("tripId") ?: return@composable
            AddMomentScreen(
                tripId = tripId,
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Destinations.FINISH_TRIP,
            arguments = listOf(navArgument("tripId") { type = NavType.LongType })
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getLong("tripId") ?: return@composable
            FinishTripScreen(
                tripId = tripId,
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onStampGenerated = { id ->
                    navController.navigate(Destinations.travelStamp(id)) {
                        popUpTo(Destinations.TRIP_CARD) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Destinations.TRAVEL_STAMP,
            arguments = listOf(navArgument("tripId") { type = NavType.LongType })
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getLong("tripId") ?: return@composable
            viewModel.selectTrip(tripId)

            TravelStampScreen(
                tripId = tripId,
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onViewTripCard = {
                    navController.navigate(Destinations.tripCard(tripId))
                },
                onCollectionClick = {
                    navController.navigate(Destinations.COLLECTION) {
                        popUpTo(Destinations.HOME)
                    }
                }
            )
        }

        composable(Destinations.COLLECTION) {
            CollectionScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onTripClick = { tripId ->
                    viewModel.selectTrip(tripId)
                    navController.navigate(Destinations.tripCard(tripId))
                },
                onStampClick = { tripId ->
                    viewModel.selectTrip(tripId)
                    navController.navigate(Destinations.travelStamp(tripId))
                },
                onCreateTripClick = {
                    navController.navigate(Destinations.CREATE_TRIP)
                }
            )
        }

        composable(Destinations.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSampleLoaded = { tripId ->
                    viewModel.selectTrip(tripId)
                    navController.navigate(Destinations.tripCard(tripId)) {
                        popUpTo(Destinations.HOME)
                    }
                }
            )
        }
    }
}
