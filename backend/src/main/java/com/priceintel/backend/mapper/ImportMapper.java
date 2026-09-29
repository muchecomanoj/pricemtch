package com.priceintel.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.priceintel.backend.dto.response.ImportErrorResponse;
import com.priceintel.backend.dto.response.ImportJobResponse;
import com.priceintel.backend.entity.ImportError;
import com.priceintel.backend.entity.ImportJob;

/**
 * MapStruct mapper for import job / error entities to their response DTOs.
 */
@Mapper(componentModel = "spring")
public interface ImportMapper {

    @Mapping(target = "progressPercent", expression = "java(job.getProgressPercent())")
    ImportJobResponse toJobResponse(ImportJob job);

    ImportErrorResponse toErrorResponse(ImportError error);
}
