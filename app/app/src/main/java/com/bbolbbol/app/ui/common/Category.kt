package com.bbolbbol.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Museum
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.TempleBuddhist
import androidx.compose.ui.graphics.vector.ImageVector

/** 카테고리 코드(서버 place_categories.code)를 아이콘과 한글 이름으로 바꾼다. 모르는 코드는 기본 핀. */
fun categoryIcon(code: String?): ImageVector = when (code) {
    "CAFE" -> Icons.Filled.LocalCafe
    "PARK" -> Icons.Filled.Park
    "SHOPPING" -> Icons.Filled.ShoppingBag
    "MOVIE" -> Icons.Filled.Movie
    "LIBRARY" -> Icons.Filled.LocalLibrary
    "STUDY_CAFE" -> Icons.Filled.School
    "MUSEUM" -> Icons.Filled.Museum
    "HERITAGE" -> Icons.Filled.TempleBuddhist
    else -> Icons.Filled.Place
}
