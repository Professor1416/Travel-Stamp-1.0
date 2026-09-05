package com.example.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.poster.PosterTemplate
import com.example.ui.poster.StampEditionFormat
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.AddMomentScreen
import com.example.ui.screens.CollectionScreen
import com.example.ui.screens.CreateTripScreen
import com.example.ui.screens.FinishTripScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PosterExportScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TravelStampScreen
import com.example.ui.screens.TripCardScreen
import com.example.ui.viewmodel.TravelViewModel

object Destinations {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CREATE_TRIP = "create_trip"
    const val TRIP_CARD = "trip_card/{tripId}"
    const val ADD_MOMENT = "add_moment/{tripId}"
    const val EDIT_MOMENT = "edit_moment/{tripId}/{momentId}"
    const val FINISH_TRIP = "finish_trip/{tripId}"
    const val TRAVEL_STAMP = "travel_stamp/{tripId}"
    const val COLLECTION = "collection"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val POSTER_EXPORT = "poster_export/{tripId}?format={format}&template={template}"

    fun tripCard(tripId: Long) = "trip_card/$tripId"
    fun addMoment(tripId: Long) = "add_moment/$tripId"
    fun editMoment(tripId: Long, momentId: Long) = "edit_moment/$tripId/$momentId"
    fun finishTrip(tripId: Long) = "finish_trip/$tripId"
    fun travelStamp(tripId: Long) = "travel_stamp/$tripId"
    fun posterExport(
        tripId: Long,
        format: StampEditionFormat = StampEditionFormat.PORTRAIT,
        template: PosterTemplate = PosterTemplate.PHOTO_STAMP
    ) = "poster_export/$tripId?format=${format.name}&template=${template.name}"
}

@Composable
fun TravelNavHost(
    viewModel: TravelViewModel,
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    val initialOnboardingCompleted = remember { viewModel.hasCompletedOnboarding.value }
    val startDest = if (initialOnboardingCompleted) Destinations.HOME else Destinations.ONBOARDING

    val hasCompletedOnboarding by viewModel.hasCompletedOnboarding.collectAsStateWithLifecycle()
    val pendingReminderTripId by viewModel.pendingReminderTripId.collectAsStateWithLifecycle()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val rootRoutes = remember { setOf(Destinations.HOME, Destinations.COLLECTION, Destinations.SETTINGS) }
    val isRootDestination = currentRoute in rootRoutes

    LaunchedEffect(pendingReminderTripId, hasCompletedOnboarding, currentRoute) {
        val targetTripId = pendingReminderTripId
        if (currentRoute != null && hasCompletedOnboarding && targetTripId != null) {
            val isValid = viewModel.validateTripForNavigation(targetTripId)
            if (isValid) {
                viewModel.selectTrip(targetTripId)
                val currentTripCardRoute = Destinations.tripCard(targetTripId)
                val currentTripIdArg = navBackStackEntry?.arguments?.getLong("tripId")

                if (currentRoute != Destinations.TRIP_CARD || currentTripIdArg != targetTripId) {
                    try {
                        navController.navigate(currentTripCardRoute) {
                            popUpTo(Destinations.HOME) {
                                inclusive = false
                            }
                            launchSingleTop = true
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            viewModel.clearPendingReminderTripId()
        }
    }

    fun navigateToRootTab(targetRoute: String) {
        if (currentRoute != targetRoute) {
            navController.navigate(targetRoute) {
                popUpTo(Destinations.HOME) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (isRootDestination) {
                TravelBottomBar(
                    currentRoute = currentRoute,
                    onNavigateToTab = { targetRoute ->
                        navigateToRootTab(targetRoute)
                    }
                )
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { scaffoldPadding ->
        NavHost(
            navController = navController,
            startDestination = startDest,
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            enterTransition = { fadeIn(animationSpec = tween(220)) },
            exitTransition = { fadeOut(animationSpec = tween(220)) },
            popEnterTransition = { fadeIn(animationSpec = tween(220)) },
            popExitTransition = { fadeOut(animationSpec = tween(220)) }
        ) {
            composable(Destinations.ONBOARDING) {
                OnboardingScreen(
                    onFinished = {
                        viewModel.completeOnboarding()
                        navController.navigate(Destinations.HOME) {
                            popUpTo(Destinations.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }

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
                        navigateToRootTab(Destinations.COLLECTION)
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
                androidx.compose.runtime.LaunchedEffect(tripId) {
                    viewModel.selectTrip(tripId)
                }

                TripCardScreen(
                    viewModel = viewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onAddMomentClick = { id ->
                        navController.navigate(Destinations.addMoment(id))
                    },
                    onEditMomentClick = { tripIdParam, momentIdParam ->
                        navController.navigate(Destinations.editMoment(tripIdParam, momentIdParam))
                    },
                    onFinishTripClick = { id ->
                        navController.navigate(Destinations.finishTrip(id))
                    },
                    onViewStampClick = { id ->
                        navController.navigate(Destinations.travelStamp(id))
                    },
                    onCreatePosterClick = { id ->
                        navController.navigate(Destinations.posterExport(id))
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
                route = Destinations.EDIT_MOMENT,
                arguments = listOf(
                    navArgument("tripId") { type = NavType.LongType },
                    navArgument("momentId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val tripId = backStackEntry.arguments?.getLong("tripId") ?: return@composable
                val momentId = backStackEntry.arguments?.getLong("momentId") ?: return@composable
                AddMomentScreen(
                    tripId = tripId,
                    momentId = momentId,
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
                androidx.compose.runtime.LaunchedEffect(tripId) {
                    viewModel.selectTrip(tripId)
                }

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
                        navigateToRootTab(Destinations.COLLECTION)
                    },
                    onCreatePosterClick = { id ->
                        navController.navigate(Destinations.posterExport(id))
                    },
                    onCreateEditionClick = { id, format, template ->
                        navController.navigate(Destinations.posterExport(id, format, template))
                    }
                )
            }

            composable(
                route = Destinations.POSTER_EXPORT,
                arguments = listOf(
                    navArgument("tripId") { type = NavType.LongType },
                    navArgument("format") {
                        type = NavType.StringType
                        defaultValue = "PORTRAIT"
                    },
                    navArgument("template") {
                        type = NavType.StringType
                        defaultValue = "PHOTO_STAMP"
                    }
                )
            ) { backStackEntry ->
                val tripId = backStackEntry.arguments?.getLong("tripId") ?: return@composable
                val formatStr = backStackEntry.arguments?.getString("format") ?: "PORTRAIT"
                val templateStr = backStackEntry.arguments?.getString("template") ?: "PHOTO_STAMP"
                val initialFormat = try { StampEditionFormat.valueOf(formatStr) } catch (_: Exception) { StampEditionFormat.PORTRAIT }
                val initialTemplate = try { PosterTemplate.valueOf(templateStr) } catch (_: Exception) { PosterTemplate.PHOTO_STAMP }

                androidx.compose.runtime.LaunchedEffect(tripId) {
                    viewModel.selectTrip(tripId)
                }

                PosterExportScreen(
                    tripId = tripId,
                    viewModel = viewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    initialFormat = initialFormat,
                    initialTemplate = initialTemplate
                )
            }

            composable(Destinations.COLLECTION) {
                CollectionScreen(
                    viewModel = viewModel,
                    onNavigateBack = null,
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
                    onNavigateBack = null,
                    onAboutClick = {
                        navController.navigate(Destinations.ABOUT)
                    }
                )
            }

            composable(Destinations.ABOUT) {
                AboutScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}

