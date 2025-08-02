package com.example.core.api.mappers

import com.example.core.domain.Page
import com.example.generated.api.models.PaginationMetadataContract

/**
 * Mapper for converting from our Generic Domain [Page] class to contract format.
 */
object PaginationContractMapper {
    fun toContract(page: Page<*>) = PaginationMetadataContract(
        page = page.pageNumber,
        pageSize = page.pageSize,
        totalPages = page.totalPages,
        totalRows = page.totalRows,
    )
}
