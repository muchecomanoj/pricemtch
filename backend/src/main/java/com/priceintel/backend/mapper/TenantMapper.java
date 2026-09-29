package com.priceintel.backend.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.priceintel.backend.dto.request.UpdateTenantRequest;
import com.priceintel.backend.dto.response.TenantResponse;
import com.priceintel.backend.entity.Tenant;

/**
 * MapStruct mapper for {@link Tenant}.
 */
@Mapper(componentModel = "spring")
public interface TenantMapper {

    TenantResponse toResponse(Tenant tenant);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "companyCode", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "subscriptionPlan", ignore = true)
    @Mapping(target = "subscriptionStatus", ignore = true)
    @Mapping(target = "subscriptionStartDate", ignore = true)
    @Mapping(target = "subscriptionEndDate", ignore = true)
    @Mapping(target = "maxUsers", ignore = true)
    void updateTenantFromRequest(UpdateTenantRequest request, @MappingTarget Tenant tenant);
}
