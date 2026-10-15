package com.fresko.sources.asura

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Format observé le 2026-10-15 sur https://api.asurascans.com/api/series
// (seuls les champs utiles sont déclarés ; les autres sont ignorés).

@Serializable
internal data class SeriesPageDto(
    val data: List<SeriesDto>? = null,
    val meta: MetaDto? = null,
)

@Serializable
internal data class MetaDto(
    val total: Int? = null,
    @SerialName("per_page") val perPage: Int? = null,
    @SerialName("has_more") val hasMore: Boolean? = null,
)

@Serializable
internal data class SeriesDto(
    val slug: String,
    val title: String,
    val cover: String? = null,
    @SerialName("public_url") val publicUrl: String? = null,
    @SerialName("last_chapter_at") val lastChapterAt: String? = null,
    @SerialName("latest_chapters") val latestChapters: List<ChapterDto>? = null,
)

@Serializable
internal data class ChapterDto(
    val number: Double? = null,
    val title: String? = null,
    val slug: String? = null,
    @SerialName("is_premium") val isPremium: Boolean = false,
    @SerialName("early_access_until") val earlyAccessUntil: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("series_slug") val seriesSlug: String? = null,
)

@Serializable
internal data class ChapterListDto(
    val data: List<ChapterDto>? = null,
)
