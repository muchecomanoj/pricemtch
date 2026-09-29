package com.priceintel.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.priceintel.backend.dto.response.SearchHistoryResponse;
import com.priceintel.backend.entity.SearchHistory;

/**
 * MapStruct mapper for search history records.
 */
@Mapper(componentModel = "spring")
public interface SearchMapper {

    @Mapping(target = "searchedBy", source = "createdBy")
    SearchHistoryResponse toResponse(SearchHistory history);
}
