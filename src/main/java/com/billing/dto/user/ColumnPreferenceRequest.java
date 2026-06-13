package com.billing.dto.user;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ColumnPreferenceRequest {
    private List<String> visibleColumns;
}
