package com.example.core.domain

/**
 * Model data and metadata that describes the current page of items.
 */
data class Page<T>(
    val data: List<T>,
    val pageNumber: Int,
    val pageSize: Int,
    val totalRows: Int,
    val totalPages: Int,
)
