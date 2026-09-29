package com.priceintel.backend.mapper;

import org.mapstruct.Mapper;

import com.priceintel.backend.dto.response.AiExecutionResponse;
import com.priceintel.backend.entity.AiExecution;

/**
 * MapStruct mapper for AI execution audit records.
 */
@Mapper(componentModel = "spring")
public interface AiMapper {

    AiExecutionResponse toResponse(AiExecution execution);
}
