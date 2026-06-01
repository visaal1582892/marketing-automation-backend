package com.medplus.marketing_automation_backend.service;

import com.medplus.marketing_automation_backend.domain.CampaignSpecMapping;
import com.medplus.marketing_automation_backend.domain.MasterItem;
import com.medplus.marketing_automation_backend.exception.BadRequestException;
import com.medplus.marketing_automation_backend.exception.ResourceNotFoundException;
import com.medplus.marketing_automation_backend.repository.CampaignSpecMappingRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CampaignSpecMappingService {

    private final CampaignSpecMappingRepository repo;

    public CampaignSpecMappingService(CampaignSpecMappingRepository repo) {
        this.repo = repo;
    }

    // ---- Business Vertical → Business Type ----

    public List<CampaignSpecMapping> listAllVerticalTypeMappings() {
        return repo.findAllVerticalTypeMappings();
    }

    public List<MasterItem> getBusinessTypesByVertical(String verticalId) {
        return repo.findBusinessTypesByVertical(verticalId);
    }

    public CampaignSpecMapping createVerticalTypeMapping(String verticalId, String typeId) {
        if (repo.existsVerticalTypeMapping(verticalId, typeId, null)) {
            throw new BadRequestException("Mapping already exists for this vertical and business type");
        }
        Integer id = repo.insertVerticalTypeMapping(verticalId, typeId);
        return repo.findVerticalTypeMappingById(id).orElseThrow();
    }

    public void deleteVerticalTypeMapping(Integer id) {
        if (repo.deleteVerticalTypeMapping(id) == 0) {
            throw new ResourceNotFoundException("Vertical-type mapping " + id + " not found");
        }
    }

    // ---- Business Type → Store Format Type ----

    public List<CampaignSpecMapping> listAllTypeFormatMappings() {
        return repo.findAllTypeFormatMappings();
    }

    public List<MasterItem> getStoreFormatsByBusinessType(String businessTypeId) {
        return repo.findStoreFormatsByBusinessType(businessTypeId);
    }

    public CampaignSpecMapping createTypeFormatMapping(String typeId, String formatId) {
        if (repo.existsTypeFormatMapping(typeId, formatId, null)) {
            throw new BadRequestException("Mapping already exists for this business type and store format");
        }
        Integer id = repo.insertTypeFormatMapping(typeId, formatId);
        return repo.findTypeFormatMappingById(id).orElseThrow();
    }

    public void deleteTypeFormatMapping(Integer id) {
        if (repo.deleteTypeFormatMapping(id) == 0) {
            throw new ResourceNotFoundException("Type-format mapping " + id + " not found");
        }
    }
}
