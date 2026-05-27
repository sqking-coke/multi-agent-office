package com.agentoffice.security;

import lombok.Data;
import java.util.List;

@Data
public class JwtUserInfo {
    private Long userId;
    private Long tenantId;
    private String roleCode;
    private List<String> permissions;
}
