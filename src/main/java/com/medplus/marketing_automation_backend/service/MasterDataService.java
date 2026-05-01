package com.medplus.marketing_automation_backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medplus.marketing_automation_backend.domain.MasterItem;
import com.medplus.marketing_automation_backend.enums.MasterTableType;
import com.medplus.marketing_automation_backend.exception.BadRequestException;
import com.medplus.marketing_automation_backend.exception.ResourceNotFoundException;
import com.medplus.marketing_automation_backend.repository.MasterDataRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MasterDataService {

    private final MasterDataRepository repo;
    private final ObjectMapper          objectMapper = new ObjectMapper();

    public MasterDataService(MasterDataRepository repo) {
        this.repo = repo;
    }

    public List<MasterItem> list(String slug, boolean includeInactive) {
        return repo.findAll(resolve(slug), includeInactive);
    }

    public MasterItem get(String slug, String id) {
        MasterTableType t = resolve(slug);
        return repo.findById(t, id).orElseThrow(() ->
                new ResourceNotFoundException(t.tableName() + " " + id + " not found"));
    }

    public MasterItem create(String slug, MasterItem item) {
        MasterTableType t = resolve(slug);
        validateName(item);
        if (repo.existsByName(t, item.getName().trim(), null)) {
            throw new BadRequestException("'" + item.getName() + "' already exists");
        }
        item.setName(item.getName().trim());
        String id = repo.insert(t, item);
        return repo.findById(t, id).orElseThrow();
    }

    public MasterItem update(String slug, String id, MasterItem item) {
        MasterTableType t = resolve(slug);
        repo.findById(t, id).orElseThrow(() ->
                new ResourceNotFoundException(t.tableName() + " " + id + " not found"));
        validateName(item);
        if (repo.existsByName(t, item.getName().trim(), id)) {
            throw new BadRequestException("'" + item.getName() + "' already exists");
        }
        item.setName(item.getName().trim());
        repo.update(t, id, item);
        return repo.findById(t, id).orElseThrow();
    }

    /** Permanently removes the record from the database. */
    public void delete(String slug, String id) {
        MasterTableType t = resolve(slug);
        if (repo.delete(t, id) == 0) {
            throw new ResourceNotFoundException(t.tableName() + " " + id + " not found");
        }
    }

    private MasterTableType resolve(String slug) {
        return MasterTableType.fromSlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Unknown master resource '" + slug + "'"));
    }

    /**
     * Converts a JSON array of master-item IDs (and optional free-text "Other" elements)
     * into a comma-separated display string, e.g. ["1","3","Custom"] → "English, Hindi, Custom".
     * Elements that are not found in the master table are used as-is (free-text "Other" support).
     */
    public String resolveIdListToNames(String jsonArray, MasterTableType type) {
        if (jsonArray == null || jsonArray.isBlank()) return null;
        List<String> ids = parseJsonArray(jsonArray);
        if (ids.isEmpty()) return null;
        Map<String, String> nameById = repo.findAll(type, false).stream()
                .collect(Collectors.toMap(MasterItem::getId, MasterItem::getName));
        return ids.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(id -> nameById.getOrDefault(id, id))
                .collect(Collectors.joining(", "));
    }

    /**
     * Serialises a List<String> to a compact JSON array string, e.g. ["1","2","Custom"].
     * Returns null if the list is null or empty.
     */
    public String toJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return null;
        }
    }

    /** Parses a JSON array string into a List<String>; returns an empty list on error. */
    public List<String> parseJsonArray(String jsonArray) {
        if (jsonArray == null || jsonArray.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(jsonArray, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private void validateName(MasterItem item) {
        if (item == null || item.getName() == null || item.getName().isBlank()) {
            throw new BadRequestException("name is required");
        }
    }
}
