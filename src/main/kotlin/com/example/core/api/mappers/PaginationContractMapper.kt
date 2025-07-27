package com.example.core.api.mappers

import com.example.core.models.Page
import com.example.generated.api.models.PaginationMetadataContract

/**
 * Mapper for converting from our Generic Domain [com.example.core.models.Page] class to contract format.
 */
object PaginationContractMapper {
    fun toContract(page: Page<*>) = PaginationMetadataContract(
        page = page.pageNumber,
        pageSize = page.pageSize,
        totalPages = page.totalPages,
        totalRows = page.totalRows,
    )
}
