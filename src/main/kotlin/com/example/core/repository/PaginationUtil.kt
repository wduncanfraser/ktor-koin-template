package com.example.core.repository

object PaginationUtil {
    fun calculateTotalPages(totalRows: Int, pageSize: Int) = (totalRows + pageSize - 1) / pageSize
    fun calculateOffset(page: Int, pageSize: Int) = (page - 1) * pageSize
}
