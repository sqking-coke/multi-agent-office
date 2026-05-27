package com.agentoffice.service;

import com.agentoffice.dispatch.DispatchHub;
import com.agentoffice.entity.AgentTask;
import com.agentoffice.entity.AgentTaskItem;
import com.agentoffice.mapper.AgentTaskItemMapper;
import com.agentoffice.mapper.AgentTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final DispatchHub dispatchHub;
    private final AgentTaskMapper taskMapper;
    private final AgentTaskItemMapper itemMapper;

    public AgentTask submit(String taskName, String taskContent, Long userId, Long tenantId) {
        return dispatchHub.submitTask(taskName, taskContent, userId, tenantId);
    }

    public Page<AgentTask> list(int page, int size, Integer status, String coopMode) {
        Page<AgentTask> p = new Page<>(page, size);
        LambdaQueryWrapper<AgentTask> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(AgentTask::getTaskStatus, status);
        if (coopMode != null) wrapper.eq(AgentTask::getCoopMode, coopMode);
        wrapper.orderByDesc(AgentTask::getCreateTime);
        taskMapper.selectPage(p, wrapper);
        return p;
    }

    public AgentTask getTask(Long taskId) {
        return taskMapper.selectById(taskId);
    }

    public List<AgentTaskItem> getItems(Long taskId) {
        return itemMapper.selectList(new LambdaQueryWrapper<AgentTaskItem>()
                .eq(AgentTaskItem::getTaskId, taskId));
    }

    @Transactional
    public void cancel(Long taskId) {
        dispatchHub.cancelTask(taskId);
    }

    @Transactional
    public void approveItem(Long taskItemId) {
        AgentTaskItem item = itemMapper.selectById(taskItemId);
        if (item == null) {
            throw new IllegalArgumentException("子任务不存在");
        }
        item.setStatus(2); // SUCCESS
        item.setFinishTime(LocalDateTime.now());
        itemMapper.updateById(item);
        log.info("Task item {} approved", taskItemId);
    }

    @Transactional
    public void rejectItem(Long taskItemId, String reason) {
        AgentTaskItem item = itemMapper.selectById(taskItemId);
        if (item == null) {
            throw new IllegalArgumentException("子任务不存在");
        }
        item.setStatus(3); // FAILED
        item.setErrorMsg(reason != null ? reason : "人工驳回");
        item.setFinishTime(LocalDateTime.now());
        itemMapper.updateById(item);
        log.info("Task item {} rejected: {}", taskItemId, reason);
    }
}
