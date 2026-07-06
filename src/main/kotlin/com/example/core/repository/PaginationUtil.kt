package com.example.core.repository

import com.example.core.domain.Page

object PaginationUtil {
    fun calculateTotalPages(totalRows: Int, pageSize: Int) = (totalRows + pageSize - 1) / pageSize
    fun calculateOffset(page: Int, pageSize: Int) = (page - 1) * pageSize

    /**
     * A [Page] with no rows — for callers already known in advance to have access to nothing.
     */
    fun <T> emptyPage(pageSize: Int, page: Int): Page<T> =
        Page(data = emptyList(), pageNumber = page, pageSize = pageSize, totalRows = 0, totalPages = 0)
}
