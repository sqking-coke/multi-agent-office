package com.agentoffice.dispatch;

import com.agentoffice.agent.AgentConfig;
import com.agentoffice.agent.registry.AgentRegistry;
import com.agentoffice.entity.AgentTaskItem;
import com.agentoffice.entity.TaskDecomposeTemplate;
import com.agentoffice.mapper.TaskDecomposeTemplateMapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskDecomposer {

    private final TaskDecomposeTemplateMapper templateMapper;
    private final AgentRegistry agentRegistry;

    /**
     * Hybrid decomposition: rule-based templates first, LLM fallback.
     */
    public DecomposeResult decompose(String taskName, String taskContent, Long tenantId) {
        // Layer 1: Rule-based template matching
        List<TaskDecomposeTemplate> templates = templateMapper.selectList(
                new LambdaQueryWrapper<TaskDecomposeTemplate>()
                        .eq(TaskDecomposeTemplate::getStatus, 1)
                        .orderByAsc(TaskDecomposeTemplate::getPriority));

        for (TaskDecomposeTemplate template : templates) {
            if (matchesKeywords(taskName + " " + (taskContent != null ? taskContent : ""),
                    template.getTriggerKeywords())) {
                log.info("Template matched: {} for task '{}'", template.getTemplateName(), taskName);
                return buildFromTemplate(template);
            }
        }

        // Layer 2: LLM-based decomposition (not implemented as LLM layer not yet ready)
        // For now, return a simple single-item decomposition as fallback
        log.info("No template matched for task '{}', using default single-item decomposition", taskName);
        return buildDefaultDecomposition(taskName, taskContent);
    }

    /**
     * LLM-based decomposition with JSON Schema constraint.
     * Called when no rule template matches.
     */
    public DecomposeResult decomposeWithLlm(String taskName, String taskContent, String llmResponse) {
        try {
            JSONObject result = JSON.parseObject(llmResponse);
            JSONArray subTasks = result.getJSONArray("subTasks");
            String coopMode = result.getString("coopMode");
            if (coopMode == null || coopMode.isEmpty()) {
                coopMode = "PARALLEL";
            }

            List<DecomposedTask> tasks = new ArrayList<>();
            for (int i = 0; i < subTasks.size(); i++) {
                JSONObject st = subTasks.getJSONObject(i);
                DecomposedTask dt = new DecomposedTask();
                dt.setName(st.getString("name"));
                dt.setContent(st.getString("content"));
                dt.setRequiredCapability(st.getString("requiredCapability"));
                dt.setDependsOn(st.getJSONArray("dependsOn") != null
                        ? st.getList("dependsOn", Integer.class) : Collections.emptyList());
                tasks.add(dt);
            }

            // Validate
            String validationError = validateDecomposition(tasks);
            if (validationError != null) {
                log.warn("LLM decomposition validation failed: {}", validationError);
                return buildDefaultDecomposition(taskName, taskContent);
            }

            return new DecomposeResult(tasks, coopMode.toUpperCase(), "llm");
        } catch (Exception e) {
            log.error("Failed to parse LLM decomposition response", e);
            return buildDefaultDecomposition(taskName, taskContent);
        }
    }

    private String validateDecomposition(List<DecomposedTask> tasks) {
        if (tasks.isEmpty() || tasks.size() > 20) {
            return "Task count out of range (1-20): " + tasks.size();
        }
        Set<String> allCapabilities = agentRegistry.getAllCapabilities();
        for (DecomposedTask task : tasks) {
            if (task.getRequiredCapability() != null
                    && !allCapabilities.contains(task.getRequiredCapability())) {
                return "No agent for capability: " + task.getRequiredCapability();
            }
        }
        // DAG cycle detection
        for (int i = 0; i < tasks.size(); i++) {
            for (int dep : tasks.get(i).getDependsOn()) {
                if (dep >= tasks.size() || dep == i) {
                    return "Invalid dependency: task " + i + " depends on " + dep;
                }
            }
        }
        return null; // valid
    }

    private DecomposeResult buildDefaultDecomposition(String taskName, String taskContent) {
        DecomposedTask dt = new DecomposedTask();
        dt.setName(taskName);
        dt.setContent(taskContent);
        dt.setRequiredCapability(null); // any capable agent
        dt.setDependsOn(Collections.emptyList());
        return new DecomposeResult(Collections.singletonList(dt), "SERIAL", "default");
    }

    private DecomposeResult buildFromTemplate(TaskDecomposeTemplate template) {
        JSONArray arr = JSON.parseArray(template.getSubTasksJson());
        List<DecomposedTask> tasks = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            DecomposedTask dt = new DecomposedTask();
            dt.setName(obj.getString("name"));
            dt.setContent(obj.getString("content"));
            dt.setRequiredCapability(obj.getString("capability"));
            dt.setDependsOn(Collections.emptyList());
            tasks.add(dt);
        }
        return new DecomposeResult(tasks, template.getCoopMode(), "template:" + template.getTemplateName());
    }

    private boolean matchesKeywords(String text, String keywords) {
        if (keywords == null || keywords.isEmpty()) return false;
        String[] parts = keywords.split(",");
        for (String kw : parts) {
            if (text.contains(kw.trim())) return true;
        }
        return false;
    }

    @lombok.Data
    public static class DecomposedTask {
        private String name;
        private String content;
        private String requiredCapability;
        private List<Integer> dependsOn;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class DecomposeResult {
        private List<DecomposedTask> subTasks;
        private String coopMode;
        private String source; // template name or "llm" or "default"
    }
}
