package com.example.intraplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.intraplay.ui.TvAccentCyan
import com.example.intraplay.ui.TvBackground
import com.example.intraplay.ui.TvFocusBorder
import com.example.intraplay.ui.TvSurface
import com.example.intraplay.ui.TvTextPrimary
import com.example.intraplay.ui.TvTextSecondary

data class TvCategoryItem(
    val id: String,
    val title: String,
    val icon: ImageVector
)

val DefaultTvCategories = listOf(
    TvCategoryItem("All", "All Channels", Icons.Default.LiveTv),
    TvCategoryItem("Favorites", "Favorites", Icons.Default.Favorite),
    TvCategoryItem("News", "News", Icons.Default.Newspaper),
    TvCategoryItem("Music", "Music", Icons.Default.MusicNote),
    TvCategoryItem("Sports", "Sports", Icons.Default.SportsSoccer),
    TvCategoryItem("Entertainment", "Entertainment", Icons.Default.Movie),
    TvCategoryItem("General", "General", Icons.Default.Category)
)

@Composable
fun TvCategorySidebar(
    selectedCategoryId: String,
    onCategorySelected: (String) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(220.dp)
            .background(TvBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // App Brand Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 20.dp, start = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LiveTv,
                    contentDescription = null,
                    tint = TvFocusBorder,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "INTRAPLAY",
                        color = TvTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Living Room Hub",
                        color = TvAccentCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Categories List
            DefaultTvCategories.forEach { item ->
                val isSelected = selectedCategoryId == item.id
                TvFocusableCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    backgroundColor = if (isSelected) Color(0xFF1E293B) else Color.Transparent,
                    focusedBorderColor = TvFocusBorder,
                    unfocusedBorderColor = if (isSelected) TvFocusBorder.copy(alpha = 0.5f) else Color.Transparent,
                    onClick = { onCategorySelected(item.id) }
                ) { isFocused ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = when {
                                isFocused -> TvFocusBorder
                                isSelected -> TvAccentCyan
                                else -> TvTextSecondary
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = item.title,
                            color = when {
                                isFocused -> Color.White
                                isSelected -> TvTextPrimary
                                else -> TvTextSecondary
                            },
                            fontSize = 14.sp,
                            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Settings Button at bottom of sidebar
        TvFocusableCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            shape = RoundedCornerShape(8.dp),
            backgroundColor = TvSurface,
            focusedBorderColor = TvFocusBorder,
            onClick = onSettingsClick
        ) { isFocused ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = if (isFocused) TvFocusBorder else TvTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "TV Settings",
                    color = if (isFocused) Color.White else TvTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
