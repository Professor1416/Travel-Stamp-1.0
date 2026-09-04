package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.ForestPine
import com.example.ui.theme.OchreGold
import com.example.ui.theme.SageGreen
import com.example.ui.theme.Terracotta

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPage by rememberSaveable { mutableIntStateOf(0) }
    val totalPages = 3

    // Back navigation handling between onboarding pages
    BackHandler(enabled = currentPage > 0) {
        currentPage--
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Top Header: SKIP on Right for Pages 1 & 2 only (No SKIP on final Page 3)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentPage < totalPages - 1) {
                // SKIP button accessible at top-right
                TextButton(
                    onClick = onFinished,
                    modifier = Modifier.testTag("onboarding_skip_button")
                ) {
                    Text(
                        text = "SKIP",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Center Content with smooth fade animation
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(top = 48.dp, bottom = 100.dp)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    fadeIn(animationSpec = tween(240)) togetherWith fadeOut(animationSpec = tween(200))
                },
                modifier = Modifier.fillMaxWidth(),
                label = "onboarding_page_anim"
            ) { page ->
                when (page) {
                    0 -> OnboardingPageOne()
                    1 -> OnboardingPageTwo()
                    else -> OnboardingPageThree()
                }
            }
        }

        // Bottom Navigation Area: Page Indicators & Primary CTA Button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Horizontal Page Indicators (● ○ ○ / ○ ● ○ / ○ ○ ●)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                repeat(totalPages) { index ->
                    val isSelected = currentPage == index
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(if (isSelected) 24.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            // Large Bottom CTA Button
            Button(
                onClick = {
                    if (currentPage < totalPages - 1) {
                        currentPage++
                    } else {
                        onFinished()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag(
                        when (currentPage) {
                            0 -> "onboarding_get_started_button"
                            1 -> "onboarding_next_button"
                            else -> "onboarding_start_exploring_button"
                        }
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
            ) {
                Text(
                    text = when (currentPage) {
                        0 -> "NEXT →"
                        1 -> "NEXT →"
                        else -> "START EXPLORING →"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 15.sp,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Page 1: Welcome
 * "Your Journey, Your Memories, Your Collection."
 * Button: NEXT →
 */
@Composable
private fun OnboardingPageOne() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Authentic Travel Stamp Brand Symbol
        Image(
            painter = painterResource(R.drawable.travel_stamp_symbol),
            contentDescription = "Travel Stamp logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(120.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "TRAVEL STAMP",
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = FontFamily.Serif,
            color = MaterialTheme.colorScheme.onBackground,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.travel_stamp_tagline_multiline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 26.sp,
            fontSize = 16.sp
        )
    }
}

/**
 * Page 2: Create Your Journey
 * "CREATE YOUR JOURNEY"
 * 3-card stack: Trips, Moments, Checklist
 * Button: NEXT →
 */
@Composable
private fun OnboardingPageTwo() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "CREATE YOUR JOURNEY",
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Serif,
            color = MaterialTheme.colorScheme.onBackground,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Plan, record, and preserve every expedition with essential travel tools.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 3-card compact feature stack
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OnboardingFeatureCard(
                icon = Icons.Filled.Backpack,
                title = "Trips",
                description = "Plan journeys, set destinations, and track your active itineraries."
            )

            OnboardingFeatureCard(
                icon = Icons.Filled.CameraAlt,
                title = "Moments",
                description = "Capture memorable photos, milestones, and field notes along the way."
            )

            OnboardingFeatureCard(
                icon = Icons.Filled.CheckCircle,
                title = "Checklist",
                description = "Keep essential gear and packing items organized before you depart."
            )
        }
    }
}

@Composable
private fun OnboardingFeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Dark circular badge (#20322A) with SageGreen icon tint
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF20322A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = SageGreen,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

/**
 * Page 3: Travel Stamps
 * "COLLECT YOUR STAMPS"
 * Button: START EXPLORING → (Completes onboarding)
 */
@Composable
private fun OnboardingPageThree() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "COLLECT YOUR STAMPS",
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Serif,
            color = MaterialTheme.colorScheme.onBackground,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Complete your journeys, earn unique Travel Stamps, and build a passport full of memories.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Compact Commemorative Stamp Preview (Static Onboarding Sample)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF16231E)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, OchreGold.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 22.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Official Stamp Number Pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Terracotta.copy(alpha = 0.15f),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        text = "OFFICIAL STAMP #001",
                        style = MaterialTheme.typography.labelMedium,
                        color = Terracotta,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                // Sample Location Title
                Text(
                    text = "Harihar Fort",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Sample Subtitle / Region
                Text(
                    text = "Nashik, Maharashtra",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Certification Label
                Text(
                    text = "CERTIFIED EXPEDITION",
                    style = MaterialTheme.typography.labelSmall,
                    color = SageGreen,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
