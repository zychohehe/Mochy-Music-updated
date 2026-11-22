// SimilarRecommendation.kt

package com.kiyo.music.models

import com.music.innertube.models.YTItem
import com.kiyo.music.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
