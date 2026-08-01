package com.podcast.service;

import com.podcast.domain.Role;
import com.podcast.repository.TaskRepository;
import com.podcast.repository.UserRepository;
import com.podcast.security.SecurityUtils;
import com.podcast.security.UserPrincipal;
import com.podcast.web.ApiException;
import org.springframework.stereotype.Service;

/**
 * Role-based authorization for actions, implementing README §9:
 *  - 制作人(PRODUCER)/管理员(ADMIN)：可改一切
 *  - 剪辑师(EDITOR)：只能操作分配给自己的（按 episode 任务分配判定）
 *  - 运营(OPERATOR)：只能操作分发相关，不能改标记
 *  - 主播/嘉宾(HOST)：可听审并添加标记，只能改自己创建的标记
 *  - 访客(GUEST)：只读（此处不授予写权限）
 *
 * Team isolation is handled separately by {@link AccessGuard}; this class only
 * decides whether a role may perform a given write action.
 */
@Service
public class AuthorizationService {

    private final TaskRepository taskRepo;
    private final UserRepository userRepo;

    public AuthorizationService(TaskRepository taskRepo, UserRepository userRepo) {
        this.taskRepo = taskRepo;
        this.userRepo = userRepo;
    }

    private Role currentRole() {
        UserPrincipal p = SecurityUtils.currentUser();
        if (p == null) {
            throw ApiException.unauthorized("未认证");
        }
        return Role.valueOf(p.getRole());
    }

    private boolean isProducerLevel(Role role) {
        return role == Role.ADMIN || role == Role.PRODUCER;
    }

    /**
     * May the current user create a marker on the given episode?
     * PRODUCER/ADMIN anywhere; EDITOR only on assigned episodes; HOST anywhere
     * (听审打标记); OPERATOR/GUEST never.
     */
    public void checkCanCreateMarker(Long episodeId) {
        Role role = currentRole();
        if (isProducerLevel(role) || role == Role.HOST) {
            return;
        }
        if (role == Role.EDITOR) {
            requireAssigned(episodeId);
            return;
        }
        throw ApiException.forbidden(denyReason(role));
    }

    /**
     * May the current user modify/delete an existing marker?
     * PRODUCER/ADMIN: any; EDITOR: only on assigned episodes; HOST: only markers
     * they created; OPERATOR/GUEST: never.
     */
    public void checkCanModifyMarker(Long episodeId, Long markerCreatedBy) {
        Role role = currentRole();
        if (isProducerLevel(role)) {
            return;
        }
        if (role == Role.EDITOR) {
            requireAssigned(episodeId);
            return;
        }
        if (role == Role.HOST) {
            Long uid = SecurityUtils.currentUserId();
            if (uid != null && uid.equals(markerCreatedBy)) {
                return;
            }
            throw ApiException.forbidden("主播/嘉宾只能修改自己创建的标记");
        }
        throw ApiException.forbidden(denyReason(role));
    }

    /**
     * May the current user change the STATUS of an existing marker
     * (README §4.2 标记状态流转)?
     *  - PRODUCER/ADMIN: any marker
     *  - EDITOR: only markers on episodes assigned to them
     *  - HOST: NOT allowed — 主播打的标记只有制作人能改状态；host may still
     *          edit non-status fields of their own marker via checkCanModifyMarker
     *  - additionally, a marker created by a HOST may only have its status
     *    changed by a producer-level user, regardless of who else asks.
     */
    public void checkCanChangeMarkerStatus(Long episodeId, Long markerCreatedBy) {
        Role role = currentRole();
        boolean creatorIsHost = markerCreatedBy != null
                && userRepo.findById(markerCreatedBy)
                        .map(u -> u.getRole() == Role.HOST)
                        .orElse(false);

        if (isProducerLevel(role)) {
            return; // producers/admins can flip any status
        }
        // Host-created markers: status locked to producer-level only.
        if (creatorIsHost) {
            throw ApiException.forbidden("主播/嘉宾创建的标记状态只能由制作人流转");
        }
        if (role == Role.EDITOR) {
            requireAssigned(episodeId);
            return;
        }
        throw ApiException.forbidden(denyReason(role));
    }

    /** May the current user upload an audio version to the given episode? */
    public void checkCanUploadAudio(Long episodeId) {
        Role role = currentRole();
        if (isProducerLevel(role)) {
            return;
        }
        if (role == Role.EDITOR) {
            requireAssigned(episodeId);
            return;
        }
        throw ApiException.forbidden(denyReason(role));
    }

    /**
     * May the current user create/assign/delete tasks on the task board
     * (README §4.1 任务看板)? Only producer-level users manage assignments.
     */
    public void checkCanManageTasks() {
        Role role = currentRole();
        if (isProducerLevel(role)) {
            return;
        }
        throw ApiException.forbidden(
                role == Role.EDITOR ? "剪辑师只能更新分配给自己的任务状态" : denyReason(role));
    }

    /**
     * May the current user change a task's status? Producers/admins any task;
     * the assignee (typically an EDITOR) may progress their own task.
     */
    public void checkCanUpdateTaskStatus(Long assigneeId) {
        Role role = currentRole();
        if (isProducerLevel(role)) {
            return;
        }
        Long uid = SecurityUtils.currentUserId();
        if (uid != null && uid.equals(assigneeId)) {
            return; // an assignee may progress their own task
        }
        throw ApiException.forbidden("只能更新分配给自己的任务状态");
    }

    /**
     * May the current user manage distribution (README §9 运营只能操作分发)?
     * OPERATOR is the dedicated distribution role; producers/admins also allowed.
     */
    public void checkCanManageDistribution() {
        Role role = currentRole();
        if (isProducerLevel(role) || role == Role.OPERATOR) {
            return;
        }
        throw ApiException.forbidden(
                switch (role) {
                    case EDITOR -> "剪辑师不参与分发管理";
                    case HOST -> "主播/嘉宾不参与分发管理";
                    case GUEST -> "访客为只读访问";
                    default -> "没有权限执行该操作";
                });
    }

    private void requireAssigned(Long episodeId) {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null || !taskRepo.existsByEpisodeIdAndAssigneeId(episodeId, uid)) {
            throw ApiException.forbidden("剪辑师只能操作分配给自己的单集");
        }
    }

    private String denyReason(Role role) {
        return switch (role) {
            case OPERATOR -> "运营只能操作分发相关功能";
            case GUEST -> "访客为只读访问";
            default -> "没有权限执行该操作";
        };
    }
}
